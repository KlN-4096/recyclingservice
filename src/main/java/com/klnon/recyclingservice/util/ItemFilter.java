package com.klnon.recyclingservice.util;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
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
    
    /**
     * 检查物品是否为复杂物品（有附魔、特殊命名、堆叠数为1等）
     * @param itemStack 物品堆
     * @return 是否为复杂物品
     */
    public static boolean isComplexItem(ItemStack itemStack) {
        // 检查最大堆叠数是否为1
        if (itemStack.getMaxStackSize() == 1) {
            return true;
        }

        // 检查是否有附魔
        if (itemStack.isEnchanted()) {
            return true;
        }
        
        // 检查是否有自定义名称（在1.21中使用DataComponents）
        if (itemStack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            return true;
        }

        // 检查是否有损坏（耐久度不满）
        if (itemStack.isDamaged()) {
            return true;
        }
        
        // 检查是否有特殊组件
        if (itemStack.has(DataComponents.CUSTOM_NAME) ||
            itemStack.has(DataComponents.CUSTOM_DATA) ||
            itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT) ||
            itemStack.has(DataComponents.WRITABLE_BOOK_CONTENT) ||  // 书与笔
            itemStack.has(DataComponents.POTION_CONTENTS) ||
            itemStack.has(DataComponents.FIREWORK_EXPLOSION) ||
            itemStack.has(DataComponents.FIREWORKS) ||             // 烟花火箭
            itemStack.has(DataComponents.CONTAINER) ||
            itemStack.has(DataComponents.STORED_ENCHANTMENTS) ||   // 附魔书
            itemStack.has(DataComponents.SUSPICIOUS_STEW_EFFECTS) || // 迷之炖菜
            itemStack.has(DataComponents.TRIM) ||                  // 盔甲纹饰
            itemStack.has(DataComponents.DYED_COLOR) ||            // 染色物品
            itemStack.has(DataComponents.BANNER_PATTERNS) ||       // 旗帜图案
            itemStack.has(DataComponents.MAP_ID)) {                // 地图
            return true;
        }
        
        if (itemStack.isEmpty()) {
            return false;
        }
        
        return false;
    }

}