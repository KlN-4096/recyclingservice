package com.klnon.recyclingservice.service;

import com.klnon.recyclingservice.util.management.ChunkFreezer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import com.klnon.recyclingservice.core.DimensionTrashManager;
import com.klnon.recyclingservice.util.cleanup.ItemFilter;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;
import com.klnon.recyclingservice.util.cleanup.ItemScanner;
import com.klnon.recyclingservice.util.cleanup.MainThreadScheduler;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 自动清理服务 - 基于主动上报系统的核心清理功能
 * 新架构特性：
 * - 零延迟清理：实体主动上报，无需扫描遍历
 * - 缓存消费：直接从SimpleReportCache获取待清理实体
 * - 性能优化：从O(n)扫描复杂度降为O(1)缓存访问
 * - 实时响应：实体状态变化立即反映到清理系统
 * 功能：
 * - 从上报缓存获取所有维度的待清理实体
 * - 根据配置过滤需要清理的物品和弹射物
 * - 将清理的物品存储到对应维度的垃圾箱
 * - 删除原始实体，避免服务器性能问题
 * - 清理完成后自动清空缓存
 * 设计原则：
 * - 异步处理：使用CompletableFuture避免阻塞主线程
 * - 错误隔离：单个维度清理失败不影响其他维度
 * - 容错性强：任何异常都跳过，不影响游戏运行
 * - 统一管理：通过DimensionTrashManager管理所有垃圾箱
 */
public class CleanupService {
    // 全局垃圾箱管理器实例
    private static final DimensionTrashManager trashManager = new DimensionTrashManager();
    
