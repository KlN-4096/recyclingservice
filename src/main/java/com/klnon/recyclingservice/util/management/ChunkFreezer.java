package com.klnon.recyclingservice.util.management;

import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.util.ErrorHandler;
import com.klnon.recyclingservice.util.MessageUtils;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.SortedArraySet;

import javax.annotation.Nonnull;

/**
 * 区块冻结工具 - 通过移除tickets来"冻结"过载区块
 * 保留白名单tickets：POST_TELEPORT, PLAYER, START, UNKNOWN, PORTAL
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
        int radius = Config.CHUNK_FREEZING_SEARCH_RADIUS.get();
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
                    chunkPos.x*16, chunkPos.z*16, removedCount);
            }
            
            return removedCount;
            
        }, 0);
    }
    
    /**
     * 过滤有效的tickets
     */
    private static List<Ticket<?>> filterValidTickets(SortedArraySet<Ticket<?>> tickets) {
        return tickets.stream()
            .filter(ticket -> !WHITELIST_TICKET_TYPES.contains(ticket.getType()))
            .filter(ticket -> ticket.getTicketLevel() <= 32)
            .collect(Collectors.toList());
    }
    
    /**
     * 批量冻结影响目标区块的所有加载器
     * @param targetChunk 物品过多的目标区块
     * @param level 服务器世界
     * @return 冻结结果信息
     */
    public static FreezeResult freezeAllAffectingChunkLoaders(ChunkPos targetChunk, ServerLevel level) {
        return ErrorHandler.handleOperation(null, "freezeAllAffectingChunkLoaders", () -> {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            
            // 动态获取搜索参数
            int searchRadius = Config.CHUNK_FREEZING_SEARCH_RADIUS.get();
            int maxTicketsToCheck = getMaxTicketsToCheck();
            
            // 调试日志：显示动态参数
            Recyclingservice.LOGGER.debug("Chunk freezing search parameters: radius={}, maxTickets={}, searchArea={}", 
                searchRadius, maxTicketsToCheck, (2 * searchRadius + 1) * (2 * searchRadius + 1));
            
            // 使用Stream API简化候选区块收集
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
     * 执行区块冻结检查 - 检测超载区块并冻结影响它们的加载器
     * @param dimensionId 维度ID
     * @param level 服务器维度实例
     */
    public static void performChunkFreezingCheck(ResourceLocation dimensionId, ServerLevel level) {
        try {
            // 使用O(1)超载区块获取
            List<ChunkPos> overloadedChunks = SimpleReportCache.getOverloadedChunks(dimensionId);
            
            // 对每个超载区块执行冻结
            for (ChunkPos chunkPos : overloadedChunks) {
                // 批量冻结所有影响目标区块的加载器
                FreezeResult freezeResult = freezeAllAffectingChunkLoaders(chunkPos, level);
                
                if (!freezeResult.isEmpty()) {
                    Recyclingservice.LOGGER.info(
                        "Chunk freezing triggered during cleanup: Frozen {} chunk loaders affecting chunk ({}, {}) in {}: {} tickets removed", 
                        freezeResult.getFrozenChunkCount(), chunkPos.x, chunkPos.z, dimensionId, freezeResult.totalFrozenTickets());
                    
                    // 详细记录每个被冻结的区块
                    for (ChunkPos frozenChunk : freezeResult.frozenChunks()) {
                        Recyclingservice.LOGGER.debug(
                            "  → Frozen chunk loader at ({}, {}) in dimension {}", 
                            frozenChunk.x, frozenChunk.z, dimensionId);
                    }
                } else {
                    // 如果找不到任何加载器，冻结当前区块（回退逻辑）
                    int frozenTickets = freezeChunk(chunkPos, level);
                    if (frozenTickets > 0) {
                        Recyclingservice.LOGGER.info(
                            "No affecting chunk loaders found during cleanup, frozen current chunk ({}, {}) in {} with {} tickets", 
                            chunkPos.x, chunkPos.z, dimensionId, frozenTickets);
                    }
                }
                
                // 发送警告消息（如果启用）
                if (Config.ENABLE_CHUNK_ITEM_WARNING.get()) {
                    int entityCount = SimpleReportCache.getEntityCountByChunk(dimensionId).getOrDefault(chunkPos, 0);
                    int worldX = chunkPos.x * 16 + 8;
                    int worldZ = chunkPos.z * 16 + 8;
                    
                    // 获取ticketLevel（通过直接访问chunkMap）
                    int ticketLevel = 33; // 默认值：未加载
                    try {
                        var chunkHolderMap = level.getChunkSource().chunkMap.visibleChunkMap;
                        long chunkKey = ChunkPos.asLong(chunkPos.x, chunkPos.z);
                        var holder = chunkHolderMap.get(chunkKey);
                        if (holder != null) {
                            ticketLevel = holder.getTicketLevel();
                        }
                    } catch (Exception e) {
                        // 获取ticketLevel失败，使用默认值
                    }
                    
                    net.minecraft.network.chat.Component warningMessage = 
                        MessageUtils.getItemWarningMessage(entityCount, worldX, worldZ, ticketLevel);
                    MessageUtils.sendChatMessage(level.getServer(), warningMessage);
                }
            }
            
            if (!overloadedChunks.isEmpty()) {
                Recyclingservice.LOGGER.info(
                    "Chunk freezing check completed for dimension {}: {} overloaded chunks processed", 
                    dimensionId, overloadedChunks.size());
            }
            
        } catch (Exception e) {
            // 出错跳过，不影响清理流程
            Recyclingservice.LOGGER.debug(
                "Failed to perform chunk freezing check for dimension {}", dimensionId, e);
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