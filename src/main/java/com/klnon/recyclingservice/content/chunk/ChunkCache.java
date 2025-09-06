package com.klnon.recyclingservice.content.chunk;

import com.klnon.recyclingservice.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块缓存 - 极简版本
 * 只存储真正需要的时间信息，状态通过ticket推断
 */
public class ChunkCache {
    
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
    
    // 唯一真正需要存储的信息：物品冻结区块的解冻时间
    private static final Map<ResourceLocation, Map<ChunkPos, Long>> itemFrozenChunks = new ConcurrentHashMap<>();

    
    /**
     * 获取维度中指定状态的区块列表（基于tickets推断）
     */
    public static List<ChunkPos> getChunksByState(ResourceLocation dimension, ChunkState state, ServerLevel level) {
        List<ChunkPos> result = new ArrayList<>();
        
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            
            tickets.forEach((encodedPos, ticketSet) -> {
                ChunkPos chunkPos = new ChunkPos(encodedPos);
                ChunkState currentState = getChunkState(dimension, chunkPos, ticketSet);
                
                if (currentState == state) {
                    result.add(chunkPos);
                }
            });
        } catch (Exception e) {
            // 推断失败，返回空列表
        }
        
        return result;
    }
    
    // ================== 物品冻结管理 ==================
    
    /**
     * 冻结区块（物品过多）
     */
    public static boolean freezeChunkForItems(ResourceLocation dimension, ChunkPos pos, ServerLevel level) {
        try {
            // 移除非白名单tickets
            int frozenTickets = freezeChunkTickets(pos, level);
            
            if (frozenTickets > 0) {
                // 记录解冻时间
                long unfreezeTime = System.currentTimeMillis() + Config.TECHNICAL.itemFreezeHours.get() * 3600_000L;
                itemFrozenChunks.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>())
                    .put(pos, unfreezeTime);
                return true;
            }
        } catch (Exception e) {
            // 冻结失败
        }
        return false;
    }
    
    /**
     * 检查物品超载区块是否应该解冻
     */
    public static boolean shouldUnfreezeItemFrozenChunk(ResourceLocation dimension, ChunkPos pos) {
        Long unfreezeTime = itemFrozenChunks.getOrDefault(dimension, Collections.emptyMap()).get(pos);
        return unfreezeTime != null && System.currentTimeMillis() >= unfreezeTime;
    }
    
    /**
     * 解冻区块（恢复管理）
     */
    public static boolean unfreezeChunk(ResourceLocation dimension, ChunkPos pos, ServerLevel level) {
        try {
            // 添加我们的管理ticket
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            distanceManager.addTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            
            // 移除解冻时间记录
            Map<ChunkPos, Long> dimensionFrozen = itemFrozenChunks.get(dimension);
            if (dimensionFrozen != null) {
                dimensionFrozen.remove(pos);
                if (dimensionFrozen.isEmpty()) {
                    itemFrozenChunks.remove(dimension);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取所有物品冻结的区块
     */
    public static List<ChunkPos> getItemFrozenChunks(ResourceLocation dimension) {
        return new ArrayList<>(itemFrozenChunks.getOrDefault(dimension, Collections.emptyMap()).keySet());
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
     * 移除管理ticket
     */
    public static boolean removeManagementTicket(ChunkPos pos, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            distanceManager.removeTicket(RECYCLING_SERVICE_TICKET, pos, 31, pos);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 冻结区块tickets(移除非白名单tickets)
     */
    public static int freezeChunkTickets(ChunkPos chunkPos, ServerLevel level) {
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
                if (!WHITELIST_TICKET_TYPES.contains(ticket.getType())) {
                    ticketsToRemove.add(ticket);
                }
            }
            
            for (Ticket<?> ticket : ticketsToRemove) {
                distanceManager.removeTicket(chunkKey, ticket);
            }
            
            return ticketsToRemove.size();
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ================== 辅助方法 ==================
    
    /**
     * 根据tickets推断区块状态
     */
    private static ChunkState getChunkState(ResourceLocation dimension, ChunkPos pos, SortedArraySet<Ticket<?>> ticketSet) {
        // 检查是否物品冻结
        if (itemFrozenChunks.getOrDefault(dimension, Collections.emptyMap()).containsKey(pos)) {
            return ChunkState.ITEM_FROZEN;
        }
        
        // 检查是否被我们管理
        boolean hasOurTicket = ticketSet.stream()
            .anyMatch(ticket -> ticket.getType() == RECYCLING_SERVICE_TICKET);
        
        if (hasOurTicket) {
            return ChunkState.MANAGED;
        }
        
        return ChunkState.UNMANAGED;
    }
}