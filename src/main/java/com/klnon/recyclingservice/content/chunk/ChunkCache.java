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
import java.util.concurrent.atomic.AtomicInteger;

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
        TicketType.create("recycling_service_chunk", Comparator.comparingLong(ChunkPos::toLong));
    
    // 主存储：维度 -> 区块信息列表
    private static final Map<ResourceLocation, List<ChunkInfo>> managedChunks = new ConcurrentHashMap<>();
    
    // 快速索引：维度 -> (区块位置 -> 区块信息)
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> dimensionIndexes = new ConcurrentHashMap<>();
    
    // 实体计数缓存：维度 -> (区块位置 -> 实体数量)
    private static final Map<ResourceLocation, Map<ChunkPos, AtomicInteger>> entityCounts = new ConcurrentHashMap<>();

    /**
     * 清空所有管理的区块缓存（服务器停止时调用）
     */
    public static void clearAll() {
        managedChunks.clear();
        dimensionIndexes.clear();
        entityCounts.clear();
    }

    
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
        public static final int UNIMPORTANT = 0;
        public static final int MANAGED = 1;
        public static final int ITEM_FROZEN = 2;
        public static final int PERFORMANCE_FROZEN = 3;
        
        public String getStateName() {
            return switch (state) {
                case UNIMPORTANT -> "UNIMPORTANT";
                case ITEM_FROZEN -> "ITEM_FROZEN";
                case PERFORMANCE_FROZEN -> "PERFORMANCE_FROZEN";
                default -> "MANAGED";
            };
        }
        /**
         * 根据状态名称获取状态值
         */
        public static int getStateByName(String name) {
            return switch (name.toUpperCase()) {
                case "UNIMPORTANT" -> ChunkCache.ChunkInfo.UNIMPORTANT;
                case "ITEM_FROZEN" -> ChunkCache.ChunkInfo.ITEM_FROZEN;
                case "PERFORMANCE_FROZEN" -> ChunkCache.ChunkInfo.PERFORMANCE_FROZEN;
                default -> ChunkCache.ChunkInfo.MANAGED;
            };
        }
    }
    
    // ================== 核心存储方法 ==================
    
    /**
     * 增量添加管理区块列表（只添加新区块，保持现有区块）
     */
    public static void setManagedChunks(ResourceLocation dimension, List<ChunkInfo> newChunks) {
        if (newChunks.isEmpty()) {
            return; // 没有新区块，直接返回，保持现有状态
        }
        
        List<ChunkInfo> existingChunks = managedChunks.computeIfAbsent(dimension, k -> new ArrayList<>());
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        
        existingChunks.addAll(newChunks);
        
        // 同时更新索引
        for (ChunkInfo chunk : newChunks) {
            dimensionIndex.put(chunk.pos(), chunk);
        }
        
        // 按方块实体数量重新排序（从大到小）
        existingChunks.sort((a, b) -> Integer.compare(b.blockEntityCount(), a.blockEntityCount()));
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
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        if (chunks == null || dimensionIndex == null) return false;
        
        for (int i = 0; i < chunks.size(); i++) {
            ChunkInfo info = chunks.get(i);
            if (info.pos.equals(pos)) {
                ChunkInfo newInfo = new ChunkInfo(dimension, pos, info.blockEntityCount, newState, freezeTime);
                chunks.set(i, newInfo);
                dimensionIndex.put(pos, newInfo); // 同时更新索引
                return true;
            }
        }
        return false;
    }

    public static boolean isChunkManaged(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        return dimensionIndex != null && dimensionIndex.containsKey(pos);
    }
    
    /**
     * 快速获取区块信息（O(1)复杂度）
     */
    public static ChunkInfo getChunkInfo(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        return dimensionIndex != null ? dimensionIndex.get(pos) : null;
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
                updateChunkState(dimension, pos, newState, System.currentTimeMillis() + unfreezeTime * 60 * 1000L);
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
//            Recyclingservice.LOGGER.info("Ticket added to distance manager{}",pos);
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
//                Recyclingservice.LOGGER.info("Ticket removed from {} distance manager{}",chunkPos,ticket);
            }
            
            return ticketsToRemove.size();
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ================== 实体计数管理 ==================
    
    /**
     * 增加指定区块的实体计数
     */
    public static void incrementEntityCount(ResourceLocation dimension, ChunkPos pos) {
        entityCounts.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(pos, k -> new AtomicInteger())
                    .incrementAndGet();
    }
    
    /**
     * 减少指定区块的实体计数
     */
    public static void decrementEntityCount(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, AtomicInteger> dimensionCounts = entityCounts.get(dimension);
        if (dimensionCounts != null) {
            AtomicInteger count = dimensionCounts.get(pos);
            if (count != null && count.decrementAndGet() <= 0) {
                // 当计数为0时，移除该区块的计数器以节省内存
                dimensionCounts.remove(pos);
                if (dimensionCounts.isEmpty()) {
                    entityCounts.remove(dimension);
                }
            }
        }
    }
    
    /**
     * 获取指定区块的实体数量
     */
    public static int getEntityCount(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, AtomicInteger> dimensionCounts = entityCounts.get(dimension);
        if (dimensionCounts != null) {
            AtomicInteger count = dimensionCounts.get(pos);
            return count != null ? count.get() : 0;
        }
        return 0;
    }
    
    /**
     * 获取指定维度所有区块的实体数量统计
     */
    public static Map<ChunkPos, Integer> getEntityCountByChunk(ResourceLocation dimension) {
        Map<ChunkPos, AtomicInteger> dimensionCounts = entityCounts.get(dimension);
        if (dimensionCounts == null) {
            return new HashMap<>();
        }
        
        Map<ChunkPos, Integer> result = new HashMap<>();
        dimensionCounts.forEach((pos, count) -> result.put(pos, count.get()));
        return result;
    }
    
    /**
     * 获取超载区块列表（实体数量超过阈值）
     */
    public static List<ChunkPos> getOverloadedChunks(ResourceLocation dimension, int threshold) {
        Map<ChunkPos, AtomicInteger> dimensionCounts = entityCounts.get(dimension);
        if (dimensionCounts == null) {
            return new ArrayList<>();
        }
        
        return dimensionCounts.entrySet().stream()
                             .filter(entry -> entry.getValue().get() >= threshold)
                             .map(Map.Entry::getKey)
                             .toList();
    }
    
    /**
     * 清空指定维度的实体计数
     */
    public static void clearEntityCounts(ResourceLocation dimension) {
        entityCounts.remove(dimension);
    }
}