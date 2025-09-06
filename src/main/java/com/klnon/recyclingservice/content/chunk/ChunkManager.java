package com.klnon.recyclingservice.content.chunk;

import net.minecraft.server.MinecraftServer;

/**
 * 区块管理器 - chunk包的统一入口
 * 纯Facade模式，所有逻辑委托给ChunkService
 */
public class ChunkManager {
    
    /**
     * 执行启动区块接管
     */
    public static void performStartupTakeover(MinecraftServer server) {
        ChunkService.handleStartupTakeover(server);
    }

    
    /**
     * 执行物品监控检查
     */
    public static void performItemMonitoring(MinecraftServer server) {
        ChunkService.performItemMonitoring(server);
    }
    
    /**
     * 执行性能调整
     */
    public static void performPerformanceAdjustment(MinecraftServer server) {
        ChunkService.adjustChunksBasedOnPerformance(server);
    }
}