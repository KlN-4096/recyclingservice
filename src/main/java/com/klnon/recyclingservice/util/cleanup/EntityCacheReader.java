package com.klnon.recyclingservice.util.cleanup;

import com.klnon.recyclingservice.Recyclingservice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * 基于缓存的实体收集器 - 直接从 SimpleReportCache 获取上报的实体
 */
public class EntityCacheReader {
    
    /**
     * 从缓存收集单个维度的实体报告
     * @param level 服务器维度
     * @return 收集结果
     */
    public static ScanResult collectReportedEntities(ServerLevel level) {
        try {
            ResourceLocation dimension = level.dimension().location();
            List<SimpleReportCache.EntityReport> reportedEntries = SimpleReportCache.getReportedEntries(dimension);
            
            // 直接从 EntityReport 分类，避免冗余转换
            List<ItemEntity> items = new ArrayList<>();
            List<Entity> projectiles = new ArrayList<>();
            
            for (SimpleReportCache.EntityReport report : reportedEntries) {
                try {
                    Entity entity = report.entity();
                    
                    // 验证实体仍然有效
                    if (entity.isRemoved() || !entity.isAlive() || entity.level().isClientSide()) {
                        continue; // 无效实体跳过
                    }
                    
                    // 直接分类，无需重复类型判断
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
            // 整个收集出错返回空结果
            return ScanResult.EMPTY;
        }
    }

    /**
     * 收集所有维度的上报实体，按维度分类返回
     * @param server 服务器实例
     * @return 维度ID -> 收集结果映射
     */
    public static Map<ResourceLocation, ScanResult> collectAllReportedEntities(MinecraftServer server) {
        Map<ResourceLocation, ScanResult> results = new HashMap<>();
        
        try {
            // 为所有维度执行收集
            for (ServerLevel level : server.getAllLevels()) {
                
                try {
                    ScanResult result = collectReportedEntities(level);
                    if (!result.isEmpty()) {
                        results.put(level.dimension().location(), result);
                    }
                } catch (Exception e) {
                    // 单个维度出错就跳过
                    Recyclingservice.LOGGER.debug("Failed to collect entities from dimension: {}, skipping", 
                        level.dimension().location(), e);
                }
            }
        } catch (Exception e) {
            // 出错返回空结果
            Recyclingservice.LOGGER.error("Failed to collect entities from all dimensions", e);
        }
        
        return results;
    }

    /**
     * 实体收集结果类 - 包含分类后的物品和弹射物
     */
    public record ScanResult(List<ItemEntity> items, List<Entity> projectiles) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());

        public boolean isEmpty() {
            return items.isEmpty() && projectiles.isEmpty();
        }
    }
}