package com.klnon.recyclingservice.util.scan;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.other.ErrorHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

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
            return ErrorHandler.handleStaticOperation(
                "scanDimension_" + level.dimension().location(),
                () -> {
                    List<ItemEntity> items = new ArrayList<>();
                    List<Entity> projectiles = new ArrayList<>();
                    int batchSize = Config.getBatchSize();
                    int scanRadius = Config.getPlayerScanRadius();
                    
                    if("chunk".equals(Config.getScanMode())){
                        // 1. 流式扫描强制加载的区块的物品和弹射物
                        ChunkScanner.findInForcedChunksStream(level, EntityType.ITEM, items::addAll, batchSize);
                        Config.getProjectileTypes().forEach(entityType -> {
                            ChunkScanner.findInForcedChunksStream(level, entityType, projectiles::addAll, batchSize);
                        });
                    }else{
                        // 2. 流式扫描所有在线玩家周围的区块的物品和弹射物
                        for (ServerPlayer player : level.players()) {
                            ChunkScanner.findAroundPlayerStream(level, player, scanRadius, EntityType.ITEM, 
                                items::addAll, batchSize);
                            Config.getProjectileTypes().forEach(entityType -> {
                                ChunkScanner.findAroundPlayerStream(level, player, scanRadius, entityType, projectiles::addAll, batchSize);
                            });
                        }
                    }
                    return new ScanResult(items, projectiles);
                },
                ScanResult.EMPTY
            );
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
            futures.add(
                scanDimensionAsync(level)
                    .thenApply(result -> result.isEmpty() ? null : 
                        Map.entry(level.dimension().location(), result))
            );
        }
        
        // CompletableFuture.allOf等待所有扫描完成并根据futures收集结果
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    
    /**
     * 扫描结果类 - 包含物品和弹射物
     */
    public static class ScanResult {
        public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());
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