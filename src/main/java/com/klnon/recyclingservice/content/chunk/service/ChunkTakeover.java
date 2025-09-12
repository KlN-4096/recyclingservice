package com.klnon.recyclingservice.content.chunk.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import com.klnon.recyclingservice.content.chunk.function.TicketManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务器接管区块功能类
 * 接管所有非白名单Ticket导致的强加载区块
 */
public class ChunkTakeover {
    public static void handleTakeover(MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                int managedCount = takeoverDimensionChunks(level);
                Recyclingservice.LOGGER.info("Takeover {} complete: managed {} chunks"
                        ,level.dimension().location(),managedCount);
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to perform chunk takeover", e);
        }
    }

    private static int takeoverDimensionChunks(ServerLevel level) {
        List<ChunkDataCache.ChunkInfo> newChunks = new ArrayList<>();
        ResourceLocation dimension = level.dimension().location();

        // 扫描所有ticket区块
        level.getChunkSource().chunkMap.getDistanceManager().tickets.long2ObjectEntrySet()
                                                .forEach(entry -> {
            var ticketSet = entry.getValue();
            ChunkPos chunkPos = new ChunkPos(entry.getLongKey());

            if (shouldSkipChunk(chunkPos, ticketSet, dimension)) return;

            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            short blockEntityCount = (short) chunk.getBlockEntities().size();

            // 根据方块实体数量决定管理方式
            byte state = blockEntityCount < Config.TECHNICAL.chunkEntityThreshold.get() ?
                        ChunkDataCache.UNIMPORTANT: ChunkDataCache.MANAGED;
            // 移除非白名单ticket
            if(state== ChunkDataCache.UNIMPORTANT)
                TicketManager.removeManagementTicket(chunkPos, level);
            newChunks.add(new ChunkDataCache.ChunkInfo(dimension, chunkPos, blockEntityCount, state, 0));
        });

        // 按方块实体数量排序（从大到小）
        newChunks.sort((a, b) -> Integer.compare(b.blockEntityCount(), a.blockEntityCount()));
        // 批量设置到缓存
        ChunkDataCache.setManagedChunks(dimension, newChunks);
        return newChunks.size();
    }

    /**
     * 未被我们管理(已经管理的话,直接跳过后2个判断,优化性能)
     * 检查是否不含玩家ticket(即玩家正在捣鼓机器的时候周围的强加载不管理)
     * 只要有非白名单ticket
     */
    private static boolean shouldSkipChunk(ChunkPos chunkPos,
                                           SortedArraySet<Ticket<?>> ticketSet,
                                           ResourceLocation dimension) {
        return ChunkDataCache.getChunkInfo(dimension, chunkPos) != null ||
                ticketSet.stream().anyMatch(ticket -> ticket.getType() == TicketType.PLAYER) ||
                ticketSet.stream().allMatch(ticket -> TicketManager.WHITELIST_TICKET_TYPES.contains(ticket.getType()));
    }
}
