package com.klnon.recyclingservice.content.cleanup.entity;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import com.klnon.recyclingservice.Config;

/**
 * 物品和实体过滤器 - 专注于清理判断
 */
public class EntityFilter {

    /**
     * 检查ItemEntity是否应该被清理（支持Create模组处理检测）
     * @param entity 掉落物实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(ItemEntity entity) {
        // 检查是否启用了Create模组保护且物品正在被处理
        if (Config.GAMEPLAY.protectCreateProcessingItems.get() && isBeingProcessedByCreate(entity)) {
            return false; // 保护正在处理中的物品
        }
        
        if (entity.getItem().isEmpty()) {
            return false;
        }
        
        String itemId = BuiltInRegistries.ITEM.getKey(entity.getItem().getItem()).toString();
        return Config.isWhitelistMode() 
            ? !Config.whitelistCache.contains(itemId)  // 白名单模式：不在保留列表中的都清理
            : Config.blacklistCache.contains(itemId);  // 黑名单模式：只清理黑名单中的
    }


    /**
     * 检查弹射物是否应该被清理
     * @param entity 弹射物实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanProjectile(Entity entity) {
        return Config.GAMEPLAY.cleanProjectiles.get() && 
               Config.projectileTypesCache.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    /**
     * 检查物品是否正在被Create模组处理
     * 基于Create模组源码逻辑：检查PersistentData中的Processing.Time >= 0
     * @param entity 掉落物实体
     * @return 是否正在被Create模组处理
     */
    private static boolean isBeingProcessedByCreate(ItemEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        
        // 检查是否包含CreateData
        if (!persistentData.contains("CreateData")) {
            return false;
        }
        
        CompoundTag createData = persistentData.getCompound("CreateData");
        if (!createData.contains("Processing")) {
            return false;
        }
        
        CompoundTag processing = createData.getCompound("Processing");
        // Time >= 0 表示正在处理中，Time = -1 表示处理已完成或失败
        return processing.getInt("Time") >= 0;
    }

}