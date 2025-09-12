package com.klnon.recyclingservice.content.cleanup;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级实体上报缓存系统
 * 仅存储UUID，分别管理物品和弹射物
 */
public class EntityCache {

    // 实体类型枚举
    public enum EntityType {
        ITEM, PROJECTILE
    }

    // 统一存储：实体类型 -> 维度 -> UUID集合
    private static final Map<EntityType, Map<ResourceLocation, Set<UUID>>> entityCache = new ConcurrentHashMap<>();

    static {
        // 初始化缓存
        entityCache.put(EntityType.ITEM, new ConcurrentHashMap<>());
        entityCache.put(EntityType.PROJECTILE, new ConcurrentHashMap<>());
    }

    // === 通用操作方法 ===

    /**
     * 添加实体到缓存
     */
    public static void addEntity(EntityType type, ResourceLocation dimension, UUID uuid) {
        entityCache.get(type).computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    /**
     * 从缓存中移除实体
     */
    public static void removeEntity(EntityType type, ResourceLocation dimension, UUID uuid) {
        Map<ResourceLocation, Set<UUID>> typeCache = entityCache.get(type);
        Set<UUID> entities = typeCache.get(dimension);
        if (entities != null) {
            entities.remove(uuid);
            if (entities.isEmpty()) {
                typeCache.remove(dimension);
            }
        }
    }

    /**
     * 检查实体是否已上报
     */
    public static boolean isEntityReported(EntityType type, ResourceLocation dimension, UUID uuid) {
        Set<UUID> entities = entityCache.get(type).get(dimension);
        return entities != null && entities.contains(uuid);
    }

    /**
     * 获取指定维度的实体数量
     */
    public static int getEntityCount(EntityType type, ResourceLocation dimension) {
        Set<UUID> entities = entityCache.get(type).get(dimension);
        return entities != null ? entities.size() : 0;
    }

    /**
     * 获取所有维度的实体数量
     */
    public static int getAllEntityCount(EntityType type) {
        return entityCache.get(type).values().stream().mapToInt(Set::size).sum();
    }

    // === 通用方法 ===

    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        entityCache.values().forEach(Map::clear);
    }
}