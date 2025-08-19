package com.klnon.recyclingservice.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * 物品扫描器 - 异步扫描维度中的掉落物和弹射物
 * 使用CompletableFuture避免阻塞主线程，提升性能
 */
public class ItemScanner {
    /**
     * 异步扫描单个维度的所有掉落物和弹射物
     * @param level 服务器维度
     * @return CompletableFuture包装的扫描结果
     */
    public static CompletableFuture<ScanResult> scanDimensionAsync(ServerLevel level) {
        return CompletableFuture.supplyAsync(() -> {
            List<ItemEntity> items = new ArrayList<>();
            List<Entity> projectiles = new ArrayList<>();
            
            try {
                // 快速复制实体列表，避免阻塞主线程,同时也避免主线程同步修改实体导致崩溃
                List<Entity> entitySnapshot = new ArrayList<>();
                synchronized(level.getEntities()) {
                    level.getAllEntities().forEach(entitySnapshot::add);
                }
                
                // 在同步块外进行筛选，避免长时间持有锁
                entitySnapshot.forEach(entity -> {
                    if (entity instanceof ItemEntity itemEntity) {
                        // 检查物品是否有效
                        ItemStack itemStack = itemEntity.getItem();
                        if (!itemStack.isEmpty()) {
                            items.add(itemEntity);
                        }
                    } else if (ItemFilter.shouldCleanProjectile(entity)) {
                        // 检查弹射物是否应该被清理
                        projectiles.add(entity);
                    }
                });
                
            } catch (Exception e) {
                // 记录异常但不中断扫描
                // 可以添加日志记录
                return new ScanResult(Collections.emptyList(), Collections.emptyList());
            }
            
            return new ScanResult(items, projectiles);
        }, ForkJoinPool.commonPool());
    }

    /**
     * 异步扫描所有维度的掉落物和弹射物，按维度分类返回
     * @param server 服务器实例
     * @return CompletableFuture包装的维度ID -> 扫描结果映射
     */
    public static CompletableFuture<Map<ResourceLocation, ScanResult>> scanAllDimensionsAsync(MinecraftServer server) {
        List<CompletableFuture<Map.Entry<ResourceLocation, ScanResult>>> futures = new ArrayList<>();
        
        // 并行扫描所有维度
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();
            
            CompletableFuture<Map.Entry<ResourceLocation, ScanResult>> future = 
                scanDimensionAsync(level).thenApply(result -> 
                    result.isEmpty() ? null : Map.entry(dimensionId, result)
                );
            futures.add(future);
        }
        
        // 等待所有扫描完成并收集结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<ResourceLocation, ScanResult> dimensionResults = new HashMap<>();
                    for (CompletableFuture<Map.Entry<ResourceLocation, ScanResult>> future : futures) {
                        try {
                            Map.Entry<ResourceLocation, ScanResult> entry = future.get();
                            if (entry != null) {
                                dimensionResults.put(entry.getKey(), entry.getValue());
                            }
                        } catch (Exception e) {
                            // 忽略单个维度的扫描失败，继续处理其他维度
                        }
                    }
                    return dimensionResults;
                });
    }
    
    /**
     * 扫描结果类 - 包含物品和弹射物
     */
    public static class ScanResult {
        private final List<ItemEntity> items;
        private final List<Entity> projectiles;
        
        public ScanResult(List<ItemEntity> items, List<Entity> projectiles) {
            this.items = items;
            this.projectiles = projectiles;
        }

        public List<ItemEntity> getItems() {
            return items;
        }
        
        public List<Entity> getProjectiles() {
            return projectiles;
        }
        
        public boolean isEmpty() {
            return items.isEmpty() && projectiles.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("ScanResult{items=%d, projectiles=%d}", 
                               items.size(), projectiles.size());
        }
    }
}