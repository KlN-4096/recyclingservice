package com.klnon.recyclingservice.content.chunk.management.startup;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkDataStore;
import com.klnon.recyclingservice.content.chunk.management.storage.ChunkState;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块接管处理器
 */
public class ChunkTakeoverHandler {
    
    /**
     * 执行启动接管
     */
    public static void performTakeover(MinecraftServer server) {
        if (!Config.TECHNICAL.enableAggressiveTakeover.get()) {
            return;
        }
        
        int threshold = Config.TECHNICAL.takeoverBlockEntityThreshold.get();
        int totalProcessed = 0;
        int totalManaged = 0;
        
        Recyclingservice.LOGGER.info("Starting aggressive chunk takeover with threshold: {}", threshold);
        
        try {
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                TakeoverStats stats = takeoverDimension(level, dimension, threshold);
                
                if (stats.processedChunks() > 0) {
                    Recyclingservice.LOGGER.info(
                        "Takeover completed for {}: {} chunks processed, {} now managed",
                        dimension, stats.processedChunks(), stats.managedChunks());
                    
                    totalProcessed += stats.processedChunks();
                    totalManaged += stats.managedChunks();
                }
            }
            
            if (totalProcessed > 0) {
                Recyclingservice.LOGGER.info(
                    "Aggressive takeover completed: {} chunks processed, {} now managed",
                    totalProcessed, totalManaged);
            } else {
                Recyclingservice.LOGGER.info("No chunks needed takeover");
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to perform takeover", e);
        }
    }
    
    private static TakeoverStats takeoverDimension(ServerLevel level, ResourceLocation dimension, int threshold) {
        DistanceManager distanceManager = level.getChunkSource().distanceManager;
        Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
        
        int processed = 0;
        int managed = 0;
        
        for (var entry : tickets.long2ObjectEntrySet()) {
            long chunkKey = entry.getLongKey();
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            SortedArraySet<Ticket<?>> chunkTickets = entry.getValue();
            
            if (chunkTickets == null || chunkTickets.isEmpty()) {
                continue;
            }
            
            // 检查是否有非白名单tickets
            List<Ticket<?>> toRemove = new ArrayList<>();
            for (Ticket<?> ticket : chunkTickets) {
                if (!ChunkDataStore.WHITELIST_TICKET_TYPES.contains(ticket.getType())) {
                    toRemove.add(ticket);
                }
            }
            
            if (toRemove.isEmpty()) {
                continue;
            }
            
            try {
                // 1. 先检查区块内容(趁着区块还被其他ticket加载)
                var chunkAccess = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
                if (chunkAccess != null) {
                    int blockEntityCount = chunkAccess.getBlockEntities().size();
                    
                    if (blockEntityCount >= threshold) {
                        // 2. 值得接管：先添加我们的ticket保护区块
                        if (ChunkDataStore.addManagedChunk(chunkPos, level, blockEntityCount)) {
                            managed++;
                            Recyclingservice.LOGGER.debug("Took over chunk ({}, {}) with {} block entities", 
                                chunkPos.x, chunkPos.z, blockEntityCount);
                        }
                    } else {
                        // 不值得管理，标记为未管理状态
                        ChunkDataStore.updateChunk(dimension, 
                            new com.klnon.recyclingservice.content.chunk.management.storage.ChunkInfo(
                                chunkPos, ChunkState.UNMANAGED, blockEntityCount));
                    }
                }
                
                // 3. 最后移除其他模组的tickets(此时重要区块已被我们的ticket保护)
                for (Ticket<?> ticket : toRemove) {
                    distanceManager.removeTicket(chunkKey, ticket);
                }
                processed++;
                
            } catch (Exception e) {
                Recyclingservice.LOGGER.debug("Failed to process chunk ({}, {})", 
                    chunkPos.x, chunkPos.z, e);
            }
        }
        
        return new TakeoverStats(processed, managed);
    }
    
    private record TakeoverStats(int processedChunks, int managedChunks) {
    }
}