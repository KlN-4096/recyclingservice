package com.klnon.recyclingservice.util.other;

import java.lang.reflect.Field;
import java.util.*;

import com.klnon.recyclingservice.Recyclingservice;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.util.SortedArraySet;

/**
 * 区块冻结工具类
 * 通过DistanceManager.removeTicket()移除区块的tickets来实现"冻结"效果
 * 保留白名单ticket类型：POST_TELEPORT, PLAYER, START, UNKNOWN, PORTAL
 */
public class ChunkFreezer {
    
    // 白名单ticket类型（不会被移除）
    private static final Set<TicketType<?>> WHITELIST_TICKET_TYPES = Set.of(
        TicketType.POST_TELEPORT,
        TicketType.PLAYER,
        TicketType.START,
        TicketType.UNKNOWN,
        TicketType.PORTAL
    );
    
    /**
     * 冻结单个区块 - 移除所有非白名单tickets
     * 
     * @param chunkPos 区块位置
     * @param level 服务器世界
     * @return 移除的ticket数量
     */
    public static int freezeChunk(ChunkPos chunkPos, ServerLevel level) {
        return ErrorHandler.handleOperation(null, "freezeChunk", () -> {
            DistanceManager distanceManager = level.getChunkSource().chunkMap.getDistanceManager();
            int removedCount = 0;
            
            try {
                // 获取区块的所有tickets
                Long2ObjectLinkedOpenHashMap<SortedArraySet<Ticket<?>>> tickets = getTicketsMap(distanceManager);
                long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
                SortedArraySet<Ticket<?>> chunkTickets = tickets.get(chunkKey);
                
                if (chunkTickets != null && !chunkTickets.isEmpty()) {
                    // 创建要移除的tickets列表（避免并发修改）
                    List<Ticket<?>> ticketsToRemove = new ArrayList<>();
                    
                    for (Ticket<?> ticket : chunkTickets) {
                        if (!WHITELIST_TICKET_TYPES.contains(ticket.getType())) {
                            ticketsToRemove.add(ticket);
                        }
                    }
                    
                    // 移除非白名单tickets
                    for (Ticket<?> ticket : ticketsToRemove) {
                        removeTicketSafely(distanceManager, ticket.getType(), chunkPos, ticket.getTicketLevel(), ticket.key);
                        removedCount++;
                    }
                }
                
                if (removedCount > 0) {
                    Recyclingservice.LOGGER.info("Frozen chunk at ({}, {}) by removing {} tickets", 
                        chunkPos.x, chunkPos.z, removedCount);
                }
                
                return removedCount;
                
            } catch (Exception e) {
                Recyclingservice.LOGGER.error("Failed to freeze chunk ({}, {}): {}", 
                    chunkPos.x, chunkPos.z, e.getMessage());
                return 0;
            }
        }, 0);
    }
    
    /**
     * 安全移除ticket
     * 
     * @param distanceManager 距离管理器
     * @param ticketType ticket类型
     * @param chunkPos 区块位置
     * @param level ticket级别
     * @param value ticket值
     */
    @SuppressWarnings("unchecked")
    private static <T> void removeTicketSafely(DistanceManager distanceManager, TicketType<T> ticketType, 
                                              ChunkPos chunkPos, int level, Object value) {
        try {
            distanceManager.removeTicket(ticketType, chunkPos, level, (T) value);
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to remove ticket {} for chunk ({}, {}): {}", 
                ticketType, chunkPos.x, chunkPos.z, e.getMessage());
        }
    }
    
    /**
     * 通过反射获取DistanceManager的tickets映射
     * 
     * @param distanceManager 距离管理器
     * @return tickets映射
     * @throws Exception 反射异常
     */
    @SuppressWarnings("unchecked")
    private static Long2ObjectLinkedOpenHashMap<SortedArraySet<Ticket<?>>> getTicketsMap(DistanceManager distanceManager) 
            throws Exception {
        Field ticketsField = DistanceManager.class.getDeclaredField("tickets");
        ticketsField.setAccessible(true);
        return (Long2ObjectLinkedOpenHashMap<SortedArraySet<Ticket<?>>>) ticketsField.get(distanceManager);
    }
    
    /**
     * 获取区块持有者映射（供外部调用）
     * 主要用于获取区块的ticketLevel信息
     */
    public static Long2ObjectLinkedOpenHashMap<ChunkHolder> getChunkHolderMap(ServerLevel level) {
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            ChunkMap chunkMap = chunkSource.chunkMap;
            
            Field visibleChunkMapField = ChunkMap.class.getDeclaredField("visibleChunkMap");
            visibleChunkMapField.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap = 
                (Long2ObjectLinkedOpenHashMap<ChunkHolder>) visibleChunkMapField.get(chunkMap);
                
            return visibleChunkMap;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Recyclingservice.LOGGER.error("Failed to access chunk holder map via reflection: {}", e.getMessage());
            return null;
        }
    }
}