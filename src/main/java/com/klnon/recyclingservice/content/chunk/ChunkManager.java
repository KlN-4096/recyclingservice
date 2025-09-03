package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.content.chunk.freezer.ChunkFreezer;
import com.klnon.recyclingservice.content.chunk.freezer.DynamicChunkManager;
import com.klnon.recyclingservice.content.chunk.freezer.StartupChunkCleaner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * 区块管理器 - chunk包的统一入口
 * 对外提供区块冻结、性能管理等核心功能
 */
public class ChunkManager {
    
    /**
     * 执行区块冻结检查
     * 检测超载区块并冻结影响它们的加载器
     * 
     * @param dimensionId 维度ID
     * @param level 服务器维度实例
     */
    public static void performFreezingCheck(ResourceLocation dimensionId, ServerLevel level) {
        ChunkFreezer.performChunkFreezingCheck(dimensionId, level);
    }
    
    /**
     * 执行服务器启动时的区块清理
     * 根据方块实体数量移除不必要的强加载
     * 
     * @param server 服务器实例
     */
    public static void performStartupCleanup(MinecraftServer server) {
        StartupChunkCleaner.performStartupChunkCleanup(server);
    }
    
    /**
     * 执行动态区块管理
     * 根据服务器性能动态管理区块加载
     * 
     * @param server 服务器实例
     */
    public static void performDynamicManagement(MinecraftServer server) {
        DynamicChunkManager.performDynamicChunkManagement(server);
    }
}