package com.klnon.recyclingservice.content.chunk.management;

import com.klnon.recyclingservice.content.chunk.management.monitoring.ItemBasedFreezer;
import com.klnon.recyclingservice.content.chunk.management.performance.PerformanceBasedController;
import com.klnon.recyclingservice.content.chunk.management.startup.ChunkTakeoverHandler;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkDataStore;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkInfo;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.Set;

/**
 * 区块生命周期管理器 - 统一协调所有区块操作
 */
public class ChunkLifecycleManager {
    
    /**
     * 服务器启动时的区块接管
     */
    public static void performStartupTakeover(MinecraftServer server) {
        ChunkTakeoverHandler
            .performTakeover(server);
    }
    
    /**
     * 清理时的超载区块处理
     */
    public static void performOverloadHandling(ResourceLocation dimensionId, ServerLevel level) {
        ItemBasedFreezer
            .handleOverloadedChunks(dimensionId, level);
    }
    
    /**
     * 物品监控检查
     */
    public static void performItemMonitoring(MinecraftServer server) {
        ItemBasedFreezer
            .performItemCheck(server);
    }
    
    /**
     * 性能调整检查
     */
    public static void performPerformanceAdjustment(MinecraftServer server) {
        PerformanceBasedController
            .performAdjustment(server);
    }
    
    /**
     * 获取区块状态
     */
    public static ChunkState getChunkState(ResourceLocation dimension, ChunkPos pos) {
        ChunkInfo info = ChunkDataStore.getChunk(dimension, pos);
        return info != null ? info.state() : ChunkState.UNMANAGED;
    }
    
    /**
     * 获取指定状态的区块数量
     */
    public static int getChunkCountByState(ChunkState state) {
        return ChunkDataStore.getChunksByState(state).values().stream()
                .mapToInt(Set::size)
                .sum();
    }
    
    /**
     * 获取维度的管理区块统计
     */
    public static Map<ChunkPos, ChunkInfo> getDimensionManagedChunks(ResourceLocation dimension) {
        return ChunkDataStore.getDimensionChunks(dimension);
    }
    
    /**
     * 检查并解冻到期的区块
     */
    public static int unfreezeExpiredChunks(MinecraftServer server) {
        int unfrzen = 0;
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimension = level.dimension().location();
            Map<ChunkPos, ChunkInfo> chunks = ChunkDataStore.getDimensionChunks(dimension);
            
            for (ChunkInfo info : chunks.values()) {
                if (info.shouldUnfreeze()) {
                    if (ChunkDataStore.transitionChunkState(dimension, info.chunkPos(), 
                                                          ChunkState.MANAGED, level)) {
                        unfrzen++;
                    }
                }
            }
        }
        return unfrzen;
    }
}