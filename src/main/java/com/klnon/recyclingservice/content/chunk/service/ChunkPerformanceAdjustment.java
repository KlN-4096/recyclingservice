package com.klnon.recyclingservice.content.chunk.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import com.klnon.recyclingservice.content.chunk.function.PerformanceMonitor;
import com.klnon.recyclingservice.content.chunk.function.TicketManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 物品性能管理功能类
 * 根据MSPT性能,智能选择冻结/解冻区块
 */
public class ChunkPerformanceAdjustment {

    /**
     * 基于性能调整区块
     */
    public static void adjustChunksBasedOnPerformance(MinecraftServer server) {
        double mspt = PerformanceMonitor.getAverageTickTime(server);

        if (mspt > Config.TECHNICAL.msptThresholdSuspend.get()) {
            adjustChunksByPerformance(server, ChunkDataCache.MANAGED, ChunkDataCache.PERFORMANCE_FROZEN, "Frozen");
        } else if (mspt < Config.TECHNICAL.msptThresholdRestore.get()) {
            adjustChunksByPerformance(server, ChunkDataCache.PERFORMANCE_FROZEN, ChunkDataCache.MANAGED, "Unfrozen");
        }
    }

    private static void adjustChunksByPerformance(MinecraftServer server,
                                                  byte fromState,
                                                  byte toState,
                                                  String action) {
        int targetCount = Config.TECHNICAL.chunkOperationCount.get();
        int processedCount = 0;

        for (ServerLevel level : server.getAllLevels()) {
            if (processedCount >= targetCount) break;

            ResourceLocation dimension = level.dimension().location();
            List<ChunkDataCache.ChunkInfo> allChunks = ChunkDataCache.getChunksByState(dimension, fromState);
            List<ChunkDataCache.ChunkInfo> targetChunks;

            if (fromState == ChunkDataCache.MANAGED) {
                // 冻结：直接取前N个（方块实体最多的）
                targetChunks = allChunks.stream()
                        .limit(targetCount - processedCount)
                        .toList();
            } else {
                // 解冻：直接取最后N个（方块实体最少的）
                int fromIndex = Math.max(0, allChunks.size() - (targetCount - processedCount));
                targetChunks = allChunks.subList(fromIndex, allChunks.size());
            }

            for (ChunkDataCache.ChunkInfo chunkInfo : targetChunks) {
                if (processedCount >= targetCount) break;

                boolean success = false;
                if (toState == ChunkDataCache.PERFORMANCE_FROZEN) {
                    // 性能冻结：使用扩散冻结，避免破坏机器
                    success = (TicketManager.freezeChunkWithRadius(dimension, chunkInfo.pos(), level) > 0);
                } else if (toState == ChunkDataCache.MANAGED) {
                    // 解冻
                    success = TicketManager.unfreezeChunk(dimension, chunkInfo.pos(), level);
                }

                if (success) {
                    processedCount++;
                }
            }
        }

        Recyclingservice.LOGGER.info("Performance: {} {} chunks", action, processedCount);
    }
}