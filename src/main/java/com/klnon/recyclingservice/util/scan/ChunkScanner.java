package com.klnon.recyclingservice.util.scan;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.util.other.ErrorHandler;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
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
     * @param entityTypes 实体类型集合
     * @param processor 分类后的实体处理器
     * @param batchSize 批次大小
     */
    public static void findAroundPlayerStream(ServerLevel level, ServerPlayer player,
                                              int chunkRadius, Set<EntityType<?>> entityTypes,
                                              Consumer<EntityBatch> processor, int batchSize) {
        ChunkPos center = player.chunkPosition();
        EntityBatch currentBatch = new EntityBatch();

        //许多区块一起的大AABB,因为是玩家周围,一体的
        AABB bounds = new AABB(
                (center.x - chunkRadius) * 16, level.getMinBuildHeight(),
                (center.z - chunkRadius) * 16,
                (center.x + chunkRadius + 1) * 16, level.getMaxBuildHeight(),
                (center.z + chunkRadius + 1) * 16
        );

        processEntitiesInBounds(level, bounds, entityTypes, currentBatch, processor, batchSize);

        if (!currentBatch.isEmpty()) {
            processor.accept(currentBatch);
        }
    }

    /**
     * 流式处理：在所有加载区块中分批查找实体（包括强加载和弱加载）
     * 使用票据级别判断区块是否加载
     * @param level 服务器维度
     * @param entityTypes 实体类型集合
     * @param processor 分类后的实体处理器
     * @param batchSize 批次大小
     */
    public static void findInForcedChunksStream(ServerLevel level, Set<EntityType<?>> entityTypes,
                                            Consumer<EntityBatch> processor, int batchSize) {
        EntityBatch currentBatch = new EntityBatch();
        //这里用不了errorhandler
        try {
            Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap = getChunkHolder(level);

            for (ChunkHolder chunkHolder : visibleChunkMap.values()) {
                int ticketLevel = chunkHolder.getTicketLevel();
                
                // 根据票据级别判断是否为加载状态(包含强弱)
                // 使用配置的票据级别阈值：force模式=31，lazy模式=32
                if (ticketLevel <= Config.getTicketLevelThreshold()) {
                    ChunkPos pos = chunkHolder.getPos();
                    AABB bounds = new AABB(
                        pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                        pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1
                    );
                    
                    processEntitiesInBounds(level, bounds, entityTypes, currentBatch, processor, batchSize);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Error in scanAllLoadedChunks", e);
        }

            // 处理最后一批 
        if (!currentBatch.isEmpty()) {
            processor.accept(currentBatch);
        }
    }

    private static Long2ObjectLinkedOpenHashMap<ChunkHolder> getChunkHolder(ServerLevel level) throws NoSuchFieldException, IllegalAccessException {
        ServerChunkCache chunkSource = level.getChunkSource();
        ChunkMap chunkMap = chunkSource.chunkMap;

        // 使用反射访问 visibleChunkMap
        Field visibleChunkMapField = ChunkMap.class.getDeclaredField("visibleChunkMap");
        visibleChunkMapField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap =
            (Long2ObjectLinkedOpenHashMap<ChunkHolder>) visibleChunkMapField.get(chunkMap);
        return visibleChunkMap;
    }

    /**
     * 在指定边界内处理实体（增量添加到现有批次）
     */
    private static void processEntitiesInBounds(ServerLevel level, AABB bounds,
                                                Set<EntityType<?>> entityTypes, EntityBatch currentBatch,
                                                Consumer<EntityBatch> processor, int batchSize) {
        level.getEntities((Entity) null, bounds, entity -> {
            if (entityTypes.contains(entity.getType())) {
                currentBatch.addEntity(entity);

                if (currentBatch.size() >= batchSize) {
                    processor.accept(currentBatch.copy());
                    currentBatch.clear();
                }
            }
            return false;
        });
    }

    /**
     * 实体批次类 - 按类型分类存储实体
     */
    public static class EntityBatch {
        private final List<net.minecraft.world.entity.item.ItemEntity> items = new ArrayList<>();
        private final List<Entity> projectiles = new ArrayList<>();

        public void addEntity(Entity entity) {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                items.add(itemEntity);
            } else {
                projectiles.add(entity);
            }
        }

        public List<net.minecraft.world.entity.item.ItemEntity> getItems() {
            return items;
        }

        public List<Entity> getProjectiles() {
            return projectiles;
        }

        public int size() {
            return items.size() + projectiles.size();
        }

        public boolean isEmpty() {
            return items.isEmpty() && projectiles.isEmpty();
        }

        public void clear() {
            items.clear();
            projectiles.clear();
        }

        public EntityBatch copy() {
            EntityBatch copy = new EntityBatch();
            copy.items.addAll(this.items);
            copy.projectiles.addAll(this.projectiles);
            return copy;
        }
    }
}