package com.klnon.recyclingservice.util.cleanup;

import com.klnon.recyclingservice.Recyclingservice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.core.ErrorHandler;

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
        return CompletableFuture.supplyAsync(() -> ErrorHandler.handleOperation(
            null, // 无玩家上下文
            "scanDimension_" + level.dimension().location(),
            () -> {
                List<ItemEntity> items = new ArrayList<>();
                List<Entity> projectiles = new ArrayList<>();
                int batchSize = Config.getBatchSize();
                int scanRadius = Config.getPlayerScanRadius();

                // 构建所有需要扫描的实体类型集合
                Set<EntityType<?>> allEntityTypes = new HashSet<>(Config.getProjectileTypes());
                allEntityTypes.add(EntityType.ITEM);

                if("chunk".equals(Config.getScanMode())){
                    // 1. 流式扫描强制加载的区块的物品和弹射物
                    ChunkScanner.findInForcedChunksStream(level, allEntityTypes, batch -> {
                        items.addAll(batch.getItems());
                        projectiles.addAll(batch.getProjectiles());
                    }, batchSize);
                }else{
                    // 2. 流式扫描所有在线玩家周围的区块的物品和弹射物
                    for (ServerPlayer player : level.players()) {
                        ChunkScanner.findAroundPlayerStream(level, player, scanRadius, allEntityTypes, batch -> {
                            items.addAll(batch.getItems());
                            projectiles.addAll(batch.getProjectiles());
                        }, batchSize);
                    }
                }
                return new ScanResult(items, projectiles);
            },
            ScanResult.EMPTY
        ), ForkJoinPool.commonPool());
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
        public record ScanResult(List<ItemEntity> items, List<Entity> projectiles) {
            public static final ScanResult EMPTY = new ScanResult(List.of(), List.of());

        public boolean isEmpty() {
                return items.isEmpty() && projectiles.isEmpty();
            }
        }
}