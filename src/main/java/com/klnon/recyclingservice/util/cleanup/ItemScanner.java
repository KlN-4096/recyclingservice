package com.klnon.recyclingservice.util.cleanup;

import com.klnon.recyclingservice.Recyclingservice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 基于缓存的扫描器 - 直接从 SimpleReportCache 获取上报的实体
 * 替代原有的区块遍历扫描方式，实现零延迟扫描
 */
public class ItemScanner {
    
    /**
     * 从缓存获取单个维度的实体
     * @param level 服务器维度
     * @return CompletableFuture包装的扫描结果
     */
    public static CompletableFuture<ScanResult> scanDimensionAsync(ServerLevel level) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResourceLocation dimension = level.dimension().location();
                List<Entity> reportedEntities = SimpleReportCache.getReported(dimension);
                
                // 简单分类
                List<ItemEntity> items = new ArrayList<>();
                List<Entity> projectiles = new ArrayList<>();
                
                for (Entity entity : reportedEntities) {
                    try {
                        // 验证实体仍然有效
                        if (entity.isRemoved() || !entity.isAlive()) {
                            continue; // 无效实体跳过
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
                // 整个扫描出错返回空结果
                return ScanResult.EMPTY;
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * 扫描所有维度的上报实体，按维度分类返回
     * @param server 服务器实例
     * @return CompletableFuture包装的维度ID -> 扫描结果映射
     */
    public static CompletableFuture<Map<ResourceLocation, ScanResult>> scanAllDimensionsAsync(MinecraftServer server) {
        return CompletableFuture.supplyAsync(() -> {
            Map<ResourceLocation, ScanResult> results = new HashMap<>();
            
            try {
                // 为所有维度执行扫描
                for (ServerLevel level : server.getAllLevels()) {
                    try {
                        ScanResult result = scanDimensionAsync(level).get();
                        if (!result.isEmpty()) {
                            results.put(level.dimension().location(), result);
                        }
                    } catch (Exception e) {
                        // 单个维度出错就跳过
                        Recyclingservice.LOGGER.debug("Failed to scan dimension: {}, skipping", 
                            level.dimension().location(), e);
                    }
                }
            } catch (Exception e) {
                // 出错返回空结果
                Recyclingservice.LOGGER.error("Failed to scan all dimensions", e);
            }
            
            return results;
        }, ForkJoinPool.commonPool());
    }

    /**
     * 扫描结果类 - 包含物品和弹射物
     */
    public record ScanResult(List<ItemEntity> items, List<Entity> projectiles) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());

        public boolean isEmpty() {
            return items.isEmpty() && projectiles.isEmpty();
        }
    }
}