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
 * 区块缓存
 * 统一管理区块信息，按方块实体数量排序
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
    
    // 主存储：维度 -> 区块信息列表
    private static final Map<ResourceLocation, List<ChunkInfo>> managedChunks = new ConcurrentHashMap<>();

    
    /**
     * 区块信息记录类
     */
    public record ChunkInfo(
        ResourceLocation dimension,
        ChunkPos pos,
        int blockEntityCount,
        int state,
        long itemFreezeTime
    ) {
        // 状态常量
        public static final int MANAGED = 1;
        public static final int ITEM_FROZEN = 2;
        public static final int PERFORMANCE_FROZEN = 3;
        
        public String getStateName() {
            return switch (state) {
                case ITEM_FROZEN -> "ITEM_FROZEN";
                case PERFORMANCE_FROZEN -> "PERFORMANCE_FROZEN";
                default -> "MANAGED";
            };
        }
    }
    
    // ================== 核心存储方法 ==================
    
    /**
     * 设置维度的管理区块列表
     */
    public static void setManagedChunks(ResourceLocation dimension, List<ChunkInfo> chunks) {
        managedChunks.put(dimension, new ArrayList<>(chunks));
    }
    
    /**
     * 获取指定状态的区块列表
     */
    public static List<ChunkInfo> getChunksByState(ResourceLocation dimension, int state) {
        return managedChunks.getOrDefault(dimension, Collections.emptyList())
                           .stream()
                           .filter(info -> info.state == state)
                           .toList();
    }
    
    /**
     * 更新区块状态
     */
    public static boolean updateChunkState(ResourceLocation dimension, ChunkPos pos, int newState, long freezeTime) {
        List<ChunkInfo> chunks = managedChunks.get(dimension);
        if (chunks == null) return false;
        
        for (int i = 0; i < chunks.size(); i++) {
            ChunkInfo info = chunks.get(i);
            if (info.pos.equals(pos)) {
                chunks.set(i, new ChunkInfo(dimension, pos, info.blockEntityCount, newState, freezeTime));
                return true;
            }
        }
        return false;
    }
    
    // ================== 物品冻结管理 ==================
    
    /**
     * 通用冻结区块方法
     */
    public static boolean freezeChunk(ResourceLocation dimension, ChunkPos pos, ServerLevel level, int newState, long unfreezeTime) {
        try {
            // 移除所有非白名单tickets
            int frozenTickets = freezeChunkTickets(pos, level);
            
            if (frozenTickets > 0) {
                updateChunkState(dimension, pos, newState, unfreezeTime);
                return true;
            }
        } catch (Exception e) {
            // 冻结失败
        }
        return false;
    }

    
    /**
     * 检查物品超载区块是否应该解冻
     */
    public static boolean shouldUnfreezeItemFrozenChunk(ResourceLocation dimension, ChunkPos pos) {
        List<ChunkInfo> chunks = managedChunks.get(dimension);
        if (chunks == null) return false;
        
        return chunks.stream()
                    .filter(info -> info.pos.equals(pos) && info.state == ChunkInfo.ITEM_FROZEN)
                    .anyMatch(info -> System.currentTimeMillis() >= info.itemFreezeTime);
    }
    
    /**
     * 解冻区块
     */
    public static boolean unfreezeChunk(ResourceLocation dimension, ChunkPos pos, ServerLevel level) {
        try {
            if (addManagementTicket(pos, level)) {
                updateChunkState(dimension, pos, ChunkInfo.MANAGED, 0);
                return true;
            }
        } catch (Exception e) {
            // 解冻失败
        }
        return false;
    }
    
    /**
     * 获取所有物品冻结的区块
     */
    public static List<ChunkPos> getItemFrozenChunks(ResourceLocation dimension) {
        return getChunksByState(dimension, ChunkInfo.ITEM_FROZEN)
               .stream()
               .map(info -> info.pos)
               .toList();
    }
    
    // ================== Ticket管理 ==================
    
    /**
     * 添加管理ticket
     */
    public static boolean addManagementTicket(ChunkPos pos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            distanceManager.addTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 冻结区块tickets(移除非白名单tickets)
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