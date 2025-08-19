package com.klnon.recyclingservice.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.core.DimensionTrashManager;
import com.klnon.recyclingservice.util.ItemFilter;
import com.klnon.recyclingservice.util.ItemMerge;
import com.klnon.recyclingservice.util.ItemScanner;
import com.klnon.recyclingservice.Config;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 自动清理服务 - 核心清理功能实现
 * 
 * 功能：
 * - 异步扫描所有维度的掉落物和弹射物
 * - 根据配置过滤需要清理的物品
 * - 将清理的物品存储到对应维度的垃圾箱
 * - 删除原始实体，避免服务器性能问题
 * 
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
     * 
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
            .thenCompose(scanResults -> processCleanupAsync(server, scanResults))
            .exceptionally(throwable -> {
                // 清理失败时返回空结果，确保系统稳定性
                return new CleanupResult(0, 0, Collections.emptyMap(), 
                    "Cleanup failed: " + throwable.getMessage());
            });
    }
    
    /**
     * 异步处理清理任务 - 并行处理所有维度
     * 
     * 处理逻辑：
     * - 遍历所有扫描到的维度结果
     * - 为每个维度执行清理处理
     * - 收集统计信息并汇总
     * - 错误隔离：单个维度失败不影响其他维度
     * 
     * @param server 服务器实例
     * @param scanResults 扫描结果映射 (维度ID -> 扫描结果)
     * @return CompletableFuture包装的清理结果
     */
    private static CompletableFuture<CleanupResult> processCleanupAsync(
            MinecraftServer server, 
            Map<ResourceLocation, ItemScanner.ScanResult> scanResults) {
        
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, DimensionCleanupStats> dimensionStats = new ConcurrentHashMap<>();
            int totalItemsCleaned = 0;
            int totalProjectilesCleaned = 0;
            
            // 添加物品前,先清空所有垃圾桶的旧物品
            trashManager.clearAll();
            // 遍历所有维度，执行清理任务
            for (Map.Entry<ResourceLocation, ItemScanner.ScanResult> entry : scanResults.entrySet()) {
                ResourceLocation dimensionId = entry.getKey();
                ItemScanner.ScanResult scanResult = entry.getValue();
                
                try {
                    // 处理单个维度的清理
                    DimensionCleanupStats stats = processDimensionCleanup(server, dimensionId, scanResult);
                    dimensionStats.put(dimensionId, stats);
                    totalItemsCleaned += stats.itemsCleaned;
                    totalProjectilesCleaned += stats.projectilesCleaned;
                } catch (Exception e) {
                    // 单个维度清理失败不影响其他维度，记录错误信息
                    dimensionStats.put(dimensionId, new DimensionCleanupStats(0, 0, 
                        "Failed: " + e.getMessage()));
                }
            }
            
            return new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, 
                dimensionStats, "Cleanup completed successfully");
        });
    }
    
    /**
     * 处理单个维度的清理任务
     * 
     * 清理步骤：
     * 1. 过滤出需要清理的掉落物品
     * 2. 将物品内容存储到垃圾箱
     * 3. 在主线程中安全删除原始掉落物实体
     * 4. 在主线程中安全处理弹射物清理
     * 
     * @param server 服务器实例
     * @param dimensionId 维度ID
     * @param scanResult 该维度的扫描结果
     * @return 该维度的清理统计信息
     */
    private static DimensionCleanupStats processDimensionCleanup(
            MinecraftServer server,
            ResourceLocation dimensionId, 
            ItemScanner.ScanResult scanResult) {
        
        // 第一步：处理掉落物品
        // 使用ItemFilter过滤出需要清理的物品内容
        List<ItemStack> itemsToClean = ItemFilter.filterItems(scanResult.getItems());
        // 此处的合并是扫描时合并,与玩家操作时合并不同
        itemsToClean = ItemMerge.combine(itemsToClean);

        // 将物品内容存储到对应维度的垃圾箱
        int itemsAddedToTrash = trashManager.addItemsToDimension(dimensionId, itemsToClean);
        
        // 第二步：在主线程中安全删除已清理的掉落物实体
        // 获取需要删除的掉落物实体列表
        List<ItemEntity> itemEntitiesToRemove = scanResult.getItems().stream()
            .filter(entity -> ItemFilter.shouldCleanItem(entity.getItem()))
            .toList();
        
        // 第三步：获取需要清理的弹射物
        List<Entity> projectilesToClean = ItemFilter.filterProjectiles(scanResult.getProjectiles());
        
        // 第四步：在主线程中安全删除所有实体
        if (!itemEntitiesToRemove.isEmpty() || !projectilesToClean.isEmpty()) {
            CountDownLatch latch = new CountDownLatch(1);
            
            server.execute(() -> {
                try {
                    // 删除掉落物实体（现在在主线程中安全执行）
                    for (ItemEntity entity : itemEntitiesToRemove) {
                        entity.discard();
                    }
                    
                    // 删除弹射物实体（现在在主线程中安全执行）
                    for (Entity projectile : projectilesToClean) {
                        projectile.discard();
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            // 等待主线程操作完成
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return new DimensionCleanupStats(itemsAddedToTrash, projectilesToClean.size(), 
            "Cleaned successfully");
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
     * 清理结果类 - 包含完整的清理统计信息
     * 
     * 包含信息：
     * - 总清理物品数量
     * - 总清理弹射物数量
     * - 分维度详细统计
     * - 清理状态消息
     */
    public static class CleanupResult {
        private final int totalItemsCleaned;
        private final int totalProjectilesCleaned;
        private final Map<ResourceLocation, DimensionCleanupStats> dimensionStats;
        private final String message;
        
        public CleanupResult(int totalItemsCleaned, int totalProjectilesCleaned, 
                           Map<ResourceLocation, DimensionCleanupStats> dimensionStats, 
                           String message) {
            this.totalItemsCleaned = totalItemsCleaned;
            this.totalProjectilesCleaned = totalProjectilesCleaned;
            this.dimensionStats = dimensionStats;
            this.message = message;
        }
        
        public int getTotalItemsCleaned() { return totalItemsCleaned; }
        public int getTotalProjectilesCleaned() { return totalProjectilesCleaned; }
        public Map<ResourceLocation, DimensionCleanupStats> getDimensionStats() { return dimensionStats; }
        public String getMessage() { return message; }
        
        /**
         * 获取格式化的清理完成消息
         * 使用配置文件中的消息模板
         * 
         * @return 格式化后的消息
         */
        public String getFormattedMessage() {
            return Config.getCleanupCompleteMessage(totalItemsCleaned + totalProjectilesCleaned);
        }
        
        @Override
        public String toString() {
            return String.format("CleanupResult{items=%d, projectiles=%d, dimensions=%d, message='%s'}", 
                totalItemsCleaned, totalProjectilesCleaned, dimensionStats.size(), message);
        }
    }
    
    /**
     * 维度清理统计类 - 单个维度的清理结果
     * 
     * 记录信息：
     * - 该维度清理的物品数量
     * - 该维度清理的弹射物数量
     * - 清理状态（成功/失败及原因）
     */
    public static class DimensionCleanupStats {
        private final int itemsCleaned;
        private final int projectilesCleaned;
        private final String status;
        
        public DimensionCleanupStats(int itemsCleaned, int projectilesCleaned, String status) {
            this.itemsCleaned = itemsCleaned;
            this.projectilesCleaned = projectilesCleaned;
            this.status = status;
        }
        
        public int getItemsCleaned() { return itemsCleaned; }
        public int getProjectilesCleaned() { return projectilesCleaned; }
        public String getStatus() { return status; }
        
        @Override
        public String toString() {
            return String.format("DimensionStats{items=%d, projectiles=%d, status='%s'}", 
                itemsCleaned, projectilesCleaned, status);
        }
    }
}