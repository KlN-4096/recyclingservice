package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 区块缓存
 * 统一管理区块信息，按方块实体数量排序
 */
public class ChunkDataCache {


    // 状态常量
    public static final byte UNIMPORTANT = 0;
    public static final byte MANAGED = 1;
    public static final byte ITEM_FROZEN = 2;
    public static final byte PERFORMANCE_FROZEN = 3;
    private static final byte[] STATE_NAMES = {UNIMPORTANT,MANAGED,ITEM_FROZEN,PERFORMANCE_FROZEN};


    // 区块索引：维度 -> (区块位置 -> 区块信息)
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> dimensionIndexes = new ConcurrentHashMap<>();

    // 实体计数缓存：维度 -> (区块位置 -> 实体数量),只是用来当区块物品过多时报告
    private static final Map<ResourceLocation, Map<ChunkPos, AtomicInteger>> entityCounts = new ConcurrentHashMap<>();

    /**
     * 区块信息记录类
     */
    public record ChunkInfo(
        ResourceLocation dimension,
        ChunkPos pos,
        short blockEntityCount,
        byte state,
        long itemFreezeTime
    ) {

        /**
         * 根据状态获取字符形式的状态
         */
        public String getStateName() {
            return switch (state) {
                case UNIMPORTANT -> "UNIMPORTANT";
                case ITEM_FROZEN -> "ITEM_FROZEN";
                case PERFORMANCE_FROZEN -> "PERFORMANCE_FROZEN";
                default -> "MANAGED";
            };
        }
    }
    // ================== 状态方法 ==================
    /**
     * 根据状态名称获取状态值
     */
    public static byte getStateByName(String name) {
        return switch (name.toUpperCase()) {
            case "UNIMPORTANT" -> UNIMPORTANT;
            case "ITEM_FROZEN" -> ITEM_FROZEN;
            case "PERFORMANCE_FROZEN" -> PERFORMANCE_FROZEN;
            default -> MANAGED;
        };
    }

    /**
     * 获取状态集合
     */
    public static byte[] getAllStates() {
        return STATE_NAMES;
    }

    /**
     * 检查物品超载区块是否应该解冻
     */
    public static boolean shouldUnfreezeItemFrozenChunk(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkDataCache.ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        if (dimensionIndex == null) return false;

        ChunkDataCache.ChunkInfo info = dimensionIndex.get(pos);
        return info != null &&
                info.state == ITEM_FROZEN &&
                System.currentTimeMillis() >= info.itemFreezeTime;
    }

    // ================== 核心存储方法 ==================


    /**
     * 增量添加管理区块列表（只添加新区块，保持现有区块）
     */
    public static void setManagedChunks(ResourceLocation dimension, List<ChunkInfo> newChunks) {
        if (newChunks.isEmpty()) {
            return; // 没有新区块，直接返回，保持现有状态
        }

        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());

        // 直接添加到索引（自动去重）
        for (ChunkInfo chunk : newChunks) {
            dimensionIndex.put(chunk.pos(), chunk);
        }
    }

    /**
     * 更新区块状态
     */
    public static boolean updateChunkState(ResourceLocation dimension, ChunkPos pos, byte newState, long freezeTime) {
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        if (dimensionIndex == null) return false;

        ChunkInfo existing = dimensionIndex.get(pos);
        if (existing != null) {
            ChunkInfo newInfo = new ChunkInfo(dimension, pos, existing.blockEntityCount(), newState, freezeTime);
            dimensionIndex.put(pos, newInfo);
            return true;
        }
        return false;
    }

    /**
     * 获取指定状态的区块列表
     */
    public static List<ChunkInfo> getChunksByState(ResourceLocation dimension, byte state) {
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        if (dimensionIndex == null) {
            return Collections.emptyList();
        }

        return dimensionIndex.values().stream()
                .filter(info -> info.state == state)
                .sorted((a, b) -> Integer.compare(b.blockEntityCount(), a.blockEntityCount())) // 按需排序
                .toList();
    }

    /**
     * 快速获取区块信息
     */
    public static ChunkInfo getChunkInfo(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, ChunkInfo> dimensionIndex = dimensionIndexes.get(dimension);
        return dimensionIndex != null ? dimensionIndex.get(pos) : null;
    }

    /**
     * 清空所有管理的区块缓存（服务器停止时调用）
     */
    public static void clearAll() {
        dimensionIndexes.clear();
        entityCounts.clear();
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
     * 获取指定维度所有区块的实体数量统计
     */
    public static Map<ChunkPos, Integer> getEntityCountByDimension(ResourceLocation dimension) {
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
    public static List<ChunkPos> getOverloadedChunks(ResourceLocation dimension) {
        Map<ChunkPos, AtomicInteger> dimensionCounts = entityCounts.get(dimension);
        if (dimensionCounts == null) {
            return Collections.emptyList();
        }

        return dimensionCounts.entrySet().stream()
                             .filter(entry -> entry.getValue().get() >= Config.TECHNICAL.tooManyItemsWarning.get())
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