package com.klnon.recyclingservice.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.management.ChunkFreezer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.klnon.recyclingservice.core.DimensionTrashManager;
import com.klnon.recyclingservice.util.cleanup.ItemFilter;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;
import com.klnon.recyclingservice.util.cleanup.EntityCacheReader;
import com.klnon.recyclingservice.util.cleanup.MainThreadScheduler;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 自动清理服务 - 基于实体主动上报的清理系统
 * 
 * 核心功能：
 * - 收集上报缓存中的待清理实体（零延迟，O(1)访问）
 * - 过滤物品和弹射物
 * - 存储物品到对应维度垃圾箱
 * - 异步删除原始实体（避免TPS下降）
 * 
 * 设计特点：缓存收集同步，实体删除异步，错误隔离
 */
public class CleanupService {
    // 全局垃圾箱管理器实例
    private static final DimensionTrashManager trashManager = new DimensionTrashManager();
    
    /**
     * 通用异常处理：创建失败的维度清理统计
     */
    private static DimensionCleanupStats createFailedStats(String errorMessage) {
        return new DimensionCleanupStats(0, 0, "Processing failed: " + errorMessage);
    }

    /**
     * 通用异常处理：创建失败的清理结果
     */
    private static CleanupResult createFailedResult(String errorMessage) {
        return new CleanupResult(0, 0, Collections.emptyMap(), errorMessage);
    }
    
    /**
     * 执行自动清理 - 基于主动上报系统的主入口方法
     * @param server 服务器实例
     * @return CompletableFuture包装的清理结果
     */
    public static CompletableFuture<CleanupResult> performAutoCleanup(MinecraftServer server) {
        
        try {
            // 同步收集缓存（轻量级操作，无需异步）
            Map<ResourceLocation, EntityCacheReader.ScanResult> scanResults = 
                EntityCacheReader.collectAllReportedEntities(server);
            
            // 异步处理清理任务（保留异步，因为需要主线程调度删除）
            return processCleanupAsync(server, scanResults)
                    .thenApply(result -> {
                        // 清理完成后清空缓存
                        clearProcessedCache(result);
                        return result;
                    })
                    .exceptionally(throwable -> createFailedResult("Cleanup failed: " + throwable.getMessage()));
        } catch (Exception e) {
            // 缓存收集阶段出错，返回失败结果
            return CompletableFuture.completedFuture(
                createFailedResult("Cache collection failed: " + e.getMessage()));
        }
    }
    
    /**
     * 清空已处理的缓存
     * @param result 清理结果
     */
    private static void clearProcessedCache(CleanupResult result) {
        try {
            // 简单粗暴：清空所有维度的缓存
            for (ResourceLocation dimension : result.dimensionStats().keySet()) {
                SimpleReportCache.clear(dimension);
            }
            
            // 记录缓存清理信息
            if (!result.dimensionStats().isEmpty()) {
                com.klnon.recyclingservice.Recyclingservice.LOGGER.debug(
                    "Cleared report cache for {} dimensions", result.dimensionStats().size());
            }
        } catch (Exception e) {
            // 出错跳过，不影响主流程
            com.klnon.recyclingservice.Recyclingservice.LOGGER.debug("Failed to clear cache", e);
        }
    }
    
    /**
     * 异步处理清理任务 - 并行处理所有维度
     * @param server 服务器实例
     * @param scanResults 从上报缓存获取的结果映射 (维度ID -> 缓存结果)
     * @return CompletableFuture包装的清理结果
     */
    private static CompletableFuture<CleanupResult> processCleanupAsync(
            MinecraftServer server, Map<ResourceLocation, EntityCacheReader.ScanResult> scanResults) {
        
        // 清空所有垃圾桶的旧物品
        trashManager.clearAll();
        
        // 为每个维度创建异步清理任务
        List<CompletableFuture<Map.Entry<ResourceLocation, DimensionCleanupStats>>> cleanupFutures = 
            scanResults.entrySet().stream()
                .map(entry -> {
                    ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, entry.getKey());
                    ServerLevel level = server.getLevel(levelKey);
                    return processDimensionCleanupAsync(entry.getKey(), level, entry.getValue())
                        .thenApply(stats -> Map.entry(entry.getKey(), stats))
                        .exceptionally(throwable -> Map.entry(entry.getKey(), 
                            createFailedStats(throwable.getMessage())));
                })
                .toList();
        
