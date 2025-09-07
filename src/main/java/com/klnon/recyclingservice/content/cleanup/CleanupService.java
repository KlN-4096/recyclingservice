package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.entity.EntityCache;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
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
                // 直接统计各类实体数量，无需创建EntityReport对象
                int itemCount = EntityCache.getEntityCount(dimensionId, EntityType.ITEM);
                int totalCount = EntityCache.getReportedCount(dimensionId);
                int projectileCount = totalCount - itemCount; // 弹射物数量 = 总数 - 物品数量
                
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