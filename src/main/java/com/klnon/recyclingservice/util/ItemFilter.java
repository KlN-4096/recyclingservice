package com.klnon.recyclingservice.util;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.klnon.recyclingservice.Config;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 物品和实体过滤器 - 专注于清理判断
 * 遵循KISS原则：简单的过滤逻辑，统一所有过滤方法
 */
public class ItemFilter {
    
    /**
     * 检查物品ID是否应该被清理（核心方法）
     * @param itemId 物品资源ID
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(String itemId) {
        // 检查白名单
        if (Config.NEVER_CLEAN_ITEMS.get().contains(itemId)) {
            return false;
        }
        
        // 如果启用了"仅清理指定物品"模式
        if (Config.ONLY_CLEAN_LISTED_ITEMS.get()) {
            return Config.ALWAYS_CLEAN_ITEMS.get().contains(itemId);
        }
        
        // 默认：清理所有不在白名单中的物品
        return true;
    }
    
    /**
     * 检查物品堆是否应该被清理
     * @param itemStack 物品堆
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        
        String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        return shouldCleanItem(itemId);
    }

        /**
     * 过滤掉落物实体，返回应该被清理的物品内容
     * @param itemEntities 掉落物实体列表
     * @return 应该被清理的物品堆列表（用于放入垃圾箱）
     */
    public static List<ItemStack> filterItems(List<ItemEntity> itemEntities) {
        return itemEntities.stream()
                .filter(entity -> shouldCleanItem(entity.getItem()))
                .map(ItemEntity::getItem)
                .map(ItemStack::copy)
                .collect(Collectors.toList());
    }
    
        /**
     * 过滤弹射物实体，返回应该被清理的
     * @param projectiles 弹射物实体列表
     * @return 应该被清理的弹射物实体列表（用于直接删除）
     */
    public static List<Entity> filterProjectiles(List<Entity> projectiles) {
        return projectiles.stream()
                .filter(ItemFilter::shouldCleanProjectile)
                .collect(Collectors.toList());
    }

    /**
     * 检查弹射物是否应该被清理
     * @param entity 弹射物实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanProjectile(Entity entity) {
        if (!Config.shouldCleanProjectiles()) {
            return false;
        }
        
        String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return Config.shouldCleanEntityType(entityTypeId);
    }
    
    

}