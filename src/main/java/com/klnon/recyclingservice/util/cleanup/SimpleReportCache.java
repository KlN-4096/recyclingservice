package com.klnon.recyclingservice.util.cleanup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * 主动上报缓存系统 - 新清理架构的核心组件
 * 
 * 工作流程：实体自检 -> 满足条件时上报 -> CleanupService获取 -> 清理后清空缓存
 * 优化：UUID去重避免重复收集
 */
public class SimpleReportCache {
    
    // 简单的维度->实体报告列表映射
    private static final ConcurrentHashMap<ResourceLocation, ConcurrentLinkedQueue<EntityReport>> cache 
        = new ConcurrentHashMap<>();
    
    // UUID去重集合：维度 -> 已上报实体UUID集合
    private static final ConcurrentHashMap<ResourceLocation, Set<UUID>> reportedUuids = new ConcurrentHashMap<>();
    
    /**
     * 上报实体需要清理 - 使用UUID去重避免重复收集
     * @param entity 需要清理的实体
     */
    public static void report(Entity entity) {
        try {
            UUID entityUuid = entity.getUUID();
            ResourceLocation dimension = entity.level().dimension().location();
            
            // UUID去重检查：只有首次上报才会添加到缓存
            Set<UUID> reported = reportedUuids.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
            if (reported.add(entityUuid)) {
                // 首次上报成功，添加到实体缓存
                ChunkPos chunkPos = new ChunkPos(entity.blockPosition());
                EntityReport report = new EntityReport(entity, chunkPos, dimension);
                cache.computeIfAbsent(dimension, k -> new ConcurrentLinkedQueue<>()).add(report);
            }
            // 如果UUID已存在，reported.add()返回false，直接跳过不重复添加
        } catch (Exception e) {
            // 出错就跳过，什么都不做
        }
    }
    
    /**
     * 取消上报 - 同时从缓存和UUID记录中移除
     * @param entity 不再需要清理的实体
     */
    public static void cancel(Entity entity) {
        try {
            UUID entityUuid = entity.getUUID();
            ResourceLocation dimension = entity.level().dimension().location();
            
            // 从实体缓存中移除
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            if (queue != null) {
                queue.removeIf(report -> report.entity().equals(entity));
            }
            
            // 从UUID记录中移除
            Set<UUID> reported = reportedUuids.get(dimension);
            if (reported != null) {
                reported.remove(entityUuid);
            }
        } catch (Exception e) {
            // 出错就跳过，什么都不做
        }
    }
    
    /**
     * 获取维度的所有上报实体
     * @param dimension 维度ID
     * @return 该维度所有上报的实体列表
     */
    public static List<Entity> getReported(ResourceLocation dimension) {
        try {
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            return queue != null ? 
                queue.stream().map(EntityReport::entity).toList() : 
                new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>(); // 出错返回空列表
        }
    }
    
    /**
     * 获取维度的所有实体报告
     * @param dimension 维度ID
     * @return 该维度所有EntityReport列表
     */
    public static List<EntityReport> getReportedEntries(ResourceLocation dimension) {
        try {
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>(); // 出错返回空列表
        }
    }
    
    /**
     * 获取指定维度中每个区块的实体数量统计
     * @param dimension 维度ID
     * @return 区块位置 -> 实体数量的映射
     */
    public static Map<ChunkPos, Integer> getEntityCountByChunk(ResourceLocation dimension) {
        try {
            Map<ChunkPos, Integer> chunkStats = new HashMap<>();
            ConcurrentLinkedQueue<EntityReport> queue = cache.get(dimension);
            
            if (queue != null) {
                for (EntityReport report : queue) {
                    ChunkPos chunkPos = report.chunkPos();
                    chunkStats.put(chunkPos, chunkStats.getOrDefault(chunkPos, 0) + 1);
                }
            }
            
            return chunkStats;
        } catch (Exception e) {
            return new HashMap<>(); // 出错返回空映射
        }
    }
    
    /**
     * 获取超过指定阈值的区块列表
     * @param dimension 维度ID
     * @param threshold 实体数量阈值
     * @return 超过阈值的区块列表
     */
    public static List<ChunkPos> getChunksExceedingThreshold(ResourceLocation dimension, int threshold) {
        try {
            Map<ChunkPos, Integer> chunkStats = getEntityCountByChunk(dimension);
            return chunkStats.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>(); // 出错返回空列表
        }
    }
    
    /**
     * 清空指定维度的缓存 - 同步清理实体缓存和UUID记录
     * @param dimension 维度ID
     */
    public static void clear(ResourceLocation dimension) {
        try {
            cache.remove(dimension);
            reportedUuids.remove(dimension); // 同步清理UUID记录
        } catch (Exception e) {
            // 出错就跳过
        }
    }
    
    /**
     * 获取缓存状态信息（用于调试）- 包含UUID去重统计
     * @return 缓存统计信息
     */
    public static String getCacheStatus() {
        try {
            int dimensionCount = cache.size();
            int totalEntities = cache.values().stream()
                .mapToInt(Queue::size)
                .sum();
            int totalUuids = reportedUuids.values().stream()
                .mapToInt(Set::size)
                .sum();
            return String.format("Dimensions: %d, Total entities: %d, Total UUIDs: %d", 
                dimensionCount, totalEntities, totalUuids);
        } catch (Exception e) {
            return "Cache status unavailable";
        }
    }
    
    /**
     * 实体上报记录
     * @param entity 上报的实体
     * @param chunkPos 实体所在的区块位置
     * @param dimension 实体所在的维度
     */
    public record EntityReport(Entity entity, ChunkPos chunkPos, ResourceLocation dimension) {}
}