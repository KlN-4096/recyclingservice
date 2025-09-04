package com.klnon.recyclingservice.content.chunk.management.performance;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkDataStore;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkInfo;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkState;
import com.klnon.recyclingservice.content.chunk.performance.PerformanceMonitor;
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
                int suspended = suspendChunks(server, operationCount);
                if (suspended > 0) {
                    Recyclingservice.LOGGER.info(
                        "Performance degraded (TPS: {}, MSPT: {}), suspended {} chunks",
                        String.format("%.2f", tps), String.format("%.2f", averageTickTime), suspended);
                }
            } else if (canRestore) {
                int restored = restoreChunks(server, operationCount);
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
     * 暂停管理的区块
     */
    private static int suspendChunks(MinecraftServer server, int count) {
        if (count <= 0) return 0;
        
        List<ChunkCandidate> candidates = new ArrayList<>();
        
        // 收集MANAGED状态的区块
        Map<ResourceLocation, Set<ChunkPos>> managedChunks = 
            ChunkDataStore.getChunksByState(ChunkState.MANAGED);
        
        for (var dimensionEntry : managedChunks.entrySet()) {
            ResourceLocation dimensionId = dimensionEntry.getKey();
            ServerLevel level = server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null) continue;
            
            for (ChunkPos pos : dimensionEntry.getValue()) {
                ChunkInfo info = ChunkDataStore.getChunk(dimensionId, pos);
                if (info != null) {
                    candidates.add(new ChunkCandidate(dimensionId, level, pos, info));
                }
            }
        }
        
        // 按block entity数量降序排序(优先暂停重的区块)
        candidates.sort((a, b) -> Integer.compare(
            b.info().blockEntityCount(), a.info().blockEntityCount()));
        
        return processChunkCandidates(candidates, count, ChunkState.PERFORMANCE_FROZEN, "suspend");
    }
    
    /**
     * 恢复性能冻结的区块
     */
    private static int restoreChunks(MinecraftServer server, int count) {
        if (count <= 0) return 0;
        
        List<ChunkCandidate> candidates = new ArrayList<>();
        
        // 收集PERFORMANCE_FROZEN状态的区块
        Map<ResourceLocation, Set<ChunkPos>> frozenChunks = 
            ChunkDataStore.getChunksByState(ChunkState.PERFORMANCE_FROZEN);
        
        for (var dimensionEntry : frozenChunks.entrySet()) {
            ResourceLocation dimensionId = dimensionEntry.getKey();
            ServerLevel level = server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null) continue;
            
            for (ChunkPos pos : dimensionEntry.getValue()) {
                ChunkInfo info = ChunkDataStore.getChunk(dimensionId, pos);
                if (info != null) {
                    candidates.add(new ChunkCandidate(dimensionId, level, pos, info));
                }
            }
        }
        
        // 按block entity数量升序排序(优先恢复轻的区块)
        candidates.sort((a, b) -> Integer.compare(
            a.info().blockEntityCount(), b.info().blockEntityCount()));
        
        return processChunkCandidates(candidates, count, ChunkState.MANAGED, "restore");
    }
    
    private static int processChunkCandidates(List<ChunkCandidate> candidates, int count, 
                                            ChunkState targetState, String operation) {
        int processed = 0;
        
        for (ChunkCandidate candidate : candidates) {
            if (processed >= count) break;
            
            try {
                if (ChunkDataStore.transitionChunkState(candidate.dimensionId(), 
                                                     candidate.pos(), targetState, candidate.level())) {
                    processed++;
                }
            } catch (Exception e) {
                Recyclingservice.LOGGER.debug("Failed to {} chunk ({}, {})", 
                    operation, candidate.pos().x, candidate.pos().z, e);
            }
        }
        
        return processed;
    }
    
    private record ChunkCandidate(ResourceLocation dimensionId, ServerLevel level, 
                                ChunkPos pos, ChunkInfo info) {
    }
}