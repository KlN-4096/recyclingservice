package com.klnon.recyclingservice.util.Item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class ChunkScanner {
    /**
     * 在所有强加载区块中查找实体
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> List<T> findInForcedChunks(ServerLevel level, EntityType<T> type) {
        List<T> result = new ArrayList<>();
        
        for (long chunkPos : level.getForcedChunks()) {
            ChunkPos pos = new ChunkPos(chunkPos);
            AABB bounds = new AABB(
                pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1
            );
            
            List<Entity> entities = level.getEntities((Entity) null, bounds, 
                entity -> entity.getType() == type);
            
            for (Entity entity : entities) {
                result.add((T) entity);
            }
        }
        
        return result;
    }
    
    /**
     * 在玩家周围查找实体
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> List<T> findAroundPlayer(ServerLevel level, ServerPlayer player, 
                                                              int chunkRadius, EntityType<T> type) {
        List<T> result = new ArrayList<>();
        ChunkPos center = player.chunkPosition();
        
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
                    result.add((T) entity);
                }
            }
        }
        
        return result;
    }
}