        // 等待所有维度清理完成并汇总结果
        return CompletableFuture.allOf(cleanupFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> collectCleanupResults(cleanupFutures));
    }
    
    /**
     * 处理单个维度的清理任务
     * 步骤：区块冻结检查 -> 过滤物品弹射物 -> 存储到垃圾箱 -> 删除实体
     * @param dimensionId 维度ID
     * @param level 服务器维度实例
     * @param scanResult 该维度的扫描结果
     * @return CompletableFuture包装的维度清理统计
     */
    private static CompletableFuture<DimensionCleanupStats> processDimensionCleanupAsync(
            ResourceLocation dimensionId,
            ServerLevel level,
            EntityCacheReader.ScanResult scanResult) {
        
        // 1. 区块冻结检查 - 在清理前检查每个区块的实体数量
        if (level != null && Config.ENABLE_CHUNK_FREEZING.get()) {
            ChunkFreezer.performChunkFreezingCheck(dimensionId, level);
        }
        
        // 2. 优化：一次过滤获得物品和实体，避免重复判断
        ItemFilter.FilterResult<ItemEntity> itemFilterResult = ItemFilter.filterItemEntities(scanResult.items());
        List<ItemStack> itemsToClean = ItemMerge.combine(itemFilterResult.itemStacks());
        
        // 将物品存储到对应维度的垃圾箱
        trashManager.addItemsToDimension(dimensionId, itemsToClean);
        
        // 准备待删除实体列表
        List<Entity> entitiesToDelete = new ArrayList<>(itemFilterResult.entities());
        
        // 添加需要清理的弹射物
        List<Entity> projectilesToClean = ItemFilter.filterProjectiles(scanResult.projectiles());
        entitiesToDelete.addAll(projectilesToClean);
        entitiesToDelete.addAll(itemFilterResult.entities());
        
        // 计算实际清理的物品总数
        // int totalItemCount = itemsToClean.stream().mapToInt(ItemStack::getCount).sum();
        
        // 分片删除实体
        return entitiesToDelete.isEmpty() && itemsToClean.isEmpty()
            ? CompletableFuture.completedFuture(null)
            // 主线程任务调度器,分片删除
            : MainThreadScheduler.getInstance().scheduleEntityDeletion(entitiesToDelete)
            .thenApply(v -> new DimensionCleanupStats(
                itemFilterResult.itemStacks().size(), projectilesToClean.size(), "Cleaned successfully"));
    }


    /**
     * 收集所有维度的清理结果
     */
    private static CleanupResult collectCleanupResults(
        List<CompletableFuture<Map.Entry<ResourceLocation, DimensionCleanupStats>>> cleanupFutures) {
        
        Map<ResourceLocation, DimensionCleanupStats> dimensionStats = new HashMap<>();
        int totalItemsCleaned = 0;
        int totalProjectilesCleaned = 0;
        
        for (CompletableFuture<Map.Entry<ResourceLocation, DimensionCleanupStats>> future : cleanupFutures) {
            try {
                Map.Entry<ResourceLocation, DimensionCleanupStats> entry = future.get();
                dimensionStats.put(entry.getKey(), entry.getValue());
                totalItemsCleaned += entry.getValue().itemsCleaned();
                totalProjectilesCleaned += entry.getValue().projectilesCleaned();
            } catch (Exception e) {
                // 单个维度失败不影响整体结果
            }
        }
        
        return new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, 
            dimensionStats, "Cleanup completed successfully");
    }
    
    
    /**
     * 获取垃圾箱管理器实例
     * 用于外部访问垃圾箱系统
     * 
     * @return 垃圾箱管理器实例
     */
    public static DimensionTrashManager getTrashManager() {
        return trashManager;
    }

    /**
     * 清理结果总类 - 包含完整的清理统计信息
     * 包含信息：
     * - 总清理物品数量
     * - 总清理弹射物数量
     * - 分维度详细统计
     * - 清理状态消息
     */
    public record CleanupResult(int totalItemsCleaned, int totalProjectilesCleaned,
                                Map<ResourceLocation, DimensionCleanupStats> dimensionStats, String message) {
    }

    /**
     * 维度清理统计类 - 单个维度的清理结果
     * <p>
     * 记录信息：
     * - 该维度清理的物品数量
     * - 该维度清理的弹射物数量
     * - 清理状态（成功/失败及原因）
     */
        public record DimensionCleanupStats(int itemsCleaned, int projectilesCleaned, String status) {

        @Override
            public @Nonnull String toString() {
                return String.format("DimensionStats{items=%d, projectiles=%d, status='%s'}",
                        itemsCleaned, projectilesCleaned, status);
            }
        }
}