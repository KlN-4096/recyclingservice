package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.content.chunk.function.TicketManager;
import com.klnon.recyclingservice.content.chunk.service.ChunkItemMonitoring;
import com.klnon.recyclingservice.content.chunk.service.ChunkPerformanceAdjustment;
import com.klnon.recyclingservice.content.chunk.service.ChunkTakeover;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * 区块管理器 - chunk包的统一入口
 * 纯Facade模式，所有逻辑委托给ChunkService
 */
public class ChunkManager {
    
    /**
     * 执行启动区块接管
     */
    public static void performTakeover(MinecraftServer server) {
        ChunkTakeover.handleTakeover(server);
    }

    /**
     * 执行物品监控检查
     */
    public static void performItemMonitoring(MinecraftServer server) {
        ChunkItemMonitoring.performItemMonitoring(server);
    }
    
    /**
     * 执行性能调整
     */
    public static void performPerformanceAdjustment(MinecraftServer server) {
        ChunkPerformanceAdjustment.adjustChunksBasedOnPerformance(server);
    }

    /**
     * 获取区块状态名称
     */
    public static String getStateName(ChunkDataCache.ChunkInfo chunkInfo) {
        return chunkInfo.getStateName();
    }

    /**
     * 根据状态名称获取状态值
     */
    public static byte getStateByName(String name) {
        return ChunkDataCache.getStateByName(name);
    }

    /**
     * 获取指定区块的所有tickets
     */
    public static List<Ticket<?>> getChunkTickets(ChunkPos chunkPos, ServerLevel level) {
        return TicketManager.getChunkTickets(chunkPos, level);
    }

}