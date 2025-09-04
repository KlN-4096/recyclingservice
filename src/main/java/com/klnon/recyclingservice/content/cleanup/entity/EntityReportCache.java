package com.klnon.recyclingservice.content.cleanup.entity;

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
 */
public class EntityReportCache {
    
    private static final ConcurrentHashMap<ResourceLocation, ConcurrentLinkedQueue<EntityReport>> cache 
        = new ConcurrentHashMap<>();
    
    private static final ConcurrentHashMap<ResourceLocation, Set<UUID>> reportedUuids = new ConcurrentHashMap<>();
    
    // 区块实体计数器 - 实时维护每个区块的实体数量
    private static final ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<ChunkPos, Integer>> chunkCounters 
        = new ConcurrentHashMap<>();
    
    // 超载区块集合 - 超过阈值的区块，O(1)访问
    private static final ConcurrentHashMap<ResourceLocation, Set<ChunkPos>> overloadedChunks 
        = new ConcurrentHashMap<>();
    
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
     * 更新区块计数器并检查是否超载
     */
    private static void updateChunkCounter(ResourceLocation dimension, ChunkPos chunkPos, int delta) {
        safeOperation(() -> {
            ConcurrentHashMap<ChunkPos, Integer> dimensionCounters = 
                chunkCounters.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
            
            int newCount = dimensionCounters.merge(chunkPos, delta, Integer::sum);
            
            // 移除计数为0或负数的条目
            if (newCount <= 0) {
                dimensionCounters.remove(chunkPos);
                // 从超载区块中移除
                Set<ChunkPos> overloaded = overloadedChunks.get(dimension);
                if (overloaded != null) {
                    overloaded.remove(chunkPos);
                }
            } else {
                // 检查是否超载
                checkAndUpdateOverloadedStatus(dimension, chunkPos, newCount);
            }
            return true;
        }, false);
    }
    
    /**
     * 检查并更新区块超载状态
     */
    private static void checkAndUpdateOverloadedStatus(ResourceLocation dimension, ChunkPos chunkPos, int count) {
        try {
            int threshold = com.klnon.recyclingservice.Config.TECHNICAL.tooManyItemsWarning.get();
            Set<ChunkPos> overloaded = overloadedChunks.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
            
            if (count >= threshold) {
                overloaded.add(chunkPos);
            } else {
                overloaded.remove(chunkPos);
            }
        } catch (Exception e) {
            // 配置获取失败，使用默认阈值50
            Set<ChunkPos> overloaded = overloadedChunks.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
            if (count >= 50) {
                overloaded.add(chunkPos);
            } else {
                overloaded.remove(chunkPos);
            }
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
                // 更新区块计数器
                updateChunkCounter(dimension, chunkPos, 1);
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

            // 从实体缓存中移除并更新计数器
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            if (queue != null) {
                // 找到对应的报告并移除，同时更新计数器
                queue.removeIf(report -> {
                    if (report.entity().getUUID().equals(uuid)) {
                        // 减少区块计数器
                        updateChunkCounter(dimension, report.chunkPos(), -1);
                        return true;
                    }
                    return false;
                });
            }

            return removed;
        }, false);
    }
    
    // === 公共API方法 ===
    
    /**
     * 上报实体
     */
    public static void report(Entity entity) {
        EntityInfo info = extractEntityInfo(entity);
        addToCache(info.dimension(), info.uuid(), info.entity());
    }
    
    /**
     * 检查实体是否已上报
     */
    public static boolean isEntityReported(Entity entity) {
        EntityInfo info = extractEntityInfo(entity);
        return safeOperation(() -> {
            Set<UUID> reported = reportedUuids.get(info.dimension());
            return reported != null && reported.contains(info.uuid());
        },false);
    }

    /**
     * 移除实体
     */
    public static void remove(Entity entity) {
        EntityInfo info = extractEntityInfo(entity);
        removeFromCache(info.dimension(), info.uuid());
    }
    
    /**
     * 清理无效实体，返回清理数量
     */
    public static int removeInvalidEntities(ResourceLocation dimension) {
        return safeOperation(() -> {
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            if (queue == null) return 0;
            
            List<EntityReport> toRemove = queue.stream()
                .filter(report -> {
                    try {
                        Entity entity = report.entity();
                        return entity == null || entity.isRemoved() || !entity.isAlive();
                    } catch (Exception e) {
                        return true; // 异常就移除
                    }
                })
                .toList();
            
            // 移除无效实体并更新计数器
            for (EntityReport report : toRemove) {
                try {
                    Entity entity = report.entity();
                    UUID uuid = entity != null ? entity.getUUID() : null;
                    if (uuid != null) {
                        removeFromCache(dimension, uuid);
                    } else {
                        // 如果无法获取UUID，直接从队列移除并更新计数器
                        queue.remove(report);
                        updateChunkCounter(dimension, report.chunkPos(), -1);
                    }
                } catch (Exception e) {
                    // 单个实体处理失败，直接从队列移除并更新计数器
                    queue.remove(report);
                    updateChunkCounter(dimension, report.chunkPos(), -1);
                }
            }
            
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
     * 获取实时超载区块列表 - 新的高性能API  
     * @param dimension 维度
     * @return 超载区块列表
     */
    public static List<ChunkPos> getOverloadedChunks(ResourceLocation dimension) {
        return safeOperation(() -> {
            Set<ChunkPos> overloaded = overloadedChunks.get(dimension);
            return overloaded != null ? new ArrayList<>(overloaded) : new ArrayList<>();
        }, new ArrayList<>());
    }
    
    /**
     * 获取所有维度缓存的实体总数
     * @return 缓存中的实体总数量
     */
    public static int getTotalReportedCount() {
        return safeOperation(() -> reportedUuids.values().stream()
                .mapToInt(Set::size)
                .sum(), 0);
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