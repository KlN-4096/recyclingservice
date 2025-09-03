package com.klnon.recyclingservice.content.chunk.freezer;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.entity.EntityReportCache;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心区块冻结器
 * 负责基础的区块冻结逻辑和算法
 */
public class ChunkFreezer {
    
    /**
     * 冻结单个区块 - 移除所有非白名单tickets
     */
    public static int freezeChunk(ChunkPos chunkPos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
            SortedArraySet<Ticket<?>> chunkTickets = tickets.get(chunkKey);
            
            if (chunkTickets == null || chunkTickets.isEmpty()) {
                return 0;
            }
            
            List<Ticket<?>> ticketsToRemove = new ArrayList<>();
            for (Ticket<?> ticket : chunkTickets) {
                if (!FreezeDataStore.WHITELIST_TICKET_TYPES.contains(ticket.getType())) {
                    ticketsToRemove.add(ticket);
                }
            }
            
            for (Ticket<?> ticket : ticketsToRemove) {
                distanceManager.removeTicket(chunkKey, ticket);
            }
            
            if (ticketsToRemove.size() > 0) {
                Recyclingservice.LOGGER.info("Frozen chunk at ({}, {}) by removing {} tickets", 
                    chunkPos.x*16, chunkPos.z*16, ticketsToRemove.size());
            }
            
            return ticketsToRemove.size();
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to freeze chunk ({}, {})", chunkPos.x, chunkPos.z, e);
            return 0;
        }
    }
    
    /**
     * 批量冻结影响目标区块的所有加载器
     */
    public static FreezeDataStore.FreezeResult freezeAllAffectingChunkLoaders(ChunkPos targetChunk, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            
            int searchRadius = Config.TECHNICAL.chunkFreezingSearchRadius.get();
            int maxTicketsToCheck = getMaxTicketsToCheck();
            
            Map<ChunkPos, List<Ticket<?>>> candidateChunks = tickets.long2ObjectEntrySet()
                .stream()
                .limit(maxTicketsToCheck)
                .map(entry -> Map.entry(new ChunkPos(entry.getLongKey()), entry.getValue()))
                .filter(entry -> getChebysevDistance(entry.getKey(), targetChunk) <= searchRadius)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> filterValidTickets(entry.getValue()),
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ))
                .entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(
                    Map.Entry::getKey, 
                    Map.Entry::getValue,
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));
            
            List<ChunkPos> frozenChunks = new ArrayList<>();
            int totalFrozenTickets = 0;
            
            for (var entry : candidateChunks.entrySet()) {
                ChunkPos candidatePos = entry.getKey();
                List<Ticket<?>> candidateTickets = entry.getValue();
                
                if (hasInfluenceOn(candidatePos, candidateTickets, targetChunk)) {
                    int frozenCount = freezeChunk(candidatePos, level);
                    if (frozenCount > 0) {
                        frozenChunks.add(candidatePos);
                        totalFrozenTickets += frozenCount;
                    }
                }
            }
            
            return new FreezeDataStore.FreezeResult(frozenChunks, totalFrozenTickets);
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to freeze affecting chunk loaders", e);
            return new FreezeDataStore.FreezeResult(List.of(), 0);
        }
    }
    
    /**
     * 执行区块冻结检查 - 检测超载区块并冻结影响它们的加载器
     */
    public static void performChunkFreezingCheck(ResourceLocation dimensionId, ServerLevel level) {
        try {
            List<ChunkPos> overloadedChunks = EntityReportCache.getOverloadedChunks(dimensionId);
            
            for (ChunkPos chunkPos : overloadedChunks) {
                FreezeDataStore.FreezeResult freezeResult = freezeAllAffectingChunkLoaders(chunkPos, level);
                
                if (!freezeResult.isEmpty()) {
                    Recyclingservice.LOGGER.info(
                        "Chunk freezing triggered during cleanup: Frozen {} chunk loaders affecting chunk ({}, {}) in {}: {} tickets removed", 
                        freezeResult.getFrozenChunkCount(), chunkPos.x, chunkPos.z, dimensionId, freezeResult.totalFrozenTickets());
                    
                    for (ChunkPos frozenChunk : freezeResult.frozenChunks()) {
                        Recyclingservice.LOGGER.debug(
                            "  → Frozen chunk loader at ({}, {}) in dimension {}", 
                            frozenChunk.x, frozenChunk.z, dimensionId);
                    }
                } else {
                    int frozenTickets = freezeChunk(chunkPos, level);
                    if (frozenTickets > 0) {
                        Recyclingservice.LOGGER.info(
                            "No affecting chunk loaders found during cleanup, frozen current chunk ({}, {}) in {} with {} tickets", 
                            chunkPos.x, chunkPos.z, dimensionId, frozenTickets);
                    }
                }
                
                if (Config.TECHNICAL.enableChunkItemWarning.get()) {
                    sendChunkWarning(dimensionId, chunkPos, level);
                }
            }
            
            if (!overloadedChunks.isEmpty()) {
                Recyclingservice.LOGGER.info(
                    "Chunk freezing check completed for dimension {}: {} overloaded chunks processed", 
                    dimensionId, overloadedChunks.size());
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug(
                "Failed to perform chunk freezing check for dimension {}", dimensionId, e);
        }
    }
    
    // 辅助方法
    
    private static int getMaxTicketsToCheck() {
        int radius = Config.TECHNICAL.chunkFreezingSearchRadius.get();
        int searchArea = (2 * radius + 1) * (2 * radius + 1);
        return (int)(searchArea * 1.5);
    }
    
    private static List<Ticket<?>> filterValidTickets(SortedArraySet<Ticket<?>> tickets) {
        return tickets.stream()
            .filter(ticket -> !FreezeDataStore.WHITELIST_TICKET_TYPES.contains(ticket.getType()))
            .filter(ticket -> ticket.getTicketLevel() <= 32)
            .collect(Collectors.toList());
    }
    
    private static int getChebysevDistance(ChunkPos pos1, ChunkPos pos2) {
        return Math.max(Math.abs(pos1.x - pos2.x), Math.abs(pos1.z - pos2.z));
    }
    
    private static int calculateInfluenceRadius(int ticketLevel) {
        return Math.max(0, (33 - ticketLevel));
    }
    
    private static boolean hasInfluenceOn(ChunkPos candidateChunk, List<Ticket<?>> tickets, ChunkPos targetChunk) {
        int distance = getChebysevDistance(candidateChunk, targetChunk);
        
        return tickets.stream().anyMatch(ticket -> {
            int influenceRadius = calculateInfluenceRadius(ticket.getTicketLevel());
            return distance <= influenceRadius;
        });
    }
    
    private static void sendChunkWarning(ResourceLocation dimensionId, ChunkPos chunkPos, ServerLevel level) {
        try {
            int entityCount = EntityReportCache.getEntityCountByChunk(dimensionId).getOrDefault(chunkPos, 0);
            int worldX = chunkPos.x * 16 + 8;
            int worldZ = chunkPos.z * 16 + 8;
            
            int ticketLevel = 33;
            try {
                var chunkHolderMap = level.getChunkSource().chunkMap.visibleChunkMap;
                long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
                var holder = chunkHolderMap.get(chunkKey);
                if (holder != null) {
                    ticketLevel = holder.getTicketLevel();
                }
            } catch (Exception ignored) {
            }
            
            var warningMessage = MessageHelper.getItemWarningMessage(entityCount, worldX, worldZ, ticketLevel);
            MessageHelper.sendChatMessage(level.getServer(), warningMessage);
        } catch (Exception ignored) {
        }
    }
}