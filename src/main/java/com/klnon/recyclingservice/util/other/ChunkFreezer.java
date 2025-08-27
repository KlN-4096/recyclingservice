package com.klnon.recyclingservice.util.other;

import java.lang.reflect.Field;
import java.util.*;

import com.klnon.recyclingservice.Recyclingservice;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/**
 * 区块冻结工具类
 * 通过移除强加载区块的强制加载状态来实现"冻结"效果
 * 依赖MC原生区块管理系统自动处理卸载
 */
public class ChunkFreezer {
    
    // 4方向连通搜索（不包括对角线）
    private static final int[][] CONNECTED_OFFSETS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    
    // 性能限制参数
    private static final int MAX_SEARCH_RADIUS = 5;      // 最大搜索半径
    private static final int MAX_FREEZE_CHUNKS = 100;     // 最大冻结区块数
    
    /**
     * 冻结连通的强加载区块
     * 
     * @param startPos 起始区块位置
     * @param level 服务器世界
     * @param chunkMap 区块持有者映射
     * @return 成功冻结的区块数量
     */
    public static int freezeConnectedChunks(ChunkPos startPos, ServerLevel level, 
                                           Long2ObjectLinkedOpenHashMap<ChunkHolder> chunkMap) {
        return ErrorHandler.handleOperation(null, "freezeConnectedChunks", () -> {
            Set<ChunkPos> processed = new HashSet<>();
            Queue<ChunkPos> toProcess = new ArrayDeque<>();
            int frozenCount = 0;
            
            toProcess.offer(startPos);
            processed.add(startPos);
            
            while (!toProcess.isEmpty() && frozenCount < MAX_FREEZE_CHUNKS) {
                ChunkPos current = toProcess.poll();
                
                // 尝试降级当前区块
                if (downgradeChunkIfForceLoaded(level, current, chunkMap)) {
                    frozenCount++;
                    
                    // 搜索相连区块
                    for (int[] offset : CONNECTED_OFFSETS) {
                        ChunkPos neighbor = new ChunkPos(current.x + offset[0], current.z + offset[1]);
                        
                        if (!processed.contains(neighbor) && isWithinSearchRadius(startPos, neighbor)) {
                            processed.add(neighbor);
                            toProcess.offer(neighbor);
                        }
                    }
                }
            }
            
            if (frozenCount > 0) {
                Recyclingservice.LOGGER.info("Frozen {} connected chunks starting from ({}, {})", 
                    frozenCount, startPos.x, startPos.z);
            }
            
            return frozenCount;
        }, 0);
    }
    
    /**
     * 如果是强加载区块，则降级其ticket level
     * 
     * @param level 服务器世界
     * @param pos 区块位置
     * @param chunkMap 区块持有者映射
     * @return 是否成功降级
     */
    private static boolean downgradeChunkIfForceLoaded(ServerLevel level, ChunkPos pos, 
                                                      Long2ObjectLinkedOpenHashMap<ChunkHolder> chunkMap) {
        long posKey = ChunkPos.asLong(pos.x, pos.z);
        ChunkHolder holder = chunkMap.get(posKey);
        
        // 检查是否为强加载区块（ticket level <= 31）
        if (holder != null && holder.getTicketLevel() <= 31) {
            Recyclingservice.LOGGER.debug(holder.getTicketLevel()+"降级前");
            boolean a= downgradeChunkTicket(level, pos);
            Recyclingservice.LOGGER.debug(holder.getTicketLevel()+"降级后");
            return a;
        }
        
        return false;
    }
    
    /**
     * 移除区块的强制加载状态
     * 
     * @param level 服务器世界
     * @param pos 区块位置
     * @return 是否成功移除强制加载
     */
    private static boolean downgradeChunkTicket(ServerLevel level, ChunkPos pos) {
        try {
            boolean wasForced = level.setChunkForced(pos.x, pos.z, false);
            
            if (wasForced) {
                Recyclingservice.LOGGER.debug("Removed force loading for chunk ({}, {})", pos.x, pos.z);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.warn("Failed to remove force loading for chunk ({}, {}): {}", 
                pos.x, pos.z, e.getMessage());
            return false;
        }
    }
    
    /**
     * 检查区块是否在搜索半径内
     * 
     * @param start 起始位置
     * @param target 目标位置
     * @return 是否在搜索半径内
     */
    private static boolean isWithinSearchRadius(ChunkPos start, ChunkPos target) {
        return Math.abs(start.x - target.x) <= MAX_SEARCH_RADIUS && 
               Math.abs(start.z - target.z) <= MAX_SEARCH_RADIUS;
    }
    
    /**
     * 获取区块持有者映射（供外部调用）
     * 必须使用原生try-catch，因为反射操作需要精确的异常处理
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