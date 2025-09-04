package com.klnon.recyclingservice.content.chunk.freezer;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

/**
 * 区块启动清理器
 * 负责服务器启动时的区块清理逻辑
 */
public class ChunkStartupCleaner {
    
    /**
     * 执行服务器启动时的区块清理
     */
    public static void performStartupChunkCleanup(MinecraftServer server) {
        if (!Config.TECHNICAL.enableStartupChunkCleanup.get()) {
            return;
        }
        
        int threshold = Config.TECHNICAL.startupChunkEntityThreshold.get();
        int totalFrozenChunks = 0;
        int totalFrozenTickets = 0;
        
        Recyclingservice.LOGGER.info("Starting startup chunk cleanup with block entity threshold: {}", threshold);
        
        try {
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimensionId = level.dimension().location();
                
                try {
                    var stats = cleanupDimension(level, dimensionId, threshold);
                    
                    if (stats.processedChunks() > 0) {
                        Recyclingservice.LOGGER.info(
                            "Startup cleanup completed for dimension {}: {} chunks processed, {} tickets removed, {} chunks now managed",
                            dimensionId, stats.processedChunks(), stats.frozenTickets(), stats.managedChunks());
                        
                        totalFrozenChunks += stats.processedChunks();
                        totalFrozenTickets += stats.frozenTickets();
                    }
                    
                } catch (Exception e) {
                    Recyclingservice.LOGGER.debug(
                        "Failed to process dimension {} during startup cleanup", dimensionId, e);
                }
            }
            
            logFinalResults(totalFrozenChunks, totalFrozenTickets);
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to perform startup chunk cleanup", e);
        }
    }
    
    private static DimensionCleanupStats cleanupDimension(ServerLevel level, ResourceLocation dimensionId, int threshold) {
        DistanceManager distanceManager = level.getChunkSource().distanceManager;
        Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
        
        int dimensionProcessedChunks = 0;
        int dimensionFrozenTickets = 0;
        int dimensionManagedChunks = 0;
        
        for (var entry : tickets.long2ObjectEntrySet()) {
            long chunkKey = entry.getLongKey();
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            SortedArraySet<Ticket<?>> chunkTickets = entry.getValue();
            
            if (chunkTickets == null || chunkTickets.isEmpty()) {
                continue;
            }
            
            // 检查是否有非白名单tickets
            boolean hasNonWhitelistTickets = chunkTickets.stream()
                .anyMatch(ticket -> !FreezeDataStore.WHITELIST_TICKET_TYPES.contains(ticket.getType()));
            
            if (!hasNonWhitelistTickets) {
                continue;
            }
            
            try {
                var chunkAccess = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
                if (chunkAccess == null) {
                    continue;
                }
                
                int blockEntityCount = chunkAccess.getBlockEntities().size();
                
                int frozenTickets = ChunkFreezer.freezeChunk(chunkPos, level);
                if (frozenTickets > 0) {
                    dimensionProcessedChunks++;
                    dimensionFrozenTickets += frozenTickets;
                    
                    if (blockEntityCount >= threshold) {
                        FreezeDataStore.addManagedChunk(chunkPos, level, blockEntityCount);
                        dimensionManagedChunks++;
                        
                        Recyclingservice.LOGGER.debug(
                            "Startup cleanup: Replaced tickets for chunk ({}, {}) in {} with {} block entities (now managed)",
                            chunkPos.x, chunkPos.z, dimensionId, blockEntityCount);
                    } else {
                        Recyclingservice.LOGGER.debug(
                            "Startup cleanup: Frozen chunk ({}, {}) in {} with {} block entities, removed {} tickets",
                            chunkPos.x, chunkPos.z, dimensionId, blockEntityCount, frozenTickets);
                    }
                }
            } catch (Exception e) {
                Recyclingservice.LOGGER.debug(
                    "Failed to process chunk ({}, {}) in dimension {} during startup cleanup", 
                    chunkPos.x, chunkPos.z, dimensionId, e);
            }
        }
        
        return new DimensionCleanupStats(dimensionProcessedChunks, dimensionFrozenTickets, dimensionManagedChunks);
    }
    
    private static void logFinalResults(int totalFrozenChunks, int totalFrozenTickets) {
        if (totalFrozenChunks > 0) {
            int totalManagedChunks = FreezeDataStore.getManagedChunks().values().stream()
                .mapToInt(map -> map.size())
                .sum();
            
            Recyclingservice.LOGGER.info(
                "Startup chunk cleanup completed: {} chunks processed across all dimensions, {} total tickets removed, {} chunks now managed by mod",
                totalFrozenChunks, totalFrozenTickets, totalManagedChunks);
        } else {
            Recyclingservice.LOGGER.info("Startup chunk cleanup completed: No chunks needed to be processed");
        }
    }
    
    private record DimensionCleanupStats(int processedChunks, int frozenTickets, int managedChunks) {
    }
}