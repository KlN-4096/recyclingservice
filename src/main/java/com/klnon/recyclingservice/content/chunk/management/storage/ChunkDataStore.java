package com.klnon.recyclingservice.content.chunk.management.storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块数据存储管理器
 */
public class ChunkDataStore {
    
    // 白名单ticket类型
    public static final Set<TicketType<?>> WHITELIST_TICKET_TYPES = Set.of(
        TicketType.POST_TELEPORT,
        TicketType.PLAYER,
        TicketType.START,
        TicketType.UNKNOWN,
        TicketType.PORTAL
    );
    
    // 自定义ticket类型
    public static final TicketType<ChunkPos> RECYCLING_SERVICE_TICKET = 
        TicketType.create("recycling_service_chunk", Comparator.comparingLong(ChunkPos::toLong), 600);
    
    // 主数据存储: 维度 -> 区块位置 -> 区块信息
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> allChunks = new ConcurrentHashMap<>();
    
    // 状态索引: 状态 -> 维度 -> 区块位置集合
    private static final Map<ChunkState, Map<ResourceLocation, Set<ChunkPos>>> stateIndex = new ConcurrentHashMap<>();
    
    static {
        // 初始化状态索引
        for (ChunkState state : ChunkState.values()) {
            stateIndex.put(state, new ConcurrentHashMap<>());
        }
    }
    
    /**
     * 添加或更新区块信息
     */
    public static void updateChunk(ResourceLocation dimension, ChunkInfo chunkInfo) {
        ChunkPos pos = chunkInfo.chunkPos();
        
        // 获取旧状态用于索引更新
        ChunkInfo oldInfo = getChunk(dimension, pos);
        ChunkState oldState = oldInfo != null ? oldInfo.state() : null;
        
        // 更新主数据
        allChunks.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>())
                 .put(pos, chunkInfo);
        
        // 更新状态索引
        updateStateIndex(dimension, pos, oldState, chunkInfo.state());
    }
    
    /**
     * 获取区块信息
     */
    public static ChunkInfo getChunk(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkInfo> dimensionChunks = allChunks.get(dimension);
        return dimensionChunks != null ? dimensionChunks.get(pos) : null;
    }
    
    /**
     * 移除区块
     */
    public static void removeChunk(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkInfo> dimensionChunks = allChunks.get(dimension);
        if (dimensionChunks != null) {
            ChunkInfo removed = dimensionChunks.remove(pos);
            if (removed != null) {
                updateStateIndex(dimension, pos, removed.state(), null);
            }
            if (dimensionChunks.isEmpty()) {
                allChunks.remove(dimension);
            }
        }
    }
    
    /**
     * 获取指定状态的区块
     */
    public static Map<ResourceLocation, Set<ChunkPos>> getChunksByState(ChunkState state) {
        return new HashMap<>(stateIndex.getOrDefault(state, Collections.emptyMap()));
    }
    
    /**
     * 获取维度的所有区块
     */
    public static Map<ChunkPos, ChunkInfo> getDimensionChunks(ResourceLocation dimension) {
        return new HashMap<>(allChunks.getOrDefault(dimension, Collections.emptyMap()));
    }
    
    /**
     * 添加管理的区块(带ticket)
     */
    public static boolean addManagedChunk(ChunkPos pos, ServerLevel level, int blockEntityCount) {
        try {
            ResourceLocation dimension = level.dimension().location();
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            distanceManager.addTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            ChunkInfo info = new ChunkInfo(pos, ChunkState.MANAGED, blockEntityCount);
            updateChunk(dimension, info);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 移除管理的区块(移除ticket)
     */
    public static boolean removeManagedChunk(ChunkPos pos, ServerLevel level) {
        try {
            ResourceLocation dimension = level.dimension().location();
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            distanceManager.removeTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            
            ChunkInfo info = getChunk(dimension, pos);
            if (info != null) {
                updateChunk(dimension, info.withState(ChunkState.UNMANAGED));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 转换区块状态
     */
    public static boolean transitionChunkState(ResourceLocation dimension, ChunkPos pos, 
                                             ChunkState newState, ServerLevel level) {
        ChunkInfo info = getChunk(dimension, pos);
        if (info == null) return false;
        
        ChunkState oldState = info.state();
        
        // 处理ticket变化
        boolean ticketSuccess = handleTicketTransition(pos, level, oldState, newState);
        if (!ticketSuccess) return false;
        
        // 更新状态
        ChunkInfo newInfo = info.withState(newState);
        if (newState == ChunkState.ITEM_FROZEN) {
            long unfreezeTime = System.currentTimeMillis() + getItemFreezeHours() * 3600_000L;
            newInfo = newInfo.withUnfreezeTime(unfreezeTime);
        }
        
        updateChunk(dimension, newInfo);
        return true;
    }
    
    private static boolean handleTicketTransition(ChunkPos pos, ServerLevel level, 
                                                ChunkState oldState, ChunkState newState) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            boolean oldHasTicket = oldState == ChunkState.MANAGED;
            boolean newHasTicket = newState == ChunkState.MANAGED;
            
            if (!oldHasTicket && newHasTicket) {
                // 添加ticket
                distanceManager.addTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            } else if (oldHasTicket && !newHasTicket) {
                // 移除ticket
                distanceManager.removeTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void updateStateIndex(ResourceLocation dimension, ChunkPos pos, 
                                       ChunkState oldState, ChunkState newState) {
        // 从旧状态索引中移除
        if (oldState != null) {
            Map<ResourceLocation, Set<ChunkPos>> oldStateMap = stateIndex.get(oldState);
            if (oldStateMap != null) {
                Set<ChunkPos> oldSet = oldStateMap.get(dimension);
                if (oldSet != null) {
                    oldSet.remove(pos);
                    if (oldSet.isEmpty()) {
                        oldStateMap.remove(dimension);
                    }
                }
            }
        }
        
        // 添加到新状态索引
        if (newState != null) {
            stateIndex.get(newState)
                     .computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet())
                     .add(pos);
        }
    }
    
    private static int getItemFreezeHours() {
        try {
            return com.klnon.recyclingservice.Config.TECHNICAL.itemFreezeHours.get();
        } catch (Exception e) {
            return 1; // 默认1小时
        }
    }
    
    /**
     * 冻结区块tickets(不管理状态)
     */
    public static int freezeChunkTickets(ChunkPos chunkPos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
            SortedArraySet<Ticket<?>> chunkTickets = tickets.get(chunkKey);
            
            if (chunkTickets == null || chunkTickets.isEmpty()) {
                return 0;
            }
            
            List<Ticket<?>> ticketsToRemove = new ArrayList<>();
            for (Ticket<?> ticket : chunkTickets) {
                if (!WHITELIST_TICKET_TYPES.contains(ticket.getType())) {
                    ticketsToRemove.add(ticket);
                }
            }
            
            for (Ticket<?> ticket : ticketsToRemove) {
                distanceManager.removeTicket(chunkKey, ticket);
            }
            
            return ticketsToRemove.size();
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * 获取区块状态
     */
    public static ChunkState getChunkState(ResourceLocation dimension, ChunkPos pos) {
        ChunkInfo info = getChunk(dimension, pos);
        return info != null ? info.state() : ChunkState.UNMANAGED;
    }
    
    /**
     * 获取指定状态的区块数量
     */
    public static int getChunkCountByState(ChunkState state) {
        return getChunksByState(state).values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}