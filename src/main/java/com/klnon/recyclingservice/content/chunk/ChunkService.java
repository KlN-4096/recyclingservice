package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
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
     * 处理超载区块（基于物品数量）
     */
    public static void handleOverloadedChunks(ResourceLocation dimensionId, ServerLevel level) {
        if (!Config.TECHNICAL.enableItemBasedFreezing.get()) {
            return;
        }
        
        try {
            List<ChunkPos> overloadedChunks = CleanupManager.getOverloadedChunks(dimensionId);
            if (overloadedChunks.isEmpty()) {
                return;
            }
            
            int frozenCount = 0;
            for (ChunkPos chunkPos : overloadedChunks) {
                processOverloadedChunk(dimensionId, chunkPos, level);
                frozenCount++;
            }
            
            if (frozenCount > 0) {
                Recyclingservice.LOGGER.info("Frozen {} overloaded chunks in {}", 
                    frozenCount, dimensionId);
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to handle overloaded chunks for {}", dimensionId, e);
        }
    }
    
    /**
     * 定期物品检查
     */
    public static void performItemCheck(MinecraftServer server) {
        if (!Config.TECHNICAL.enableItemBasedFreezing.get()) {
            return;
        }
        
        try {
            int frozenCount = 0;
            int unfrozenCount = 0;
            
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                
                // 检查管理状态的区块是否需要冻结
                List<ChunkPos> managedChunks = ChunkCache.getChunksByState(dimension, ChunkState.MANAGED);
                for (ChunkPos pos : managedChunks) {
                    int itemCount = getChunkItemCount(dimension, pos);
                    if (shouldFreezeForItems(itemCount)) {
                        if (ChunkCache.transitionChunkState(dimension, pos, ChunkState.ITEM_FROZEN, level)) {
                            frozenCount++;
                            Recyclingservice.LOGGER.debug("Frozen chunk ({}, {}) for {} items", 
                                pos.x, pos.z, itemCount);
                        }
                    }
                }
                
                // 检查已冻结的区块是否应该解冻
                unfrozenCount += unfreezeExpiredChunks(server);
            }
            
            if (frozenCount > 0 || unfrozenCount > 0) {
                Recyclingservice.LOGGER.info("Item monitoring completed: {} frozen, {} unfrozen", 
                    frozenCount, unfrozenCount);
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform item check", e);
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
            // 未管理的区块，尝试直接冻结其tickets
            int frozenTickets = ChunkCache.freezeChunkTickets(chunkPos, level);
            if (frozenTickets > 0) {
                Recyclingservice.LOGGER.debug("Frozen unmanaged chunk ({}, {}) with {} tickets", 
                    chunkPos.x, chunkPos.z, frozenTickets);
                
                // 记录为未管理状态
                ChunkCache.updateChunk(dimension, 
                    new ChunkInfo(chunkPos, ChunkState.UNMANAGED, 0));
            }
        }
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
    // ================== 辅助方法 ==================
    
    private static int getChunkItemCount(ResourceLocation dimension, ChunkPos pos) {
        Map<ChunkPos, Integer> entityCounts = CleanupManager.getEntityCountByChunk(dimension);
        return entityCounts.getOrDefault(pos, 0);
    }
    
    private static boolean shouldFreezeForItems(int itemCount) {
        return itemCount > Config.TECHNICAL.tooManyItemsWarning.get();
    }
    
    private static int unfreezeExpiredChunks(MinecraftServer server) {
        // 简化版本，可以根据需要扩展
        return 0;
    }
}