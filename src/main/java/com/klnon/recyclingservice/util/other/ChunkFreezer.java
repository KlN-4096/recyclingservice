package com.klnon.recyclingservice.util.other;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.Config;
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
     * 根据搜索半径动态计算最大检查ticket数量
     */
    private static int getMaxTicketsToCheck() {
        int radius = Config.getChunkFreezingSearchRadius();
        int searchArea = (2 * radius + 1) * (2 * radius + 1);  // 方形区域
        return (int)(searchArea * 1.5);  // 1.5倍安全系数
    }
    
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
     * 批量冻结所有影响目标区块的加载器
     * 找到所有影响目标区块的加载器位置并冻结它们
     * 
     * @param targetChunk 物品过多的目标区块
     * @param level 服务器世界
     * @return 冻结结果信息
     */
    public static FreezeResult freezeAllAffectingChunkLoaders(ChunkPos targetChunk, ServerLevel level) {
        return ErrorHandler.handleOperation(null, "freezeAllAffectingChunkLoaders", () -> {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            
            // 动态获取搜索参数
            int searchRadius = Config.getChunkFreezingSearchRadius();
            int maxTicketsToCheck = getMaxTicketsToCheck();
            
            // 调试日志：显示动态参数
            Recyclingservice.LOGGER.debug("Chunk freezing search parameters: radius={}, maxTickets={}, searchArea={}", 
                searchRadius, maxTicketsToCheck, (2 * searchRadius + 1) * (2 * searchRadius + 1));
            
            // 预处理：一次性收集所有相关tickets
            Map<ChunkPos, List<Ticket<?>>> candidateChunks = new HashMap<>();
            int checkedTickets = 0;
            
            for (var entry : tickets.long2ObjectEntrySet()) {
                if (checkedTickets >= maxTicketsToCheck) break;
                
                ChunkPos chunkPos = new ChunkPos(entry.getLongKey());
                
                // 预筛选：只考虑搜索范围内的区块
                if (getChebysevDistance(chunkPos, targetChunk) > searchRadius) {
                    continue;
                }
                
                // 过滤有效tickets
                List<Ticket<?>> validTickets = entry.getValue().stream()
                    .filter(ticket -> !WHITELIST_TICKET_TYPES.contains(ticket.getType()))
                    .filter(ticket -> ticket.getTicketLevel() <= 32)
                    .collect(Collectors.toList());
                
                if (!validTickets.isEmpty()) {
                    candidateChunks.put(chunkPos, validTickets);
                    checkedTickets += validTickets.size();
                }
            }
            
            // 批量冻结所有影响目标区块的加载器
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
            
            return new FreezeResult(frozenChunks, totalFrozenTickets);
                
        }, new FreezeResult(List.of(), 0));
    }
    
    /**
     * 计算ticket的影响半径
     * 
     * @param ticketLevel ticket等级
     * @return 影响半径
     */
    private static int calculateInfluenceRadius(int ticketLevel) {
        // 基于Minecraft区块加载机制：level越低，影响范围越大
        return Math.max(0, (33 - ticketLevel));
    }
    
    /**
     * 计算两个区块之间的切比雪夫距离（棋盘距离）
     * 
     * @param pos1 区块1
     * @param pos2 区块2
     * @return 切比雪夫距离
     */
    private static int getChebysevDistance(ChunkPos pos1, ChunkPos pos2) {
        return Math.max(Math.abs(pos1.x - pos2.x), Math.abs(pos1.z - pos2.z));
    }
    
    /**
     * 判断候选区块的tickets是否对目标区块有影响
     * 
     * @param candidateChunk 候选区块位置
     * @param tickets 候选区块的tickets
     * @param targetChunk 目标区块
     * @return 是否有影响
     */
    private static boolean hasInfluenceOn(ChunkPos candidateChunk, List<Ticket<?>> tickets, ChunkPos targetChunk) {
        int distance = getChebysevDistance(candidateChunk, targetChunk);
        
        return tickets.stream().anyMatch(ticket -> {
            int influenceRadius = calculateInfluenceRadius(ticket.getTicketLevel());
            return distance <= influenceRadius;
        });
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
    
    /**
     * 冻结结果记录类
     * 包含被冻结的区块位置列表和总票据数量
     */
    public record FreezeResult(List<ChunkPos> frozenChunks, int totalFrozenTickets) {
        
        public boolean isEmpty() {
            return frozenChunks.isEmpty();
        }
        
        public int getFrozenChunkCount() {
            return frozenChunks.size();
        }
        
        @Override
        public String toString() {
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