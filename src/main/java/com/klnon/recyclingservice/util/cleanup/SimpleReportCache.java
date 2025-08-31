package com.klnon.recyclingservice.util.cleanup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * 主动上报缓存系统 - UUID统一操作的简化版本
 * 核心理念：所有操作都基于UUID，提供Entity便捷方法
 * 优化：统一异常处理、减少代码重复、清晰的API设计
 */
public class SimpleReportCache {
    
    private static final ConcurrentHashMap<ResourceLocation, ConcurrentLinkedQueue<EntityReport>> cache 
        = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<ResourceLocation, Set<UUID>> reportedUuids = new ConcurrentHashMap<>();
    
    // === 核心辅助方法 ===
    
    /**
     * 提取实体信息
     */
    private static EntityInfo extractEntityInfo(Entity entity) {
        return new EntityInfo(entity.getUUID(), entity.level().dimension().location(), entity);
    }
    
    /**
     * 统一异常处理包装器
     */
    private static <T> T safeOperation(Supplier<T> operation, T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 核心UUID添加操作
     */
    private static void addToCache(ResourceLocation dimension, UUID uuid, Entity entity) {
        safeOperation(() -> {
            Set<UUID> reported = reportedUuids.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
            if (reported.add(uuid)) {
                ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
                EntityReport report = new EntityReport(entity, chunkPos, dimension);
                cache.computeIfAbsent(dimension, k -> new ConcurrentLinkedQueue<>()).add(report);
                return true;
            }
            return false;
        }, false);
    }
    
    /**
     * 核心UUID移除操作
     */
    private static void removeFromCache(ResourceLocation dimension, UUID uuid) {
        safeOperation(() -> {
            boolean removed = false;

            // 从UUID记录中移除
            Set<UUID> reported = reportedUuids.get(dimension);
            if (reported != null) {
                removed = reported.remove(uuid);
            }

            // 从实体缓存中移除
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            if (queue != null) {
                queue.removeIf(report -> report.entity().getUUID().equals(uuid));
            }

            return removed;
        }, false);
    }
    
    // === 公共API方法 ===
    
    /**
     * 上报实体 (Entity便捷方法)
     */
    public static void report(Entity entity) {
        EntityInfo info = extractEntityInfo(entity);
        addToCache(info.dimension(), info.uuid(), info.entity());
    }
    
    /**
     * 检查UUID是否已上报 (核心方法)
     */
    public static boolean isReported(ResourceLocation dimension, UUID uuid) {
        return safeOperation(() -> {
            Set<UUID> reported = reportedUuids.get(dimension);
            return reported != null && reported.contains(uuid);
        }, false);
    }
    
    /**
     * 检查实体是否已上报 (Entity便捷方法)
     */
    public static boolean isEntityReported(Entity entity) {
        EntityInfo info = extractEntityInfo(entity);
        return isReported(info.dimension(), info.uuid());
    }
    
    /**
     * 移除UUID (核心方法)
     */
    public static void remove(ResourceLocation dimension, UUID uuid) {
        removeFromCache(dimension, uuid);
    }
    
    /**
     * 移除实体 (Entity便捷方法)
     */
    public static void remove(Entity entity) {
        EntityInfo info = extractEntityInfo(entity);
        remove(info.dimension(), info.uuid());
    }
    
    /**
     * 清理无效实体，返回清理数量
     */
    public static int removeInvalidEntities(ResourceLocation dimension) {
        return safeOperation(() -> {
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            if (queue == null) return 0;
            
            List<UUID> toRemove = queue.stream()
                .map(EntityReport::entity)
                .filter(entity -> entity.isRemoved() || !entity.isAlive())
                .map(Entity::getUUID)
                .toList();
            
            toRemove.forEach(uuid -> removeFromCache(dimension, uuid));
            return toRemove.size();
        }, 0);
    }
    
    /**
     * 获取维度的所有实体报告
     */
    public static List<EntityReport> getReportedEntries(ResourceLocation dimension) {
        return safeOperation(() -> {
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
        }, new ArrayList<>());
    }
    
    /**
     * 获取区块实体数量统计
     */
    public static Map<ChunkPos, Integer> getEntityCountByChunk(ResourceLocation dimension) {
        return safeOperation(() -> {
            Map<ChunkPos, Integer> stats = new HashMap<>();
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            
            if (queue != null) {
                queue.forEach(report -> 
                    stats.merge(report.chunkPos(), 1, Integer::sum));
            }
            
            return stats;
        }, new HashMap<>());
    }
    
    /**
     * 获取超过阈值的区块
     */
    public static List<ChunkPos> getChunksExceedingThreshold(ResourceLocation dimension, int threshold) {
        return getEntityCountByChunk(dimension).entrySet().stream()
            .filter(entry -> entry.getValue() >= threshold)
            .map(Map.Entry::getKey)
            .toList();
    }
    
    /**
     * 清空维度缓存
     */
    public static void clear(ResourceLocation dimension) {
        safeOperation(() -> {
            cache.remove(dimension);
            reportedUuids.remove(dimension);
            return null;
        }, null);
    }

    // === 辅助记录类 ===
    
    /**
     * 实体信息记录
     */
    private record EntityInfo(UUID uuid, ResourceLocation dimension, Entity entity) {}
    
    /**
     * 实体上报记录
     */
    public record EntityReport(Entity entity, ChunkPos chunkPos, ResourceLocation dimension) {}
}