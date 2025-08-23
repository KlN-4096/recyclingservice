package com.klnon.recyclingservice.util.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class ChunkScanner {
    /**
     * 流式处理：在玩家周围分批查找实体
     * @param level 服务器维度
     * @param player 玩家
     * @param chunkRadius 玩家范围半径
     * @param type 实体类型
     * @param processor 实体处理器（边扫描边处理）
     * @param batchSize 批次大小
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void findAroundPlayerStream(ServerLevel level, ServerPlayer player, 
                                                                int chunkRadius, EntityType<T> type,
                                                                Consumer<List<T>> processor, int batchSize) {
        ChunkPos center = player.chunkPosition();
        List<T> currentBatch = new ArrayList<>();
        
        for (int x = -chunkRadius; x <= chunkRadius; x++) {
            for (int z = -chunkRadius; z <= chunkRadius; z++) {
                ChunkPos pos = new ChunkPos(center.x + x, center.z + z);
                if (!level.hasChunk(pos.x, pos.z)) continue;
                
                AABB bounds = new AABB(
                    pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                    pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1
                );
                
                List<Entity> entities = level.getEntities((Entity) null, bounds, 
                    entity -> entity.getType() == type);
                
                for (Entity entity : entities) {
                    currentBatch.add((T) entity);
                    
                    // 达到批次大小，立即处理并清空
                    if (currentBatch.size() >= batchSize) {
                        processor.accept(new ArrayList<>(currentBatch));
                        currentBatch.clear();
                    }
                }
            }
        }
        
        // 处理最后一批
        if (!currentBatch.isEmpty()) {
            processor.accept(currentBatch);
        }
    }
    

    /**
     * 流式处理：在强加载区块中分批查找实体
     * @param level 服务器维度
     * @param type 实体类型
     * @param processor 实体处理器（边扫描边处理）
     * @param batchSize 批次大小
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void findInForcedChunksStream(ServerLevel level, EntityType<T> type,
                                                                  Consumer<List<T>> processor, int batchSize) {
        List<T> currentBatch = new ArrayList<>();
        
        for (long chunkPos : level.getForcedChunks()) {
            ChunkPos pos = new ChunkPos(chunkPos);
            AABB bounds = new AABB(
                pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1
            );
            
            List<Entity> entities = level.getEntities((Entity) null, bounds, 
                entity -> entity.getType() == type);
            
            for (Entity entity : entities) {
                currentBatch.add((T) entity);
                
                // 达到批次大小，立即处理并清空
                if (currentBatch.size() >= batchSize) {
                    processor.accept(new ArrayList<>(currentBatch));
                    currentBatch.clear();
                }
            }
        }
        
        // 处理最后一批
        if (!currentBatch.isEmpty()) {
            processor.accept(currentBatch);
        }
    }
}
