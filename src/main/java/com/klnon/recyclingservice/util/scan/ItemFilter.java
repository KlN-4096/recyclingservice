package com.klnon.recyclingservice.util.scan;

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
     * 检查物品是否应该被清理（核心方法）
     * @param itemStack 物品堆
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        
        String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        return Config.isWhitelistMode() 
            ? !Config.isInWhitelist(itemId)  // 白名单模式：不在保留列表中的都清理
            : Config.isInBlacklist(itemId);  // 黑名单模式：只清理黑名单中的
    }

    /**
     * 过滤掉落物实体，返回应该被清理的物品引用（零拷贝优化）
     * @param itemEntities 掉落物实体列表
     * @return 应该被清理的物品堆引用列表
     */
    public static List<ItemStack> filterItems(List<ItemEntity> itemEntities) {
        return itemEntities.stream()
                .map(ItemEntity::getItem)
                .filter(ItemFilter::shouldCleanItem) // 直接引用，不拷贝
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
        return Config.shouldCleanProjectiles() && 
               Config.isProjectileTypeToClean(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }
    
    /**
     * 检查物品是否为复杂物品（有附魔、特殊命名、堆叠数为1等）
     * @param itemStack 物品堆
     * @return 是否为复杂物品
     */
    public static boolean isComplexItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        // 检查基本属性
        if (itemStack.getMaxStackSize() == 1 || 
            itemStack.isEnchanted() || 
            itemStack.isDamaged()) {
            return true;
        }
        
        // 检查是否有特殊组件 - 合并重复检查
        return hasAnySpecialComponent(itemStack);
    }

    /**
     * 检查物品是否包含任何特殊组件
     * @param itemStack 物品堆
     * @return 是否有特殊组件
     */
    private static boolean hasAnySpecialComponent(ItemStack itemStack) {
        // 定义需要检查的特殊组件类型
        net.minecraft.core.component.DataComponentType<?>[] specialComponents = {
            DataComponents.CUSTOM_NAME,
            DataComponents.CUSTOM_DATA,
            DataComponents.WRITTEN_BOOK_CONTENT,
            DataComponents.WRITABLE_BOOK_CONTENT,
            DataComponents.POTION_CONTENTS,
            DataComponents.FIREWORK_EXPLOSION,
            DataComponents.FIREWORKS,
            DataComponents.CONTAINER,
            DataComponents.STORED_ENCHANTMENTS,
            DataComponents.SUSPICIOUS_STEW_EFFECTS,
            DataComponents.TRIM,
            DataComponents.DYED_COLOR,
            DataComponents.BANNER_PATTERNS,
            DataComponents.MAP_ID
        };
        
        // 使用流式操作检查是否包含任何特殊组件
        return java.util.Arrays.stream(specialComponents)
                .anyMatch(itemStack::has);
    }
}