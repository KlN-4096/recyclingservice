package com.klnon.recyclingservice.util.management;

import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.util.MessageUtils;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.SortedArraySet;

import javax.annotation.Nonnull;

/**
 * 区块冻结工具 - 通过移除tickets来"冻结"过载区块
 * 保留白名单tickets：POST_TELEPORT, PLAYER, START, UNKNOWN, PORTAL
 * 新增动态区块管理功能：根据服务器性能动态管理区块加载
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
    
    // 自定义ticket类型 - 为mod管理的区块使用
    private static final TicketType<ChunkPos> RECYCLING_SERVICE_TICKET = 
        TicketType.create("recycling_service_chunk", Comparator.comparingLong(ChunkPos::toLong), 600);
    
    // 数据结构：管理的区块（地图 -> 区块信息）
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> managedChunks = new ConcurrentHashMap<>();
    
    // 数据结构：暂停的区块（地图 -> 区块信息）
    private static final Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> suspendedChunks = new ConcurrentHashMap<>();
    /**
     * 根据搜索半径动态计算最大检查ticket数量
     */
    private static int getMaxTicketsToCheck() {
        int radius = Config.TECHNICAL.chunkFreezingSearchRadius.get();
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
     * 过滤有效的tickets
     */
    private static List<Ticket<?>> filterValidTickets(SortedArraySet<Ticket<?>> tickets) {
        return tickets.stream()
            .filter(ticket -> !WHITELIST_TICKET_TYPES.contains(ticket.getType()))
            .filter(ticket -> ticket.getTicketLevel() <= 32)
            .collect(Collectors.toList());
    }
    
    /**
     * 添加自定义管理ticket
     */
    private static void addManagedTicket(ChunkPos chunkPos, ServerLevel level, int blockEntityCount) {
        try {
            ResourceLocation dimensionId = level.dimension().location();
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            
            distanceManager.addTicket(RECYCLING_SERVICE_TICKET, chunkPos, 31, chunkPos);
            managedChunks.computeIfAbsent(dimensionId, k -> new ConcurrentHashMap<>())
                .put(chunkPos, new ChunkInfo(chunkPos, blockEntityCount));
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to add managed ticket for chunk ({}, {})", chunkPos.x, chunkPos.z, e);
        }
    }
    
    /**
     * 移除自定义管理ticket
     */
    private static void removeManagedTicket(ChunkPos chunkPos, ServerLevel level) {
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
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to remove managed ticket for chunk ({}, {})", chunkPos.x, chunkPos.z, e);
        }
    }
    
    /**
     * 暂停或恢复区块 - 统一处理逻辑
     */
    private static int suspendOrRestoreChunks(MinecraftServer server, int count, boolean isSuspend) {
        if (count <= 0) return 0;
        
        Map<ResourceLocation, Map<ChunkPos, ChunkInfo>> sourceChunks = isSuspend ? managedChunks : suspendedChunks;
        List<ChunkSuspensionCandidate> candidates = new ArrayList<>();
        
        // 收集候选区块
        for (var dimensionEntry : sourceChunks.entrySet()) {
            ResourceLocation dimensionId = dimensionEntry.getKey();
            ServerLevel level = server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dimensionId));
            if (level == null) continue;
            
            for (var chunkEntry : dimensionEntry.getValue().entrySet()) {
                ChunkPos chunkPos = chunkEntry.getKey();
                ChunkInfo chunkInfo = chunkEntry.getValue();
                candidates.add(new ChunkSuspensionCandidate(dimensionId, level, chunkPos, chunkInfo));
            }
        }
        
        // 排序：suspend从高到低，restore从低到高
        if (isSuspend) {
            candidates.sort((a, b) -> Integer.compare(b.chunkInfo.blockEntityCount(), a.chunkInfo.blockEntityCount()));
        } else {
            candidates.sort((a, b) -> Integer.compare(a.chunkInfo.blockEntityCount(), b.chunkInfo.blockEntityCount()));
        }
        
        // 执行操作
        int processed = 0;
        for (ChunkSuspensionCandidate candidate : candidates) {
            if (processed >= count) break;
            
            try {
                if (isSuspend) {
                    // 暂停操作
                    removeManagedTicket(candidate.chunkPos, candidate.level);
                    suspendedChunks.computeIfAbsent(candidate.dimensionId, k -> new ConcurrentHashMap<>())
                        .put(candidate.chunkPos, candidate.chunkInfo);
                } else {
                    // 恢复操作
                    Map<ChunkPos, ChunkInfo> dimensionSuspendedChunks = suspendedChunks.get(candidate.dimensionId);
                    if (dimensionSuspendedChunks != null) {
                        dimensionSuspendedChunks.remove(candidate.chunkPos);
                        if (dimensionSuspendedChunks.isEmpty()) {
                            suspendedChunks.remove(candidate.dimensionId);
                        }
                    }
                    addManagedTicket(candidate.chunkPos, candidate.level, candidate.chunkInfo.blockEntityCount());
                }
                processed++;
            } catch (Exception e) {
                String action = isSuspend ? "suspend" : "restore";
                Recyclingservice.LOGGER.debug("Failed to {} chunk ({}, {})", 
                    action, candidate.chunkPos.x, candidate.chunkPos.z, e);
            }
        }
        
        if (processed > 0) {
            String message = isSuspend ? "Suspended {} chunks due to server performance" : "Restored {} chunks due to improved server performance";
            Recyclingservice.LOGGER.info(message, processed);
        }
        
        return processed;
    }
    
    /**
     * 暂停管理的区块
     */
    public static int suspendManagedChunks(MinecraftServer server, int count) {
        return suspendOrRestoreChunks(server, count, true);
    }
    
    /**
     * 恢复暂停的区块
     */
    public static int restoreSuspendedChunks(MinecraftServer server, int count) {
        return suspendOrRestoreChunks(server, count, false);
    }
    public static void performDynamicChunkManagement(MinecraftServer server) {
        if (!Config.TECHNICAL.enableDynamicChunkManagement.get()) {
            return;
        }
        
        try {
            double averageTickTime = getAverageTickTime(server);
            double tps = Math.min(20.0, 1000.0 / averageTickTime);
            
            double tpsThreshold = Config.TECHNICAL.tpsThreshold.get();
            double msptSuspendThreshold = Config.TECHNICAL.msptThresholdSuspend.get();
            double msptRestoreThreshold = Config.TECHNICAL.msptThresholdRestore.get();
            int operationCount = Config.TECHNICAL.chunkOperationCount.get();
            
            boolean shouldSuspend = tps < tpsThreshold || averageTickTime > msptSuspendThreshold;
            boolean canRestore = averageTickTime < msptRestoreThreshold;
            
            if (shouldSuspend && !managedChunks.isEmpty()) {
                int suspended = suspendManagedChunks(server, operationCount);
                if (suspended > 0) {
                    Recyclingservice.LOGGER.info(
                        "Server performance degraded (TPS: {}, MSPT: {}), suspended {} chunks",
                        String.format("%.2f", tps), String.format("%.2f", averageTickTime), suspended);
                }
            } else if (canRestore && !suspendedChunks.isEmpty()) {
                int restored = restoreSuspendedChunks(server, operationCount);
                if (restored > 0) {
                    Recyclingservice.LOGGER.info(
                        "Server performance improved (MSPT: {}), restored {} chunks",
                        String.format("%.2f", averageTickTime), restored);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform dynamic chunk management", e);
        }
    }
    
    /**
     * 获取服务器平均tick时间（MSPT）
     * @param server 服务器实例
     * @return 平均tick时间（毫秒）
     */
    private static double getAverageTickTime(MinecraftServer server) {
        try {
            // 获取主世界的tick时间数据
            ResourceLocation overworldKey = ResourceLocation.parse("minecraft:overworld");
            long[] recentTicks = server.getTickTime(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, overworldKey));
            
            if (recentTicks == null || recentTicks.length == 0) {
                return 50.0; // 默认值
            }
            
            // 计算最近几个tick的平均时间
            long sum = 0;
            int count = 0;
            for (int i = recentTicks.length - 1; i >= Math.max(0, recentTicks.length - 20); i--) {
                if (recentTicks[i] > 0) {
                    sum += recentTicks[i];
                    count++;
                }
            }
            
            if (count == 0) {
                return 50.0; // 默认值
            }
            
            // 转换纳秒到毫秒
            return (sum / count) / 1_000_000.0;
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to get server tick time", e);
            return 50.0; // 默认值
        }
    }
    
    /**
     * 批量冻结影响目标区块的所有加载器
     * @param targetChunk 物品过多的目标区块
     * @param level 服务器世界
     * @return 冻结结果信息
     */
    public static FreezeResult freezeAllAffectingChunkLoaders(ChunkPos targetChunk, ServerLevel level) {
        try {
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            
            int searchRadius = Config.TECHNICAL.chunkFreezingSearchRadius.get();
            int maxTicketsToCheck = getMaxTicketsToCheck();
            
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
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to freeze affecting chunk loaders", e);
            return new FreezeResult(List.of(), 0);
        }
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
                if (Config.TECHNICAL.enableChunkItemWarning.get()) {
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
     * 执行服务器启动时的区块清理 - 根据方块实体数量移除不必要的强加载
     * @param server 服务器实例
     */
    public static void performStartupChunkCleanup(MinecraftServer server) {
        if (!Config.TECHNICAL.enableStartupChunkCleanup.get()) {
            return;
        }
        
        int threshold = Config.TECHNICAL.startupChunkEntityThreshold.get();
        int totalFrozenChunks = 0;
        int totalFrozenTickets = 0;
        
        Recyclingservice.LOGGER.info("Starting startup chunk cleanup with block entity threshold: {}", threshold);
        
        try {
            // 遍历所有维度
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimensionId = level.dimension().location();
                
                try {
                    DistanceManager distanceManager = level.getChunkSource().distanceManager;
                    Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
                    
                    int dimensionProcessedChunks = 0;
                    int dimensionFrozenTickets = 0;
                    int dimensionManagedChunks = 0;
                    
                    // 遍历所有有tickets的区块
                    for (var entry : tickets.long2ObjectEntrySet()) {
                        long chunkKey = entry.getLongKey();
                        ChunkPos chunkPos = new ChunkPos(chunkKey);
                        SortedArraySet<Ticket<?>> chunkTickets = entry.getValue();
                        
                        if (chunkTickets == null || chunkTickets.isEmpty()) {
                            continue;
                        }
                        
                        // 检查是否有非白名单tickets
                        boolean hasNonWhitelistTickets = chunkTickets.stream()
                            .anyMatch(ticket -> !WHITELIST_TICKET_TYPES.contains(ticket.getType()));
                        
                        if (!hasNonWhitelistTickets) {
                            continue;
                        }
                        
                        try {
                            // 检查区块是否已加载
                            var chunkAccess = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
                            if (chunkAccess == null) {
                                continue;
                            }
                            
                            // 获取方块实体数量
                            int blockEntityCount = chunkAccess.getBlockEntities().size();
                            
                            // 先移除所有非白名单tickets
                            int frozenTickets = freezeChunk(chunkPos, level);
                            if (frozenTickets > 0) {
                                dimensionProcessedChunks++;
                                dimensionFrozenTickets += frozenTickets;
                                
                                // 如果方块实体数量达到阈值，添加我们的管理ticket
                                if (blockEntityCount >= threshold) {
                                    addManagedTicket(chunkPos, level, blockEntityCount);
                                    dimensionManagedChunks++;
                                    
                                    Recyclingservice.LOGGER.debug(
                                        "Startup cleanup: Replaced tickets for chunk ({}, {}) in {} with {} block entities (now managed)",
                                        chunkPos.x, chunkPos.z, dimensionId, blockEntityCount);
                                } else {
                                    Recyclingservice.LOGGER.debug(
                                        "Startup cleanup: Frozen chunk ({}, {}) in {} with {} block entities, removed {} tickets",
                                        chunkPos.x, chunkPos.z, dimensionId, blockEntityCount, frozenTickets);
                                }
                            }
                        } catch (Exception e) {
                            // 单个区块处理失败，继续处理下一个
                            Recyclingservice.LOGGER.debug(
                                "Failed to process chunk ({}, {}) in dimension {} during startup cleanup", 
                                chunkPos.x, chunkPos.z, dimensionId, e);
                        }
                    }
                    
                    if (dimensionProcessedChunks > 0) {
                        Recyclingservice.LOGGER.info(
                            "Startup cleanup completed for dimension {}: {} chunks processed, {} tickets removed, {} chunks now managed",
                            dimensionId, dimensionProcessedChunks, dimensionFrozenTickets, dimensionManagedChunks);
                        
                        totalFrozenChunks += dimensionProcessedChunks;
                        totalFrozenTickets += dimensionFrozenTickets;
                    }
                    
                } catch (Exception e) {
                    // 单个维度处理失败，不影响其他维度
                    Recyclingservice.LOGGER.debug(
                        "Failed to process dimension {} during startup cleanup", dimensionId, e);
                }
            }
            
            if (totalFrozenChunks > 0) {
                int totalManagedChunks = managedChunks.values().stream()
                    .mapToInt(Map::size)
                    .sum();
                
                Recyclingservice.LOGGER.info(
                    "Startup chunk cleanup completed: {} chunks processed across all dimensions, {} total tickets removed, {} chunks now managed by mod",
                    totalFrozenChunks, totalFrozenTickets, totalManagedChunks);
            } else {
                Recyclingservice.LOGGER.info("Startup chunk cleanup completed: No chunks needed to be processed");
            }
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to perform startup chunk cleanup", e);
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
    
    /**
     * 区块信息记录类
     * 用于跟踪管理的区块的详细信息
     */
    public record ChunkInfo(ChunkPos chunkPos, int blockEntityCount, long addedTime) {
        public ChunkInfo(ChunkPos chunkPos, int blockEntityCount) {
            this(chunkPos, blockEntityCount, System.currentTimeMillis());
        }
    }
    
    /**
     * 区块暂停候选者记录类
     * 用于暂停/恢复操作的排序和处理
     */
    private record ChunkSuspensionCandidate(ResourceLocation dimensionId, ServerLevel level, ChunkPos chunkPos, ChunkInfo chunkInfo) {
    }
}