package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.Config;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 实体上报缓存系统
 * 功能：
 * - 分类管理物品和弹射物
 * - 自动清理过期记录（区块卸载的实体）
 */
public class EntityCache {

    // ==================== 类型定义 ====================

    public enum EntityType {
        ITEM, PROJECTILE
    }

    // ==================== 缓存结构 ====================

    /**
     * 缓存结构：EntityType -> Dimension -> UUID -> 上报时间戳
     */
    private static final Map<EntityType, Map<ResourceLocation, Map<UUID, Long>>> entityCache = new ConcurrentHashMap<>();
    private static final Map<EntityType, LongAdder> totalCounts = new EnumMap<>(EntityType.class);

    static {
        entityCache.put(EntityType.ITEM, new ConcurrentHashMap<>());
        entityCache.put(EntityType.PROJECTILE, new ConcurrentHashMap<>());
        for (EntityType type : EntityType.values()) {
            totalCounts.put(type, new LongAdder());
        }
    }

    // ==================== 添加/移除操作 ====================

    /**
     * 添加实体到缓存
     */
    public static void addEntity(EntityType type, ResourceLocation dimension, UUID uuid) {
        Map<UUID, Long> dimensionCache = entityCache.get(type)
                .computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        Long previous = dimensionCache.put(uuid, System.currentTimeMillis());
        if (previous == null) {
            totalCounts.get(type).increment();
        }
    }

    /**
     * 从缓存中移除实体
     */
    public static void removeEntity(EntityType type, ResourceLocation dimension, UUID uuid) {
        Map<ResourceLocation, Map<UUID, Long>> typeCache = entityCache.get(type);
        Map<UUID, Long> dimensionCache = typeCache.get(dimension);

        if (dimensionCache != null) {
            Long removed = dimensionCache.remove(uuid);
            if (removed != null) {
                totalCounts.get(type).add(-1);
            }
            if (dimensionCache.isEmpty()) {
                typeCache.remove(dimension);
            }
        }
    }

    /**
     * 刷新实体时间戳（证明实体仍在运行）
     */
    public static void refreshEntity(EntityType type, ResourceLocation dimension, UUID uuid) {
        Map<UUID, Long> dimensionCache = entityCache.get(type).get(dimension);
        if (dimensionCache != null && dimensionCache.containsKey(uuid)) {
            dimensionCache.put(uuid, System.currentTimeMillis());
        }
    }

    // ==================== 查询操作 ====================

    /**
     * 检查实体是否已上报
     */
    public static boolean isEntityReported(EntityType type, ResourceLocation dimension, UUID uuid) {
        Map<UUID, Long> dimensionCache = entityCache.get(type).get(dimension);
        return dimensionCache != null && dimensionCache.containsKey(uuid);
    }

    /**
     * 获取指定维度的实体数量
     */
    public static int getEntityCount(EntityType type, ResourceLocation dimension) {
        Map<UUID, Long> dimensionCache = entityCache.get(type).get(dimension);
        return dimensionCache != null ? dimensionCache.size() : 0;
    }

    /**
     * 获取所有维度的实体数量
     */
    public static int getAllEntityCount(EntityType type) {
        return (int) totalCounts.get(type).sum();
    }

    // ==================== 缓存清理 ====================
    /**
     * 清理过期的缓存记录
     * 如果实体在2个检查周期内没有刷新时间戳，认为它已失效
     */
    public static void pruneExpiredCache() {
        long now = System.currentTimeMillis();
        // 过期阈值
        long expireMillis = Config.getCacheExpireMillis();

        for (EntityType type : EntityType.values()) {
            Map<ResourceLocation, Map<UUID, Long>> typeCache = entityCache.get(type);

            for (Map.Entry<ResourceLocation, Map<UUID, Long>> dimEntry : typeCache.entrySet()) {
                Map<UUID, Long> dimensionCache = dimEntry.getValue();

                int beforeSize = dimensionCache.size();
                dimensionCache.entrySet().removeIf(entry ->
                        now - entry.getValue() > expireMillis
                );
                int removed = beforeSize - dimensionCache.size();
                if (removed > 0) {
                    totalCounts.get(type).add(-removed);
                }

                if (dimensionCache.isEmpty()) {
                    typeCache.remove(dimEntry.getKey());
                }
            }
        }
    }

    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        entityCache.values().forEach(Map::clear);
        totalCounts.values().forEach(LongAdder::reset);
    }
}
