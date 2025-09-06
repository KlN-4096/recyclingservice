package com.klnon.recyclingservice.content.chunk;

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
 * 区块数据存储管理器 - 简化版本
 * 遵循KISS原则，移除过度复杂的双重索引系统
 */
public class ChunkCache {
    
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
    
    // 简化为单一数据存储: 维度 -> 区块位置 -> 区块信息
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> chunks = new ConcurrentHashMap<>();
    
    /**
     * 添加或更新区块信息
     */
    public static void updateChunk(ResourceLocation dimension, ChunkInfo chunkInfo) {
        chunks.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>())
              .put(chunkInfo.chunkPos(), chunkInfo);
    }
    
    /**
     * 获取区块信息
     */
    public static ChunkInfo getChunk(ResourceLocation dimension, ChunkPos pos) {
        return chunks.getOrDefault(dimension, Collections.emptyMap()).get(pos);
    }
    
    /**
     * 移除区块
     */
    public static void removeChunk(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkInfo> dimensionChunks = chunks.get(dimension);
        if (dimensionChunks != null) {
            dimensionChunks.remove(pos);
            if (dimensionChunks.isEmpty()) {
                chunks.remove(dimension);
            }
        }
    }
    
    /**
     * 获取维度中指定状态的区块列表 (按需计算)
     */
    public static List<ChunkPos> getChunksByState(ResourceLocation dimension, ChunkState state) {
        return chunks.getOrDefault(dimension, Collections.emptyMap())
            .entrySet().stream()
            .filter(entry -> entry.getValue().state() == state)
            .map(Map.Entry::getKey)
            .toList();
    }
    
    /**
     * 获取维度的所有区块
     */
    public static Map<ChunkPos, ChunkInfo> getDimensionChunks(ResourceLocation dimension) {
        return new HashMap<>(chunks.getOrDefault(dimension, Collections.emptyMap()));
    }
    
    /**
     * 获取区块状态
     */
    public static ChunkState getChunkState(ResourceLocation dimension, ChunkPos pos) {
        ChunkInfo info = getChunk(dimension, pos);
        return info != null ? info.state() : ChunkState.UNMANAGED;
    }
    
    /**
     * 转换区块状态 - 统一的状态转换方法
     */
    public static boolean transitionChunkState(ResourceLocation dimension, ChunkPos pos, 
                                             ChunkState newState, ServerLevel level) {
        ChunkInfo info = getChunk(dimension, pos);
        if (info == null) return false;
        
        ChunkState oldState = info.state();
        
        // 处理ticket变化
        if (!handleTickets(pos, level, oldState, newState)) {
            return false;
        }
        
        // 更新状态
        ChunkInfo newInfo = info.withState(newState);
        if (newState == ChunkState.ITEM_FROZEN) {
            long unfreezeTime = System.currentTimeMillis() + getItemFreezeHours() * 3600_000L;
            newInfo = newInfo.withUnfreezeTime(unfreezeTime);
        }
        
        updateChunk(dimension, newInfo);
        return true;
    }
    
    /**
     * 统一的ticket处理方法
     */
    private static boolean handleTickets(ChunkPos pos, ServerLevel level, 
                                       ChunkState oldState, ChunkState newState) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            boolean oldHasTicket = oldState == ChunkState.MANAGED;
            boolean newHasTicket = newState == ChunkState.MANAGED;
            
            if (!oldHasTicket && newHasTicket) {
                distanceManager.addTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            } else if (oldHasTicket && !newHasTicket) {
                distanceManager.removeTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取物品冻结持续时间(小时)
     */
    private static int getItemFreezeHours() {
        try {
            return com.klnon.recyclingservice.Config.TECHNICAL.itemFreezeHours.get();
        } catch (Exception e) {
            return 1; // 默认1小时
        }
    }
    
    /**
     * 冻结区块tickets(不管理状态) - 直接移除非白名单tickets
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
}