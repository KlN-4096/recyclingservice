package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * 区块服务 - 整合所有区块处理逻辑
 * 
 * 包含原来的：
 * - ItemBasedFreezer: 基于物品数量的冻结
 * - PerformanceBasedController: 基于性能的控制
 * - ChunkTakeoverHandler: 启动时区块接管
 */
public class ChunkService {

    // ================== 启动接管功能 (原ChunkTakeoverHandler) ==================
    
    /**
     * 服务器启动时接管区块
     */
    public static void handleStartupTakeover(MinecraftServer server) {
        if (!Config.TECHNICAL.enableStartupChunkCleanup.get()) {
            return;
        }
        
        try {
            int managedCount = 0;
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
                
                managedCount += takeoverDimensionChunks(dimension, level, distanceManager);
            }
            
            if (managedCount > 0) {
                Recyclingservice.LOGGER.info("Startup takeover complete: managed {} chunks", managedCount);
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to perform startup chunk takeover", e);
        }
    }

    private static int takeoverDimensionChunks(ResourceLocation dimension, ServerLevel level,
                                               DistanceManager distanceManager) {
        int managedCount = 0;

        try {
            // 直接使用 DistanceManager 的 tickets 字段
            var tickets = distanceManager.tickets;

            // 使用 Stream API 简化逻辑，避免中间集合
            var chunksToManage = tickets.long2ObjectEntrySet()
                    .stream()
                    .filter(entry -> entry.getValue().stream()
                            .anyMatch(ticket -> !ChunkCache.WHITELIST_TICKET_TYPES.contains(ticket.getType())))
                    .mapToLong(Long2ObjectMap.Entry::getLongKey)
                    .toArray();

            // 批量处理区块状态转换
            for (long encodedPos : chunksToManage) {
                ChunkPos chunkPos = new ChunkPos(encodedPos);
                if (ChunkCache.transitionChunkState(dimension, chunkPos, ChunkState.MANAGED, level)) {
                    managedCount++;
                }
            }

        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to takeover chunks for dimension {}", dimension, e);
        }

        return managedCount;
    }

    // ================== 物品冻结功能 (原ItemBasedFreezer) ==================

    /**
     * 物品监控 - 简化版本，直接处理EntityCache统计的超载区块
     */
    public static void performItemMonitoring(MinecraftServer server) {
        if (!Config.TECHNICAL.enableItemBasedFreezing.get()) {
            return;
        }
        
        try {
            int totalFrozenCount = 0;
            int unfrozenCount = 0;
            
            // 处理所有维度的超载区块
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                
                // 获取并处理超载区块
                List<ChunkPos> overloadedChunks = CleanupManager.getOverloadedChunks(dimension);
                for (ChunkPos chunkPos : overloadedChunks) {
                    processOverloadedChunk(dimension, chunkPos, level);
                    totalFrozenCount++;
                }

                // 检查已冻结的区块是否应该解冻
                unfrozenCount += unfreezeExpiredChunks(server);
            }
            
            if (totalFrozenCount > 0 || unfrozenCount > 0) {
                Recyclingservice.LOGGER.info("Item monitoring completed: {} frozen, {} unfrozen", 
                    totalFrozenCount, unfrozenCount);
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform item monitoring", e);
        }
    }
    
