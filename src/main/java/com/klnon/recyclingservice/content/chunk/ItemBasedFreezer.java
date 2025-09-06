package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;

/**
 * 基于物品的区块冻结器
 */
public class ItemBasedFreezer {
    
    /**
     * 处理清理时的超载区块(随物品清理一块冻结)
     */
    public static void handleOverloadedChunks(ResourceLocation dimensionId, ServerLevel level) {
        if (!Config.TECHNICAL.enableItemBasedFreezing.get()) {
            return;
        }
        
        try {
            List<ChunkPos> overloadedChunks = CleanupManager.getOverloadedChunks(dimensionId);
            
            for (ChunkPos chunkPos : overloadedChunks) {
                processOverloadedChunk(dimensionId, chunkPos, level);
                
                // 发送警告消息
                if (Config.TECHNICAL.enableChunkItemWarning.get()) {
                    sendChunkWarning(dimensionId, chunkPos, level);
                }
            }
            
            if (!overloadedChunks.isEmpty()) {
                Recyclingservice.LOGGER.info(
                    "Item-based freezing completed for {}: {} overloaded chunks processed",
                    dimensionId, overloadedChunks.size());
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to handle overloaded chunks for {}", dimensionId, e);
        }
    }
    
    /**
     * 定期物品检查(检查管理区块的物品数量)
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
                Map<ChunkPos, ChunkInfo> managedChunks = getChunksInState(dimension);
                for (Map.Entry<ChunkPos, ChunkInfo> entry : managedChunks.entrySet()) {
                    ChunkPos pos = entry.getKey();
                    ChunkInfo info = entry.getValue();
                    
                    int itemCount = getChunkItemCount(dimension, pos);
                    if (shouldFreezeForItems(itemCount)) {
                        if (ChunkDataStore.transitionChunkState(dimension, pos, ChunkState.ITEM_FROZEN, level)) {
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
        ChunkInfo existingInfo = ChunkDataStore.getChunk(dimension, chunkPos);
        
        if (existingInfo != null && existingInfo.state() == ChunkState.MANAGED) {
            // 已管理的区块，转为物品冻结状态
            ChunkDataStore.transitionChunkState(dimension, chunkPos, ChunkState.ITEM_FROZEN, level);
            Recyclingservice.LOGGER.debug("Frozen managed chunk ({}, {}) due to item overload", 
                chunkPos.x, chunkPos.z);
        } else {
            // 未管理的区块，尝试直接冻结其tickets
            int frozenTickets = freezeChunkTickets(chunkPos, level);
            if (frozenTickets > 0) {
                Recyclingservice.LOGGER.debug("Frozen unmanaged chunk ({}, {}) with {} tickets", 
                    chunkPos.x, chunkPos.z, frozenTickets);
                
                // 记录为未管理状态
                ChunkDataStore.updateChunk(dimension, 
                    new ChunkInfo(chunkPos, ChunkState.UNMANAGED, 0));
            }
        }
    }
    
    private static int freezeChunkTickets(ChunkPos chunkPos, ServerLevel level) {
        return ChunkDataStore.freezeChunkTickets(chunkPos, level);
    }
    
    private static Map<ChunkPos, ChunkInfo> getChunksInState(ResourceLocation dimension) {
        Map<ChunkPos, ChunkInfo> allChunks = ChunkDataStore.getDimensionChunks(dimension);
        return allChunks.entrySet().stream()
            .filter(entry -> entry.getValue().state() == ChunkState.MANAGED)
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey, 
                Map.Entry::getValue
            ));
    }
    
    private static int getChunkItemCount(ResourceLocation dimension, ChunkPos pos) {
        return CleanupManager.getEntityCountByChunk(dimension).getOrDefault(pos, 0);
    }
    
    private static boolean shouldFreezeForItems(int itemCount) {
        try {
            return itemCount >= Config.TECHNICAL.tooManyItemsWarning.get();
        } catch (Exception e) {
            return itemCount >= 50; // 默认阈值
        }
    }
    
    private static void sendChunkWarning(ResourceLocation dimension, ChunkPos chunkPos, ServerLevel level) {
        try {
            int itemCount = getChunkItemCount(dimension, chunkPos);
            int worldX = chunkPos.x * 16 + 8;
            int worldZ = chunkPos.z * 16 + 8;
            
            // 获取ticket level
            int ticketLevel = 33;
            try {
                var holder = level.getChunkSource().chunkMap.visibleChunkMap.get(
                    ChunkPos.asLong(chunkPos.x, chunkPos.z));
                if (holder != null) {
                    ticketLevel = holder.getTicketLevel();
                }
            } catch (Exception ignored) {
            }
            
            var warningMessage = MessageHelper.getItemWarningMessage(itemCount, worldX, worldZ, ticketLevel);
            MessageHelper.sendChatMessage(level.getServer(), warningMessage);
        } catch (Exception ignored) {
        }
    }
    
    /**
     * 检查并解冻到期的区块
     */
    private static int unfreezeExpiredChunks(MinecraftServer server) {
        int unfrozen = 0;
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimension = level.dimension().location();
            Map<ChunkPos, ChunkInfo> chunks = ChunkDataStore.getDimensionChunks(dimension);
            
            for (ChunkInfo info : chunks.values()) {
                if (info.shouldUnfreeze()) {
                    if (ChunkDataStore.transitionChunkState(dimension, info.chunkPos(), 
                                                          ChunkState.MANAGED, level)) {
                        unfrozen++;
                    }
                }
            }
        }
        return unfrozen;
    }
}