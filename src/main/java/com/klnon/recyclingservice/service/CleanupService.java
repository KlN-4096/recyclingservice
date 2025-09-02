package com.klnon.recyclingservice.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
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
import com.klnon.recyclingservice.core.TrashManager;
import com.klnon.recyclingservice.util.cleanup.ItemFilter;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;
import com.klnon.recyclingservice.util.cleanup.EntityCacheReader;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;
import com.klnon.recyclingservice.util.cleanup.GlobalDeleteSignal;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 自动清理服务 - 简化的同步清理系统
 * 核心功能：
 * - 收集上报缓存中的待清理实体
 * - 存储物品到垃圾箱
 * - 激活删除信号，实体自删除
 * 设计特点：线性执行，简洁高效
 */
public class CleanupService {
    // 全局垃圾箱管理器实例
    private static final TrashManager trashManager = new TrashManager();
    
    /**
     * 执行自动清理 - 同步版本
     * @param server 服务器实例
     * @return 清理结果
     */
    public static CleanupResult performAutoCleanup(MinecraftServer server) {
        try {
            // 1. 收集缓存实体
            Map<ResourceLocation, EntityCacheReader.ScanResult> scanResults = 
                EntityCacheReader.collectAllReportedEntities(server);
            
            // 2. 清空垃圾箱
            trashManager.clearAll();
            
            // 3. 处理各维度
            Map<ResourceLocation, DimensionCleanupStats> dimensionStats = new HashMap<>();
            int totalItemsCleaned = 0;
            int totalProjectilesCleaned = 0;
            boolean hasEntitiesToDelete = false;
            
            for (Map.Entry<ResourceLocation, EntityCacheReader.ScanResult> entry : scanResults.entrySet()) {
                ResourceLocation dimensionId = entry.getKey();
                EntityCacheReader.ScanResult scanResult = entry.getValue();
                
                try {
                    // 获取维度
                    ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
                    ServerLevel level = server.getLevel(levelKey);
                    
                    // 区块冻结检查
                    if (level != null && Config.TECHNICAL.enableChunkFreezing.get()) {
                        ChunkFreezer.performChunkFreezingCheck(dimensionId, level);
                    }
                    
                    // 处理物品
                    List<ItemStack> itemStacksToClean = new ArrayList<>();
                    for (ItemEntity itemEntity : scanResult.items()) {
                        itemStacksToClean.add(itemEntity.getItem());
                    }
                    List<ItemStack> itemsToClean = ItemMerge.combine(itemStacksToClean);
                    
                    // 存储到垃圾箱
                    trashManager.addItemsToDimension(dimensionId, itemsToClean);
                    
                    // 统计弹射物
                    List<Entity> projectilesToClean = ItemFilter.filterProjectiles(scanResult.projectiles());
                    
                    // 检查是否有实体需要删除
                    if (!scanResult.items().isEmpty() || !projectilesToClean.isEmpty()) {
                        hasEntitiesToDelete = true;
                    }
                    
                    // 记录统计
                    DimensionCleanupStats stats = new DimensionCleanupStats(
                        itemStacksToClean.size(), projectilesToClean.size(), "Cleaned successfully");
                    dimensionStats.put(dimensionId, stats);
                    
                    totalItemsCleaned += itemStacksToClean.size();
                    totalProjectilesCleaned += projectilesToClean.size();
                    
                } catch (Exception e) {
                    // 单个维度失败不影响其他维度
                    dimensionStats.put(dimensionId, new DimensionCleanupStats(0, 0, "Failed: " + e.getMessage()));
                }
            }
            
            // 4. 激活删除信号
            if (hasEntitiesToDelete) {
                GlobalDeleteSignal.activate(server);
            }
            
            // 5. 清理无效缓存
            clearProcessedCache(new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, dimensionStats, ""));
            
            return new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, 
                dimensionStats, "Cleanup completed successfully");
                
        } catch (Exception e) {
            return new CleanupResult(0, 0, Collections.emptyMap(), 
                "Cleanup failed: " + e.getMessage());
        }
    }
    
    /**
     * 清理无效缓存
     */
    private static void clearProcessedCache(CleanupResult result) {
        try {
            int totalCleanedEntities = 0;
            
            for (ResourceLocation dimension : result.dimensionStats().keySet()) {
                int cleanedInDimension = SimpleReportCache.removeInvalidEntities(dimension);
                totalCleanedEntities += cleanedInDimension;
                
                if (Config.TECHNICAL.enableDebugLogs.get() && cleanedInDimension > 0) {
                    Recyclingservice.LOGGER.debug(
                        "Cleaned {} invalid entities from cache in dimension {}", 
                        cleanedInDimension, dimension);
                }
            }
            
            if (totalCleanedEntities > 0) {
                Recyclingservice.LOGGER.debug(
                    "Cleaned {} invalid entities from report cache across {} dimensions", 
                    totalCleanedEntities, result.dimensionStats().size());
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to clean processed cache", e);
        }
    }
    
    
    /**
     * 获取垃圾箱管理器实例
     * 用于外部访问垃圾箱系统
     * 
     * @return 垃圾箱管理器实例
     */
    public static TrashManager getTrashManager() {
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