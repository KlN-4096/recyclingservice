package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;

import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.*;

/**
 * 区块服务 - 整合所有区块处理逻辑
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
    public static void handleTakeover(MinecraftServer server) {
        try {
            int managedCount = 0;
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
                
                managedCount += takeoverDimensionChunks(dimension, level, distanceManager);
            }

            Recyclingservice.LOGGER.info("Takeover complete: managed {} chunks", managedCount);
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to perform chunk takeover", e);
        }
    }

    private static int takeoverDimensionChunks(ResourceLocation dimension, ServerLevel level,
                                               DistanceManager distanceManager) {
        try {
            List<ChunkCache.ChunkInfo> newChunks = new ArrayList<>();
            
            // 扫描所有ticket区块
            distanceManager.tickets.long2ObjectEntrySet().forEach(entry -> {
                var ticketSet = entry.getValue();
                ChunkPos chunkPos = new ChunkPos(entry.getLongKey());
                
                // 检查是否有非白名单ticket且未被我们管理
                boolean hasNonWhitelist = ticketSet.stream()
                    .anyMatch(ticket -> !ChunkCache.WHITELIST_TICKET_TYPES.contains(ticket.getType()));
                boolean alreadyManaged = ChunkCache.isChunkManaged(dimension, chunkPos);
                
                if (hasNonWhitelist && !alreadyManaged) {
                    // 计算方块实体数量
                    int blockEntityCount = 0;
                    try {
                        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                        blockEntityCount = chunk.getBlockEntities().size();
                    } catch (Exception ignored) {}
                    // 添加管理ticket,先移除非白名单,再添加我们自己的ticket
                    ChunkCache.freezeChunkTickets(chunkPos, level);
                    LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                    //先判断区块方块实体数量是否满足配置条件,再添加我们的ticket
                    if (chunk.getBlockEntities().size()<Config.TECHNICAL.chunkEntityThreshold.get()) {
                        //方块实体数量小于配置项目时纳入管理,但不添加我们的ticket
                        newChunks.add(new ChunkCache.ChunkInfo(dimension, chunkPos, blockEntityCount, ChunkCache.ChunkInfo.UNIMPORTANT, 0));
                    }
                    else if(ChunkCache.addManagementTicket(chunkPos, level)){
                        newChunks.add(new ChunkCache.ChunkInfo(dimension, chunkPos, blockEntityCount, ChunkCache.ChunkInfo.MANAGED, 0));
                    }
                }
            });
            
            // 按方块实体数量排序（从大到小）
            newChunks.sort((a, b) -> Integer.compare(b.blockEntityCount(), a.blockEntityCount()));
            
            // 批量设置到缓存
            ChunkCache.setManagedChunks(dimension, newChunks);

            
            return newChunks.size();
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to takeover chunks for dimension {}", dimension, e);
            return 0;
        }
    }

    // ================== 性能控制功能 ==================

    /**
     * 基于性能调整区块
     */
    public static void adjustChunksBasedOnPerformance(MinecraftServer server) {
        double mspt = PerformanceMonitor.getAverageTickTime(server);

        if (mspt > Config.TECHNICAL.msptThresholdSuspend.get()) {
            adjustChunksByPerformance(server, ChunkCache.ChunkInfo.MANAGED, ChunkCache.ChunkInfo.PERFORMANCE_FROZEN, "Frozen");
        } else if (mspt < Config.TECHNICAL.msptThresholdRestore.get()) {
            adjustChunksByPerformance(server, ChunkCache.ChunkInfo.PERFORMANCE_FROZEN, ChunkCache.ChunkInfo.MANAGED, "Unfrozen");
        }
    }

    private static void adjustChunksByPerformance(MinecraftServer server,
                                                  int fromState,
                                                  int toState,
                                                  String action) {
        try {
            int targetCount = Config.TECHNICAL.chunkOperationCount.get();
            int processedCount = 0;

            for (ServerLevel level : server.getAllLevels()) {
                if (processedCount >= targetCount) break;

                ResourceLocation dimension = level.dimension().location();
                List<ChunkCache.ChunkInfo> targetChunks;
                
                if (fromState == ChunkCache.ChunkInfo.MANAGED && toState == ChunkCache.ChunkInfo.PERFORMANCE_FROZEN) {
                    // 冻结：从实体多的开始（已排序，直接取前N个）
                    targetChunks = ChunkCache.getChunksByState(dimension, fromState)
                                           .stream()
                                           .limit(targetCount - processedCount)
                                           .toList();
                } else {
                    // 解冻：从实体少的开始（需要重新排序）
                    targetChunks = ChunkCache.getChunksByState(dimension, fromState)
                                           .stream()
                                           .sorted((a, b) -> Integer.compare(a.blockEntityCount(), b.blockEntityCount()))
                                           .limit(targetCount - processedCount)
                                           .toList();
                }

                for (ChunkCache.ChunkInfo chunkInfo : targetChunks) {
                    if (processedCount >= targetCount) break;

                    boolean success = false;
                    if (toState == ChunkCache.ChunkInfo.PERFORMANCE_FROZEN) {
                        // 性能冻结：使用通用freezeChunk方法，unfreezeTime=0
                        success = ChunkCache.freezeChunk(dimension, chunkInfo.pos(), level, toState, 0);
                    } else if (toState == ChunkCache.ChunkInfo.MANAGED) {
                        // 解冻：使用unfreezeChunk方法
                        success = ChunkCache.unfreezeChunk(dimension, chunkInfo.pos(), level);
                    }

                    if (success) {
                        processedCount++;
                    }
                }
            }

            Recyclingservice.LOGGER.info("Performance: {} {} chunks", action, processedCount);

        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to {} chunks for performance", action.toLowerCase(), e);
        }
    }


    // ================== 物品超载冻结功能 ==================

    /**
     * 物品监控 ，直接处理EntityCache统计的超载区块
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
                
                // 获取超载区块
                List<ChunkPos> overloadedChunks = CleanupManager.getOverloadedChunks(dimension);
                
                // 发送警告消息（如果启用）
                if (Config.TECHNICAL.enableChunkItemWarning.get()) {
                    sendItemWarningMessages(server, dimension, overloadedChunks);
                }
                
                // 冻结超载区块（包括扩散冻结）
                for (ChunkPos chunkPos : overloadedChunks) {
                    totalFrozenCount += freezeChunkWithRadius(dimension, chunkPos, level);
                }

                // 检查已冻结的区块是否应该解冻
                unfrozenCount += unfreezeExpiredChunks(dimension, level);
            }

            Recyclingservice.LOGGER.info("Item monitoring completed: {} frozen, {} unfrozen",
                totalFrozenCount, unfrozenCount);
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform item monitoring", e);
        }
    }
    
    private static int unfreezeExpiredChunks(ResourceLocation dimension, ServerLevel level) {
        int unfrozenCount = 0;
        
        try {
            // 获取所有物品冻结的区块
            List<ChunkPos> frozenChunks = ChunkCache.getItemFrozenChunks(dimension);
            
            for (ChunkPos chunkPos : frozenChunks) {
                // 检查是否到期
                if (ChunkCache.shouldUnfreezeItemFrozenChunk(dimension, chunkPos)) {
                    // 解冻：恢复管理
                    if (ChunkCache.unfreezeChunk(dimension, chunkPos, level)) {
                        unfrozenCount++;
                        Recyclingservice.LOGGER.debug("Unfrozen expired chunk ({}, {})", 
                            chunkPos.x, chunkPos.z);
                    }
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to unfreeze expired chunks for {}", dimension, e);
        }
        
        return unfrozenCount;
    }
    
    /**
     * 发送物品超载警告消息给所有玩家
     */
    private static void sendItemWarningMessages(MinecraftServer server, ResourceLocation dimension, List<ChunkPos> overloadedChunks) {
        if (overloadedChunks.isEmpty()) return;
        
        try {
            // 获取该维度所有区块的实体数量统计
            Map<ChunkPos, Integer> entityCountMap = CleanupManager.getEntityCountByChunk(dimension);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            DistanceManager distanceManager = level != null ? level.getChunkSource().chunkMap.getDistanceManager() : null;
            
            for (ChunkPos chunkPos : overloadedChunks) {
                int itemCount = entityCountMap.getOrDefault(chunkPos, 0);
                
                if (itemCount > 0) {
                    // 计算世界坐标（区块中心）
                    int worldX = chunkPos.x * 16 + 8;
                    int worldZ = chunkPos.z * 16 + 8;
                    
                    // 直接获取ticket等级
                    int ticketLevel = 33; // 默认未加载
                    if (distanceManager != null) {
                        var chunkTickets = distanceManager.tickets.get(chunkPos.toLong());
                        if (chunkTickets != null && !chunkTickets.isEmpty()) {
                            ticketLevel = chunkTickets.stream()
                                .mapToInt(Ticket::getTicketLevel)
                                .min()
                                .orElse(33);
                        }
                    }
                    
                    Component warningMessage = MessageHelper.getItemWarningMessage(itemCount, worldX, worldZ, ticketLevel);
                    
                    // 发送给所有玩家（在聊天栏显示）
                    MessageHelper.sendChatMessage(server, warningMessage);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to send item warning messages for {}", dimension, e);
        }
    }
    
    
    /**
     * 扩散冻结：冻结超载区块及其周围半径内的非白名单强加载区块
     */
    private static int freezeChunkWithRadius(ResourceLocation dimension, ChunkPos centerChunk, ServerLevel level) {
        int frozenCount = 0;
        int radius = Config.TECHNICAL.chunkFreezingSearchRadius.get();
        
        try {
            // 首先冻结中心区块
            if (ChunkCache.freezeChunk(dimension, centerChunk,level,ChunkCache.ChunkInfo.ITEM_FROZEN,Config.TECHNICAL.itemFreezeMinutes.get())) {
                frozenCount++;
                Recyclingservice.LOGGER.debug("Frozen overloaded chunk ({}, {}) due to items", 
                    centerChunk.x, centerChunk.z);
            }
            
            // 获取DistanceManager来检查周围区块的ticket状态
            DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
            var tickets = distanceManager.tickets;
            
            // 遍历半径内的所有区块
            for (int x = centerChunk.x - radius; x <= centerChunk.x + radius; x++) {
                for (int z = centerChunk.z - radius; z <= centerChunk.z + radius; z++) {
                    // 跳过中心区块（已经处理过）
                    if (x == centerChunk.x && z == centerChunk.z) continue;
                    
                    ChunkPos chunkPos = new ChunkPos(x, z);
                    long chunkKey = chunkPos.toLong();
                    
                    // 检查该区块是否有非白名单的ticket（强加载区块）
                    var chunkTickets = tickets.get(chunkKey);
                    if (chunkTickets != null && !chunkTickets.isEmpty()) {
                        boolean hasNonWhitelistTicket = chunkTickets.stream()
                            .anyMatch(ticket -> !ChunkCache.WHITELIST_TICKET_TYPES.contains(ticket.getType()));
                        
                        if (hasNonWhitelistTicket) {
                            // 冻结该区块
                            if (ChunkCache.freezeChunk(dimension,chunkPos,level,ChunkCache.ChunkInfo.ITEM_FROZEN,Config.TECHNICAL.itemFreezeMinutes.get())) {
                                frozenCount++;
                                Recyclingservice.LOGGER.debug("Frozen adjacent chunk ({}, {}) within radius {} of overloaded chunk", 
                                    chunkPos.x, chunkPos.z, radius);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to freeze chunk with radius for ({}, {})", 
                centerChunk.x, centerChunk.z, e);
        }
        
        return frozenCount;
    }


}