package com.klnon.recyclingservice.util.other;

import java.lang.reflect.Field;
import java.util.*;

import com.klnon.recyclingservice.Recyclingservice;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.SortedArraySet;
/**
 * 区块冻结工具类
 * 通过DistanceManager.removeTicket()移除区块的tickets来实现"冻结"效果
 * 保留白名单ticket类型：POST_TELEPORT, PLAYER, START, UNKNOWN, PORTAL
 * 使用AccessTransformer简化访问
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
            // 直接访问distanceManager（通过AccessTransformer公开）
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            int removedCount = 0;
            
            // 直接访问tickets映射（通过AccessTransformer公开）
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
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
                
                // 直接移除tickets（通过AccessTransformer公开的方法）
                for (Ticket<?> ticket : ticketsToRemove) {
                    distanceManager.removeTicket(chunkKey, ticket);
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                Recyclingservice.LOGGER.info("Frozen chunk at ({}, {}) by removing {} tickets", 
                    chunkPos.x, chunkPos.z, removedCount);
            }
            
            return removedCount;
            
        }, 0);
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