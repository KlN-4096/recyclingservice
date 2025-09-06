package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * 基于性能的区块控制器
 */
public class PerformanceBasedController {
    
    /**
     * 执行性能调整
     */
    public static void performAdjustment(MinecraftServer server) {
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
            
            if (shouldSuspend) {
                int suspended = adjustChunks(server, operationCount, 
                    ChunkState.MANAGED, ChunkState.PERFORMANCE_FROZEN, true, "suspend");
                if (suspended > 0) {
                    Recyclingservice.LOGGER.info(
                        "Performance degraded (TPS: {}, MSPT: {}), suspended {} chunks",
                        String.format("%.2f", tps), String.format("%.2f", averageTickTime), suspended);
                }
            } else if (canRestore) {
                int restored = adjustChunks(server, operationCount,
                    ChunkState.PERFORMANCE_FROZEN, ChunkState.MANAGED, false, "restore");
                if (restored > 0) {
                    Recyclingservice.LOGGER.info(
                        "Performance improved (MSPT: {}), restored {} chunks",
                        String.format("%.2f", averageTickTime), restored);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform performance adjustment", e);
        }
    }
    
    /**
     * 通用区块调整方法
     */
    private static int adjustChunks(MinecraftServer server, int count, 
                                  ChunkState sourceState, ChunkState targetState, 
                                  boolean prioritizeHeavy, String operation) {
        if (count <= 0) return 0;
        
        Map<ResourceLocation, Set<ChunkPos>> chunks = ChunkDataStore.getChunksByState(sourceState);
        List<ChunkAdjustment> adjustments = new ArrayList<>();
        
        // 收集调整候选
        for (var entry : chunks.entrySet()) {
            ResourceLocation dimensionId = entry.getKey();
            ServerLevel level = server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null) continue;
            
            for (ChunkPos pos : entry.getValue()) {
                ChunkInfo info = ChunkDataStore.getChunk(dimensionId, pos);
                if (info != null) {
                    adjustments.add(new ChunkAdjustment(dimensionId, level, pos, info.blockEntityCount()));
                }
            }
        }
        
        // 排序：heavy优先=降序，light优先=升序
        adjustments.sort(prioritizeHeavy ? 
            (a, b) -> Integer.compare(b.blockEntityCount, a.blockEntityCount) :
            (a, b) -> Integer.compare(a.blockEntityCount, b.blockEntityCount));
        
        // 执行调整
        int processed = 0;
        for (ChunkAdjustment adj : adjustments) {
            if (processed >= count) break;
            
            try {
                if (ChunkDataStore.transitionChunkState(adj.dimensionId, adj.pos, targetState, adj.level)) {
                    processed++;
                }
            } catch (Exception e) {
                Recyclingservice.LOGGER.debug("Failed to {} chunk ({}, {})", 
                    operation, adj.pos.x, adj.pos.z, e);
            }
        }
        
        return processed;
    }
    
    private record ChunkAdjustment(ResourceLocation dimensionId, ServerLevel level, 
                                 ChunkPos pos, int blockEntityCount) {
    }
}