package com.klnon.recyclingservice.content.cleanup.entity;

import com.klnon.recyclingservice.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主动上报缓存系统 - UUID统一操作的简化版本
 * 核心理念：所有操作都基于UUID，提供Entity便捷方法
 */
public class EntityCache {
    
    // 统一主存储：维度 -> UUID -> 实体记录
    private static final ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<UUID, EntityRecord>> entities 
        = new ConcurrentHashMap<>();
    
    // === 核心存储方法 ===
    
    /**
     * 添加实体到缓存
     */
    public static void addEntity(ResourceLocation dimension, UUID uuid, Entity entity) {
        ConcurrentHashMap<UUID, EntityRecord> dimensionEntities = 
            entities.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        
        EntityRecord record = new EntityRecord(entity, new ChunkPos(entity.blockPosition()), System.currentTimeMillis());
        dimensionEntities.putIfAbsent(uuid, record);
    }
    
    /**
     * 从缓存中移除实体
     */
    public static void removeEntity(ResourceLocation dimension, UUID uuid) {
        ConcurrentHashMap<UUID, EntityRecord> dimensionEntities = entities.get(dimension);
        if (dimensionEntities != null) {
            dimensionEntities.remove(uuid);
        }
    }
    
    // === 公共API方法 ===

    /**
     * 检查实体是否已上报
     */
    public static boolean isEntityReported(Entity entity) {
        ResourceLocation dimension = entity.level().dimension().location();
        ConcurrentHashMap<UUID, EntityRecord> dimensionEntities = entities.get(dimension);
        return dimensionEntities != null && dimensionEntities.containsKey(entity.getUUID());
    }
    
    /**
     * 清理无效实体
     */
    public static void removeInvalidEntities(ResourceLocation dimension) {
        ConcurrentHashMap<UUID, EntityRecord> dimensionEntities = entities.get(dimension);
        if (dimensionEntities == null) return;

        Iterator<Map.Entry<UUID, EntityRecord>> iterator = dimensionEntities.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<UUID, EntityRecord> entry = iterator.next();
            EntityRecord record = entry.getValue();
            
            try {
                Entity entity = record.entity();
                if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                    iterator.remove();
                }
            } catch (Exception e) {
                iterator.remove();
            }
        }

    }
    
    /**
     * 获取维度的所有实体报告
     */
    public static List<EntityReport> getReportedEntries(ResourceLocation dimension) {
        ConcurrentHashMap<UUID, EntityRecord> dimensionEntities = entities.get(dimension);
        if (dimensionEntities == null) return new ArrayList<>();
        
        List<EntityReport> reports = new ArrayList<>();
        for (EntityRecord record : dimensionEntities.values()) {
            reports.add(new EntityReport(record.entity(), record.chunkPos(), dimension));
        }
        return reports;
    }
    
    /**
     * 获取区块实体数量统计（按需计算）
     */
    public static Map<ChunkPos, Integer> getEntityCountByChunk(ResourceLocation dimension) {
        ConcurrentHashMap<UUID, EntityRecord> dimensionEntities = entities.get(dimension);
        if (dimensionEntities == null) return new HashMap<>();
        
        Map<ChunkPos, Integer> chunkCounts = new HashMap<>();
        for (EntityRecord record : dimensionEntities.values()) {
            chunkCounts.merge(record.chunkPos(), 1, Integer::sum);
        }
        return chunkCounts;
    }

    /**
     * 获取所有维度缓存的实体总数
     */
    public static int getTotalReportedCount() {
        return entities.values().stream()
            .mapToInt(Map::size)
            .sum();
    }

    /**
     * 获取超载区块列表（按需计算）
     */
    public static List<ChunkPos> getOverloadedChunks(ResourceLocation dimension) {
        Map<ChunkPos, Integer> chunkCounts = getEntityCountByChunk(dimension);
        int threshold = Config.TECHNICAL.tooManyItemsWarning.get();

        return chunkCounts.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .toList();
    }

    // === 辅助记录类 ===
    
    /**
     * 实体存储记录
     */
    private record EntityRecord(Entity entity, ChunkPos chunkPos, long reportTime) {}
    
    /**
     * 实体上报记录（公共API返回格式）
     */
    public record EntityReport(Entity entity, ChunkPos chunkPos, ResourceLocation dimension) {}
}