package com.klnon.recyclingservice.content.chunk;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 区块管理器 - chunk包的统一入口
 */
public class ChunkManager {
    
    /**
     * 执行启动区块接管
     */
    public static void performStartupTakeover(MinecraftServer server) {
        ChunkTakeoverHandler.performTakeover(server);
    }
    
    /**
     * 执行清理时的超载区块处理
     */
    public static void performOverloadHandling(ResourceLocation dimensionId, ServerLevel level) {
        ItemBasedFreezer.handleOverloadedChunks(dimensionId, level);
    }
    
    /**
     * 执行物品监控检查
     */
    public static void performItemMonitoring(MinecraftServer server) {
        ItemBasedFreezer.performItemCheck(server);
    }
    
    /**
     * 执行性能调整
     */
    public static void performPerformanceAdjustment(MinecraftServer server) {
        PerformanceBasedController.performAdjustment(server);
    }
}