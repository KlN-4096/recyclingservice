package com.klnon.recyclingservice.content.cleanup.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.ChunkManager;
import com.klnon.recyclingservice.content.cleanup.entity.EntityFilter;
import com.klnon.recyclingservice.content.cleanup.entity.EntityMerger;
import com.klnon.recyclingservice.content.cleanup.entity.EntityReportCache;
import com.klnon.recyclingservice.content.cleanup.signal.GlobalDeleteSignal;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

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
        try {
            Map<ResourceLocation, ScanResult> scanResults = collectAllReportedEntities(server);
            TrashBoxManager.clearAll();
            
            Map<ResourceLocation, DimensionCleanupStats> dimensionStats = new HashMap<>();
            int totalItemsCleaned = 0;
            int totalProjectilesCleaned = 0;
            boolean hasEntitiesToDelete = false;
            
            for (Map.Entry<ResourceLocation, ScanResult> entry : scanResults.entrySet()) {
                ResourceLocation dimensionId = entry.getKey();
                ScanResult scanResult = entry.getValue();
                
                try {
                    ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
                    ServerLevel level = server.getLevel(levelKey);
                    
                    if (level != null && Config.TECHNICAL.enableChunkFreezing.get()) {
                        ChunkManager.performFreezingCheck(dimensionId, level);
                    }
                    
                    List<ItemStack> itemStacksToClean = new ArrayList<>();
                    for (ItemEntity itemEntity : scanResult.items()) {
                        itemStacksToClean.add(itemEntity.getItem());
                    }
                    List<ItemStack> itemsToClean = EntityMerger.combine(itemStacksToClean);
                    
                    TrashBoxManager.addItemsToDimension(dimensionId, itemsToClean);
                    
                    List<Entity> projectilesToClean = EntityFilter.filterProjectiles(scanResult.projectiles());
                    
                    if (!scanResult.items().isEmpty() || !projectilesToClean.isEmpty()) {
                        hasEntitiesToDelete = true;
                    }
                    
                    DimensionCleanupStats stats = new DimensionCleanupStats(
                        itemStacksToClean.size(), projectilesToClean.size(), "Cleaned successfully");
                    dimensionStats.put(dimensionId, stats);
                    
                    totalItemsCleaned += itemStacksToClean.size();
                    totalProjectilesCleaned += projectilesToClean.size();
                    
                } catch (Exception e) {
                    dimensionStats.put(dimensionId, new DimensionCleanupStats(0, 0, "Failed: " + e.getMessage()));
                }
            }
            
            if (hasEntitiesToDelete) {
                GlobalDeleteSignal.activate(server);
            }
            
            clearProcessedCache(new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, dimensionStats, ""));
            
            return new CleanupResult(totalItemsCleaned, totalProjectilesCleaned, 
                dimensionStats, "Cleanup completed successfully");
                
        } catch (Exception e) {
            return new CleanupResult(0, 0, Collections.emptyMap(), 
                "Cleanup failed: " + e.getMessage());
        }
    }
    
    private static Map<ResourceLocation, ScanResult> collectAllReportedEntities(MinecraftServer server) {
        Map<ResourceLocation, ScanResult> results = new HashMap<>();
        
        try {
            for (ServerLevel level : server.getAllLevels()) {
                try {
                    ScanResult result = collectReportedEntities(level);
                    if (!result.isEmpty()) {
                        results.put(level.dimension().location(), result);
                    }
                } catch (Exception e) {
                    Recyclingservice.LOGGER.debug("Failed to collect entities from dimension: {}, skipping", 
                        level.dimension().location(), e);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to collect entities from all dimensions", e);
        }
        
        return results;
    }
    
    private static ScanResult collectReportedEntities(ServerLevel level) {
        try {
            ResourceLocation dimension = level.dimension().location();
            List<EntityReportCache.EntityReport> reportedEntries = EntityReportCache.getReportedEntries(dimension);
            
            List<ItemEntity> items = new ArrayList<>();
            List<Entity> projectiles = new ArrayList<>();
            
            for (EntityReportCache.EntityReport report : reportedEntries) {
                try {
                    Entity entity = report.entity();
                    
                    if (entity.isRemoved() || !entity.isAlive()) {
                        continue;
                    }
                    
                    if (entity instanceof ItemEntity itemEntity) {
                        items.add(itemEntity);
                    } else {
                        projectiles.add(entity);
                    }
                } catch (Exception e) {
                    // 单个实体出错就跳过
                }
            }
            
            return new ScanResult(items, projectiles);
            
        } catch (Exception e) {
            return ScanResult.EMPTY;
        }
    }
    
    private static void clearProcessedCache(CleanupResult result) {
        try {
            int totalCleanedEntities = 0;
            
            for (ResourceLocation dimension : result.dimensionStats().keySet()) {
                int cleanedInDimension = EntityReportCache.removeInvalidEntities(dimension);
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
     * 实体收集结果
     */
    public record ScanResult(List<ItemEntity> items, List<Entity> projectiles) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());

        public boolean isEmpty() {
            return items.isEmpty() && projectiles.isEmpty();
        }
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