    private static void processOverloadedChunk(ResourceLocation dimension, ChunkPos chunkPos, ServerLevel level) {
        ChunkInfo existingInfo = ChunkCache.getChunk(dimension, chunkPos);
        
        if (existingInfo != null && existingInfo.state() == ChunkState.MANAGED) {
            // 已管理的区块，转为物品冻结状态
            ChunkCache.transitionChunkState(dimension, chunkPos, ChunkState.ITEM_FROZEN, level);
            Recyclingservice.LOGGER.debug("Frozen managed chunk ({}, {}) due to item overload", 
                chunkPos.x, chunkPos.z);
        } else {
            // 未管理的区块，先创建基础信息再接管管理
            if (existingInfo == null) {
                // 创建基础区块信息
                ChunkCache.updateChunk(dimension, new ChunkInfo(chunkPos, ChunkState.UNMANAGED, 0));
            }
            
            // 接管管理
            if (ChunkCache.transitionChunkState(dimension, chunkPos, ChunkState.MANAGED, level)) {
                // 接管成功，再转为冻结状态
                if (ChunkCache.transitionChunkState(dimension, chunkPos, ChunkState.ITEM_FROZEN, level)) {
                    Recyclingservice.LOGGER.debug("Taken over and frozen unmanaged chunk ({}, {}) due to item overload", 
                        chunkPos.x, chunkPos.z);
                }
            } else {
                // 接管失败，尝试直接冻结其tickets作为后备方案
                int frozenTickets = ChunkCache.freezeChunkTickets(chunkPos, level);
                if (frozenTickets > 0) {
                    Recyclingservice.LOGGER.debug("Frozen unmanaged chunk ({}, {}) with {} tickets (fallback)", 
                        chunkPos.x, chunkPos.z, frozenTickets);
                }
            }
        }
    }

    private static int unfreezeExpiredChunks(MinecraftServer server) {
        int unfrozenCount = 0;
        
        try {
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                
                // 获取所有物品冻结状态的区块
                List<ChunkPos> frozenChunks = ChunkCache.getChunksByState(dimension, ChunkState.ITEM_FROZEN);
                
                for (ChunkPos chunkPos : frozenChunks) {
                    ChunkInfo chunkInfo = ChunkCache.getChunk(dimension, chunkPos);
                    
                    // 检查是否到期
                    if (chunkInfo != null && chunkInfo.shouldUnfreeze()) {
                        // 解冻：转回MANAGED状态
                        if (ChunkCache.transitionChunkState(dimension, chunkPos, ChunkState.MANAGED, level)) {
                            unfrozenCount++;
                            Recyclingservice.LOGGER.debug("Unfrozen expired chunk ({}, {}) after {} hours", 
                                chunkPos.x, chunkPos.z, 
                                (System.currentTimeMillis() - (chunkInfo.unfreezeTime() - Config.TECHNICAL.itemFreezeHours.get() * 3600_000L)) / 3600_000L);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to unfreeze expired chunks", e);
        }
        
        return unfrozenCount;
    }
    // ================== 性能控制功能 (原PerformanceBasedController) ==================
    
    /**
     * 基于性能调整区块
     */
    public static void adjustChunksBasedOnPerformance(MinecraftServer server) {
        if (!Config.TECHNICAL.enableDynamicChunkManagement.get()) {
            return;
        }
        
        double mspt = PerformanceMonitor.getAverageTickTime(server);
        
        if (mspt > Config.TECHNICAL.msptThresholdSuspend.get()) {
            adjustChunksByPerformance(server, ChunkState.MANAGED, ChunkState.PERFORMANCE_FROZEN, "Frozen");
        } else if (mspt < Config.TECHNICAL.msptThresholdRestore.get()) {
            adjustChunksByPerformance(server, ChunkState.PERFORMANCE_FROZEN, ChunkState.MANAGED, "Unfrozen");
        }
    }
    
    private static void adjustChunksByPerformance(MinecraftServer server, 
                                                    ChunkState fromState, 
                                                    ChunkState toState, 
                                                    String action) {
        try {
            int targetCount = Config.TECHNICAL.chunkOperationCount.get();
            int processedCount = 0;

            for (ServerLevel level : server.getAllLevels()) {
                if (processedCount >= targetCount) break;

                ResourceLocation dimension = level.dimension().location();
                List<ChunkPos> targetChunks = ChunkCache.getChunksByState(dimension, fromState);

                for (ChunkPos pos : targetChunks) {
                    if (processedCount >= targetCount) break;

                    if (ChunkCache.transitionChunkState(dimension, pos, toState, level)) {
                        processedCount++;
                    }
                }
            }

            if (processedCount > 0) {
                Recyclingservice.LOGGER.info("Performance: {} {} chunks",action, processedCount);
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to {} chunks for performance", action.toLowerCase(), e);
        }
    }

}