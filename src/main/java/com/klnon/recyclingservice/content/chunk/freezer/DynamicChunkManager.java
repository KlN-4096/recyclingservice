package com.klnon.recyclingservice.content.chunk.freezer;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.freezer.FreezeDataStore.ChunkInfo;
import com.klnon.recyclingservice.content.chunk.freezer.FreezeDataStore.ChunkSuspensionCandidate;
import com.klnon.recyclingservice.content.chunk.performance.PerformanceMonitor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * 动态区块管理器
 * 负责根据服务器性能动态管理区块加载
 */
public class DynamicChunkManager {
    
    /**
     * 执行动态区块管理
     */
    public static void performDynamicChunkManagement(MinecraftServer server) {
        if (!Config.TECHNICAL.enableDynamicChunkManagement.get()) {
            return;
        }
        
        try {
            double averageTickTime = PerformanceMonitor.getAverageTickTime(server);
            double tps = PerformanceMonitor.calculateTPS(averageTickTime);
            
            double tpsThreshold = Config.TECHNICAL.tpsThreshold.get();
            double msptSuspendThreshold = Config.TECHNICAL.msptThresholdSuspend.get();
            double msptRestoreThreshold = Config.TECHNICAL.msptThresholdRestore.get();
            int operationCount = Config.TECHNICAL.chunkOperationCount.get();
            
            boolean shouldSuspend = tps < tpsThreshold || averageTickTime > msptSuspendThreshold;
            boolean canRestore = averageTickTime < msptRestoreThreshold;
            
            if (shouldSuspend && !FreezeDataStore.getManagedChunks().isEmpty()) {
                int suspended = suspendManagedChunks(server, operationCount);
                if (suspended > 0) {
                    Recyclingservice.LOGGER.info(
                        "Server performance degraded (TPS: {}, MSPT: {}), suspended {} chunks",
                        String.format("%.2f", tps), String.format("%.2f", averageTickTime), suspended);
                }
            } else if (canRestore && !FreezeDataStore.getSuspendedChunks().isEmpty()) {
                int restored = restoreSuspendedChunks(server, operationCount);
                if (restored > 0) {
                    Recyclingservice.LOGGER.info(
                        "Server performance improved (MSPT: {}), restored {} chunks",
                        String.format("%.2f", averageTickTime), restored);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform dynamic chunk management", e);
        }
    }
    
    /**
     * 暂停管理的区块
     */
    public static int suspendManagedChunks(MinecraftServer server, int count) {
        return suspendOrRestoreChunks(server, count, true);
    }
    
    /**
     * 恢复暂停的区块
     */
    public static int restoreSuspendedChunks(MinecraftServer server, int count) {
        return suspendOrRestoreChunks(server, count, false);
    }
    
    /**
     * 暂停或恢复区块 - 统一处理逻辑
     */
    private static int suspendOrRestoreChunks(MinecraftServer server, int count, boolean isSuspend) {
        if (count <= 0) return 0;
        
        Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> sourceChunks = isSuspend ? 
            FreezeDataStore.getManagedChunks() : FreezeDataStore.getSuspendedChunks();
        List<ChunkSuspensionCandidate> candidates = new ArrayList<>();
        
        // 收集候选区块
        for (var dimensionEntry : sourceChunks.entrySet()) {
            ResourceLocation dimensionId = dimensionEntry.getKey();
            ServerLevel level = server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null) continue;
            
            for (var chunkEntry : dimensionEntry.getValue().entrySet()) {
                ChunkPos chunkPos = chunkEntry.getKey();
                ChunkInfo chunkInfo = chunkEntry.getValue();
                candidates.add(new ChunkSuspensionCandidate(dimensionId, level, chunkPos, chunkInfo));
            }
        }
        
        // 排序：suspend从高到低，restore从低到高
        if (isSuspend) {
            candidates.sort((a, b) -> Integer.compare(b.chunkInfo().blockEntityCount(), a.chunkInfo().blockEntityCount()));
        } else {
            candidates.sort((a, b) -> Integer.compare(a.chunkInfo().blockEntityCount(), b.chunkInfo().blockEntityCount()));
        }
        
        // 执行操作
        int processed = 0;
        for (ChunkSuspensionCandidate candidate : candidates) {
            if (processed >= count) break;
            
            try {
                if (isSuspend) {
                    // 暂停操作
                    FreezeDataStore.removeManagedChunk(candidate.chunkPos(), candidate.level());
                    FreezeDataStore.addSuspendedChunk(candidate.dimensionId(), candidate.chunkPos(), candidate.chunkInfo());
                } else {
                    // 恢复操作
                    FreezeDataStore.removeSuspendedChunk(candidate.dimensionId(), candidate.chunkPos());
                    FreezeDataStore.addManagedChunk(candidate.chunkPos(), candidate.level(), candidate.chunkInfo().blockEntityCount());
                }
                processed++;
            } catch (Exception e) {
                String action = isSuspend ? "suspend" : "restore";
                Recyclingservice.LOGGER.debug("Failed to {} chunk ({}, {})", 
                    action, candidate.chunkPos().x, candidate.chunkPos().z, e);
            }
        }
        
        if (processed > 0) {
            String message = isSuspend ? "Suspended {} chunks due to server performance" : "Restored {} chunks due to improved server performance";
            Recyclingservice.LOGGER.info(message, processed);
        }
        
        return processed;
    }
}