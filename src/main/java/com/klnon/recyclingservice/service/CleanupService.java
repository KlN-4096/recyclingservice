package com.klnon.recyclingservice.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.core.DimensionTrashManager;
import com.klnon.recyclingservice.util.cleanup.ItemFilter;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;
import com.klnon.recyclingservice.util.cleanup.ItemScanner;
import com.klnon.recyclingservice.util.cleanup.MainThreadScheduler;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 自动清理服务 - 核心清理功能实现
 * 功能：
 * - 异步扫描所有维度的掉落物和弹射物
 * - 根据配置过滤需要清理的物品
 * - 将清理的物品存储到对应维度的垃圾箱
 * - 删除原始实体，避免服务器性能问题
 * 设计原则：
 * - 异步处理：使用CompletableFuture避免阻塞主线程
 * - 错误隔离：单个维度清理失败不影响其他维度
 * - 统一管理：通过DimensionTrashManager管理所有垃圾箱
 */
public class CleanupService {
    // 全局垃圾箱管理器实例
    private static final DimensionTrashManager trashManager = new DimensionTrashManager();
    
    /**
     * 执行自动清理 - 主入口方法
     * 清理流程：
     * 1. 异步扫描所有维度的掉落物和弹射物
     * 2. 并行处理每个维度的清理任务
     * 3. 返回详细的清理结果统计
     * 
     * @param server 服务器实例
     * @return CompletableFuture包装的清理结果
     */
    public static CompletableFuture<CleanupResult> performAutoCleanup(MinecraftServer server) {
        return ItemScanner.scanAllDimensionsAsync(server)
                .thenCompose(CleanupService::processCleanupAsync)
                .exceptionally(throwable -> {
                    String errorMessage = "Cleanup failed: " + throwable.getMessage();
                    return new CleanupResult(0, 0, Collections.emptyMap(), errorMessage);
                });
    }
    
    /**
     * 异步处理清理任务 - 并行处理所有维度
     * 处理逻辑：
     * - 遍历所有扫描到的维度结果
     * - 为每个维度执行异步清理处理
     * - 收集统计信息并汇总
     * - 错误隔离：单个维度失败不影响其他维度
     * 
     * @param scanResults 扫描结果映射 (维度ID -> 扫描结果)
     * @return CompletableFuture包装的清理结果
     */
    private static CompletableFuture<CleanupResult> processCleanupAsync(
            Map<ResourceLocation, ItemScanner.ScanResult> scanResults) {
        
        // 清空所有垃圾桶的旧物品
        trashManager.clearAll();
        
        // 为每个维度创建异步清理任务
        List<CompletableFuture<Map.Entry<ResourceLocation, DimensionCleanupStats>>> cleanupFutures = 
            scanResults.entrySet().stream()
                .map(entry -> processDimensionCleanupAsync(entry.getKey(), entry.getValue())
                    .thenApply(stats -> Map.entry(entry.getKey(), stats))
                    .exceptionally(throwable -> Map.entry(entry.getKey(), 
                        new DimensionCleanupStats(0, 0, "Processing failed: " + throwable.getMessage()))))
                .toList();
        
        // 等待所有维度清理完成并汇总结果
        return CompletableFuture.allOf(cleanupFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> collectCleanupResults(cleanupFutures));
    }
    

    /**
     * 处理单个维度的清理任务
     * 清理步骤：
     * 1. 过滤出需要清理的掉落物品
     * 2. 将物品内容存储到垃圾箱
     * 3. 使用MainThreadScheduler分片删除实体（避免TPS下降）
     * 4. 异步返回清理统计结果
     * 
     * @param dimensionId 维度ID
     * @param scanResult 该维度的扫描结果
     * @return CompletableFuture包装的维度清理统计
     */
    private static CompletableFuture<DimensionCleanupStats> processDimensionCleanupAsync(
            ResourceLocation dimensionId,
            ItemScanner.ScanResult scanResult) {
        
        // 优化：一次过滤获得物品和实体，避免重复判断
        ItemFilter.FilterResult<ItemEntity> itemFilterResult = ItemFilter.filterItemEntities(scanResult.items());
        List<ItemStack> itemsToClean = ItemMerge.combine(itemFilterResult.getItemStacks());
        
        // 将物品存储到对应维度的垃圾箱
        trashManager.addItemsToDimension(dimensionId, itemsToClean);
        
        // 准备待删除实体列表
        List<Entity> entitiesToDelete = new ArrayList<>(itemFilterResult.getEntities());
        
        // 添加需要清理的弹射物
        List<Entity> projectilesToClean = ItemFilter.filterProjectiles(scanResult.projectiles());
        entitiesToDelete.addAll(projectilesToClean);
        
        // 计算实际清理的物品总数
        int totalItemCount = itemsToClean.stream().mapToInt(ItemStack::getCount).sum();
        
        // 分片删除实体
        return entitiesToDelete.isEmpty() && itemsToClean.isEmpty()
            ? CompletableFuture.completedFuture(null)
            // 主线程任务调度器,分片删除
            : MainThreadScheduler.getInstance().scheduleEntityDeletion(entitiesToDelete)
            .thenApply(v -> new DimensionCleanupStats(
                totalItemCount, projectilesToClean.size(), "Cleaned successfully"));
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