    /**
     * 执行自动清理 - 基于主动上报系统的主入口方法
     * @param server 服务器实例
     * @return CompletableFuture包装的清理结果
     */
    public static CompletableFuture<CleanupResult> performAutoCleanup(MinecraftServer server) {
        // 激进模式检查和触发（在清理开始时执行）
        if (com.klnon.recyclingservice.Config.shouldUseAggressiveMode(server)) {
            // 批量冻结所有非白名单区块
            int frozenChunks = ChunkFreezer.freezeAllNonWhitelistChunks(server);
            
            if (frozenChunks > 0) {
                // 记录激进模式激活信息
                double avgTickTime = server.getAverageTickTimeNanos();
                double tps = Math.min(20.0, 1000.0 / avgTickTime);
                
                com.klnon.recyclingservice.Recyclingservice.LOGGER.warn(
                    "Aggressive mode activated during cleanup! Server performance: TPS={}, MSPT={}ms - Frozen {} chunks",
                    tps, avgTickTime / 1_000_000.0, frozenChunks);
            }
        }
        
        return ItemScanner.scanAllDimensionsAsync(server)  // 现在直接从上报缓存获取
                .thenCompose(scanResults -> processCleanupAsync(server, scanResults))
                .thenApply(result -> {
                    // 清理完成后清空缓存
                    clearProcessedCache(result);
                    return result;
                })
                .exceptionally(throwable -> {
                    String errorMessage = "Cleanup failed: " + throwable.getMessage();
                    return new CleanupResult(0, 0, Collections.emptyMap(), errorMessage);
                });
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
     * 注意：这里的scanResults实际上是从缓存直接获取的结果，
     * 不是传统意义上的"扫描"结果
     * 
     * @param server 服务器实例
     * @param scanResults 从上报缓存获取的结果映射 (维度ID -> 缓存结果)
     * @return CompletableFuture包装的清理结果
     */
    private static CompletableFuture<CleanupResult> processCleanupAsync(
            MinecraftServer server, Map<ResourceLocation, ItemScanner.ScanResult> scanResults) {
        
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
                            new DimensionCleanupStats(0, 0, "Processing failed: " + throwable.getMessage())));
                })
                .toList();
        
        // 等待所有维度清理完成并汇总结果
        return CompletableFuture.allOf(cleanupFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> collectCleanupResults(cleanupFutures));
    }
    
    /**
     * 执行区块冻结检查 - 检查维度中每个区块的实体数量，冻结超过阈值的区块
     * @param dimensionId 维度ID
     * @param level 服务器维度实例
     */
    private static void performChunkFreezingCheck(ResourceLocation dimensionId, ServerLevel level) {
        try {
            // 获取区块实体数量统计
            List<ChunkPos> overloadedChunks = SimpleReportCache.getChunksExceedingThreshold(
                dimensionId, 
                com.klnon.recyclingservice.Config.TOO_MANY_ITEMS_WARNING.get()
            );
            
            // 对每个超载区块执行冻结
            for (ChunkPos chunkPos : overloadedChunks) {
                // 批量冻结所有影响目标区块的加载器
                ChunkFreezer.FreezeResult freezeResult = ChunkFreezer.freezeAllAffectingChunkLoaders(chunkPos, level);
                
                if (!freezeResult.isEmpty()) {
                    com.klnon.recyclingservice.Recyclingservice.LOGGER.info(
                        "Chunk freezing triggered during cleanup: Frozen {} chunk loaders affecting chunk ({}, {}) in {}: {} tickets removed", 
                        freezeResult.getFrozenChunkCount(), chunkPos.x, chunkPos.z, dimensionId, freezeResult.totalFrozenTickets());
                    
                    // 详细记录每个被冻结的区块
                    for (ChunkPos frozenChunk : freezeResult.frozenChunks()) {
                        com.klnon.recyclingservice.Recyclingservice.LOGGER.debug(
                            "  → Frozen chunk loader at ({}, {}) in dimension {}", 
                            frozenChunk.x, frozenChunk.z, dimensionId);
                    }
                } else {
                    // 如果找不到任何加载器，冻结当前区块（回退逻辑）
                    int frozenTickets = ChunkFreezer.freezeChunk(chunkPos, level);
                    if (frozenTickets > 0) {
                        com.klnon.recyclingservice.Recyclingservice.LOGGER.info(
                            "No affecting chunk loaders found during cleanup, frozen current chunk ({}, {}) in {} with {} tickets", 
                            chunkPos.x, chunkPos.z, dimensionId, frozenTickets);
                    }
                }
                
                // 发送警告消息（如果启用）
                if (com.klnon.recyclingservice.Config.isChunkWarningEnabled()) {
                    int entityCount = SimpleReportCache.getEntityCountByChunk(dimensionId).getOrDefault(chunkPos, 0);
                    int worldX = chunkPos.x * 16 + 8;
                    int worldZ = chunkPos.z * 16 + 8;
                    
                    // 获取ticketLevel（通过直接访问chunkMap）
                    int ticketLevel = 33; // 默认值：未加载
                    try {
                        var chunkHolderMap = level.getChunkSource().chunkMap.visibleChunkMap;
                        long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
                        var holder = chunkHolderMap.get(chunkKey);
                        if (holder != null) {
                            ticketLevel = holder.getTicketLevel();
                        }
                    } catch (Exception e) {
                        // 获取ticketLevel失败，使用默认值
                    }
                    
                    net.minecraft.network.chat.Component warningMessage = 
                        com.klnon.recyclingservice.Config.getItemWarningMessage(entityCount, worldX, worldZ, ticketLevel);
                    com.klnon.recyclingservice.util.core.MessageSender.sendChatMessage(level.getServer(), warningMessage);
                }
            }
            
            if (!overloadedChunks.isEmpty()) {
                com.klnon.recyclingservice.Recyclingservice.LOGGER.info(
                    "Chunk freezing check completed for dimension {}: {} overloaded chunks processed", 
                    dimensionId, overloadedChunks.size());
            }
            
        } catch (Exception e) {
            // 出错跳过，不影响清理流程
            com.klnon.recyclingservice.Recyclingservice.LOGGER.debug(
                "Failed to perform chunk freezing check for dimension {}", dimensionId, e);
        }
    }
    

    /**
     * 处理单个维度的清理任务
     * 清理步骤：
     * 1. 检查区块实体数量，触发区块冻结（如果需要）
     * 2. 过滤物品和弹射物
     * 3. 将物品存储到垃圾箱
     * 4. 删除实体
     * @param dimensionId 维度ID
     * @param level 服务器维度实例
     * @param scanResult 该维度的扫描结果
     * @return CompletableFuture包装的维度清理统计
     */
    private static CompletableFuture<DimensionCleanupStats> processDimensionCleanupAsync(
            ResourceLocation dimensionId,
            ServerLevel level,
            ItemScanner.ScanResult scanResult) {
        
        // 1. 区块冻结检查 - 在清理前检查每个区块的实体数量
        if (level != null && com.klnon.recyclingservice.Config.isChunkFreezingEnabled()) {
            performChunkFreezingCheck(dimensionId, level);
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
        
        // 计算实际清理的物品总数
        // int totalItemCount = itemsToClean.stream().mapToInt(ItemStack::getCount).sum();
        
        // 分片删除实体
        return entitiesToDelete.isEmpty() && itemsToClean.isEmpty()
            ? CompletableFuture.completedFuture(null)
            // 主线程任务调度器,分片删除
            : MainThreadScheduler.getInstance().scheduleEntityDeletion(entitiesToDelete)
            .thenApply(v -> new DimensionCleanupStats(
                entitiesToDelete.size(), projectilesToClean.size(), "Cleaned successfully"));
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