package com.klnon.recyclingservice.content.chunk.freezer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.DistanceManager;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块数据存储管理器
 * 负责管理区块状态数据和ticket操作
 */
public class FreezeDataStore {
    
    // 白名单ticket类型
    public static final Set<TicketType<?>> WHITELIST_TICKET_TYPES = Set.of(
        TicketType.POST_TELEPORT,
        TicketType.PLAYER,
        TicketType.START,
        TicketType.UNKNOWN,
        TicketType.PORTAL
    );
    
    // 自定义ticket类型
    public static final TicketType<ChunkPos> RECYCLING_SERVICE_TICKET = 
        TicketType.create("recycling_service_chunk", Comparator.comparingLong(ChunkPos::toLong), 600);
    
    // 管理的区块数据
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> managedChunks = new ConcurrentHashMap<>();
    
    // 暂停的区块数据
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> suspendedChunks = new ConcurrentHashMap<>();
    
    /**
     * 添加管理的区块
     */
    public static void addManagedChunk(ChunkPos chunkPos, ServerLevel level, int blockEntityCount) {
        try {
            ResourceLocation dimensionId = level.dimension().location();
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            distanceManager.addTicket(RECYCLING_SERVICE_TICKET, chunkPos, 31, chunkPos);
            managedChunks.computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
                .put(chunkPos, new ChunkInfo(chunkPos, blockEntityCount));
        } catch (Exception ignored) {
        }
    }
    
    /**
     * 移除管理的区块
     */
    public static void removeManagedChunk(ChunkPos chunkPos, ServerLevel level) {
        try {
            ResourceLocation dimensionId = level.dimension().location();
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            distanceManager.removeTicket(RECYCLING_SERVICE_TICKET, chunkPos, 31, chunkPos);
            
            Map<ChunkPos, ChunkInfo> dimensionChunks = managedChunks.get(dimensionId);
            if (dimensionChunks != null) {
                dimensionChunks.remove(chunkPos);
                if (dimensionChunks.isEmpty()) {
                    managedChunks.remove(dimensionId);
                }
            }
        } catch (Exception ignored) {
        }
    }
    
    /**
     * 添加暂停的区块
     */
    public static void addSuspendedChunk(ResourceLocation dimensionId, ChunkPos chunkPos, ChunkInfo chunkInfo) {
        suspendedChunks.computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
            .put(chunkPos, chunkInfo);
    }
    
    /**
     * 移除暂停的区块
     */
    public static void removeSuspendedChunk(ResourceLocation dimensionId, ChunkPos chunkPos) {
        Map<ChunkPos, ChunkInfo> dimensionSuspendedChunks = suspendedChunks.get(dimensionId);
        if (dimensionSuspendedChunks != null) {
            dimensionSuspendedChunks.remove(chunkPos);
            if (dimensionSuspendedChunks.isEmpty()) {
                suspendedChunks.remove(dimensionId);
            }
        }
    }
    
    /**
     * 获取管理的区块
     */
    public static Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> getManagedChunks() {
        return managedChunks;
    }
    
    /**
     * 获取暂停的区块
     */
    public static Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> getSuspendedChunks() {
        return suspendedChunks;
    }
    
    /**
     * 区块信息记录类
     */
    public record ChunkInfo(ChunkPos chunkPos, int blockEntityCount, long addedTime) {
        public ChunkInfo(ChunkPos chunkPos, int blockEntityCount) {
            this(chunkPos, blockEntityCount, System.currentTimeMillis());
        }
    }
    
    /**
     * 区块暂停候选者记录类
     */
    public record ChunkSuspensionCandidate(ResourceLocation dimensionId, ServerLevel level, ChunkPos chunkPos, ChunkInfo chunkInfo) {
    }
    
    /**
     * 冻结结果记录类
     */
    public record FreezeResult(List<ChunkPos> frozenChunks, int totalFrozenTickets) {
        
        public boolean isEmpty() {
            return frozenChunks.isEmpty();
        }
        
        public int getFrozenChunkCount() {
            return frozenChunks.size();
        }
        
        @Override
        public @Nonnull String toString() {
            if (isEmpty()) {
                return "FreezeResult{no chunks frozen}";
            }
            return String.format("FreezeResult{%d chunks frozen, %d tickets removed: %s}",
                getFrozenChunkCount(), totalFrozenTickets, 
                frozenChunks.stream()
                    .map(pos -> String.format("(%d,%d)", pos.x, pos.z))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
        }
    }
}