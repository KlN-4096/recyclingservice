package com.klnon.recyclingservice.content.chunk.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
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

    // 强加载区块的ticket level阈值
    private static final int FORCE_LOADED_THRESHOLD = 31;

    public static void handleTakeover(MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                int managedCount = takeoverDimensionChunks(level);
                Recyclingservice.LOGGER.info("Takeover {} complete: managed {} chunks",
                        level.dimension().location(), managedCount);
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
                            ChunkDataCache.UNIMPORTANT : ChunkDataCache.MANAGED;

                    // 不重要的区块会被冻结（通过DistanceManagerMixin实现）
                    // 不再需要移除tickets，因为我们现在通过ChunkDataCache状态控制

                    newChunks.add(new ChunkDataCache.ChunkInfo(dimension, chunkPos, blockEntityCount, state, 0));
                });

        // 按方块实体数量排序（从大到小）
        newChunks.sort((a, b) -> Integer.compare(b.blockEntityCount(), a.blockEntityCount()));
        // 批量设置到缓存
        ChunkDataCache.setManagedChunks(dimension, newChunks);
        return newChunks.size();
    }

    /**
     * 判断是否应该跳过此区块
     * 跳过条件：
     * 1. 已经被我们管理
     * 2. 包含玩家ticket（玩家正在操作）
     * 3. 不是强加载区块（所有tickets的level都 > 31）
     */
    private static boolean shouldSkipChunk(ChunkPos chunkPos,
                                           SortedArraySet<Ticket<?>> ticketSet,
                                           ResourceLocation dimension) {
        // 已经被管理，跳过
        if (ChunkDataCache.getChunkInfo(dimension, chunkPos) != null) {
            return true;
        }

        // 包含玩家ticket，跳过
        if (ticketSet.stream().anyMatch(ticket -> ticket.getType() == TicketType.PLAYER)) {
            return true;
        }

        // 不是强加载区块，跳过
        return ticketSet.stream().noneMatch(ticket -> ticket.getTicketLevel() <= FORCE_LOADED_THRESHOLD);
    }
}