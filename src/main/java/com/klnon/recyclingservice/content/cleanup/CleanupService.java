package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.entity.EntityFilter;
import com.klnon.recyclingservice.content.cleanup.entity.EntityCache;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 清理服务 - 具体业务逻辑实现
 */
public class CleanupService {
    
    /**
     * 执行自动清理
     */
    public static CleanupResult performAutoCleanup(MinecraftServer server) {
        TrashBoxManager.clearAll();
        GlobalDeleteSignal.activate(server);
        
        Map<ResourceLocation, DimensionCleanupStats> dimensionStats = new HashMap<>();
        int totalItemsCleaned = 0;
        int totalProjectilesCleaned = 0;
        
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();
            
            try {
                // 直接从缓存获取并统计
                List<EntityCache.EntityReport> reports = CleanupManager.getReportedEntries(dimensionId);
                int itemCount = 0;
                int projectileCount = 0;
                
                for (EntityCache.EntityReport report : reports) {
                    try {
                        Entity entity = report.entity();
                        if (entity.isRemoved() || !entity.isAlive()) continue;
                        
                        if (entity instanceof ItemEntity) {
                            itemCount++;
                        } else if (EntityFilter.shouldCleanProjectile(entity)) {
                            projectileCount++;
                        }
                    } catch (Exception e) {
                        // 单个实体出错就跳过
                    }
                }
                
                // 清理缓存
                CleanupManager.removeInvalidEntities(dimensionId);
                
                // 记录统计
                if (itemCount > 0 || projectileCount > 0) {
                    dimensionStats.put(dimensionId, new DimensionCleanupStats(itemCount, projectileCount, "OK"));
                    totalItemsCleaned += itemCount;
                    totalProjectilesCleaned += projectileCount;
                }
                
            } catch (Exception e) {
                Recyclingservice.LOGGER.debug("Failed to cleanup dimension {}: {}", dimensionId, e.getMessage());
                dimensionStats.put(dimensionId, new DimensionCleanupStats(0, 0, "Failed"));
            }
        }
        
        return new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, 
            dimensionStats, "Cleanup completed successfully");
    }

    /**
     * 清理结果
     */
    public record CleanupResult(int totalItemsCleaned, int totalProjectilesCleaned,
                                Map<ResourceLocation, DimensionCleanupStats> dimensionStats, String message) {
    }

    /**
     * 维度清理统计
     */
    public record DimensionCleanupStats(int itemsCleaned, int projectilesCleaned, String status) {

        @Override
        public @Nonnull String toString() {
            return String.format("DimensionStats{items=%d, projectiles=%d, status='%s'}",
                    itemsCleaned, projectilesCleaned, status);
        }
    }
}