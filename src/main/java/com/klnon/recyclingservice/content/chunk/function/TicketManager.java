package com.klnon.recyclingservice.content.chunk.function;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class TicketManager {
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
            TicketType.create("recycling_service_chunk", Comparator.comparingLong(ChunkPos::toLong));
    // ================== 物品冻结管理 ==================
    /**
     * 扩散冻结：冻结超载区块及其周围半径内的非白名单强加载区块
     */
    public static int freezeChunkWithRadius(ResourceLocation dimension, ChunkPos centerChunk, ServerLevel level) {
        int radius = Config.TECHNICAL.chunkFreezingSearchRadius.get();
        int frozenCount = 0;

        try {
            for (int x = centerChunk.x - radius; x <= centerChunk.x + radius; x++) {
                for (int z = centerChunk.z - radius; z <= centerChunk.z + radius; z++) {
                    ChunkPos chunkPos = new ChunkPos(x, z);

                    // 调用freezeChunk内部处理白名单检查和移除
                    if (freezeChunk(dimension, chunkPos, level, ChunkDataCache.ITEM_FROZEN, Config.TECHNICAL.itemFreezeMinutes.get())) {
                        frozenCount++;
                    }
                }
            }

        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to freeze chunks with radius for ({}, {})",
                    centerChunk.x, centerChunk.z, e);
        }

        return frozenCount;
    }
    /**
     * 通用冻结区块方法
     */
    public static boolean freezeChunk(ResourceLocation dimension, ChunkPos pos, ServerLevel level, byte newState, long unfreezeTime) {
        // 移除所有非白名单tickets
        int frozenTickets = removeManagementTicket(pos, level);
        return frozenTickets > 0 && ChunkDataCache.updateChunkState(dimension, pos, newState,
                System.currentTimeMillis() + unfreezeTime * 60 * 1000L);
    }
    /**
     * 解冻区块
     */
    public static boolean unfreezeChunk(ResourceLocation dimension, ChunkPos pos, ServerLevel level) {
        return addManagementTicket(pos, level) && ChunkDataCache.updateChunkState(dimension, pos, ChunkDataCache.MANAGED, 0);
    }


    // ================== Ticket管理 ==================

    /**
     * 添加管理ticket
     */
    public static boolean addManagementTicket(ChunkPos pos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            distanceManager.addTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 冻结区块tickets(移除非白名单tickets)
     */
    public static short removeManagementTicket(ChunkPos chunkPos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
            SortedArraySet<Ticket<?>> chunkTickets = tickets.get(chunkKey);

            if (chunkTickets == null || chunkTickets.isEmpty()) {
                return 0;
            }

            short removedTickets=0;
            for (Ticket<?> ticket : chunkTickets) {
                if (!WHITELIST_TICKET_TYPES.contains(ticket.getType())) {
                    distanceManager.removeTicket(chunkKey, ticket);
                    removedTickets++;
                }
            }
            return removedTickets;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取指定区块的所有tickets
     */
    public static List<Ticket<?>> getChunkTickets(ChunkPos chunkPos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
            SortedArraySet<Ticket<?>> chunkTickets = distanceManager.tickets.get(chunkKey);

            return chunkTickets != null ? List.copyOf(chunkTickets) : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
