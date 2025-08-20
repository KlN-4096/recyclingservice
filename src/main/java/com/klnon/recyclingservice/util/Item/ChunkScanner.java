package com.klnon.recyclingservice.util.Item;

import java.util.ArrayList;
import java.util.List;

import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue.Consumer;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class ChunkScanner {
    /**
     * 在所有强加载区块中搜索指定类型的实体
     * @param level 服务器世界
     * @param entityType 实体类型
     * @param consumer 处理找到实体的函数
     */
    public static <T extends Entity> void processEntitiesInForcedChunks(
            ServerLevel level, 
            EntityType<T> entityType, 
            Consumer<T> consumer) {
        
        LongSet forcedChunks = level.getForcedChunks();
        if (forcedChunks.isEmpty()) {
            return;
        }
        
        for (long chunkPos : forcedChunks) {
            processEntitiesInChunk(level, new ChunkPos(chunkPos), entityType, consumer);
        }
    }
    
    /**
     * 在单个区块中搜索指定类型的实体
     * @param level 服务器世界
     * @param chunkPos 区块位置
     * @param entityType 实体类型
     * @param consumer 处理找到实体的函数
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void processEntitiesInChunk(
            ServerLevel level, 
            ChunkPos chunkPos, 
            EntityType<T> entityType, 
            Consumer<T> consumer) {
        
        AABB chunkBounds = new AABB(
            chunkPos.getMinBlockX(), 
            level.getMinBuildHeight(), 
            chunkPos.getMinBlockZ(),
            chunkPos.getMaxBlockX() + 1, 
            level.getMaxBuildHeight(), 
            chunkPos.getMaxBlockZ() + 1
        );
        
        // 使用正确的方法签名：getEntities(Entity, AABB, Predicate)
        level.getEntities((Entity) null, chunkBounds, entity -> {
            if (entity.getType() == entityType) {
                consumer.accept((T) entity);
            }
            return false; // 继续搜索
        });
    }
    
    /**
     * 收集所有强加载区块中的指定类型实体到列表
     * @param level 服务器世界
     * @param entityType 实体类型
     * @return 实体列表
     */
    public static <T extends Entity> List<T> collectEntitiesInForcedChunks(
            ServerLevel level, 
            EntityType<T> entityType) {
        
        List<T> entities = new ArrayList<>();
        processEntitiesInForcedChunks(level, entityType, entities::add);
        return entities;
    }
}
