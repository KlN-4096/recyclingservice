package com.klnon.recyclingservice.util.cleanup;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import com.klnon.recyclingservice.Config;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 物品和实体过滤器 - 专注于清理判断
 */
public class ItemFilter {
    /**
     * 检查ItemEntity是否应该被清理（支持Create模组处理检测）
     * @param entity 掉落物实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(ItemEntity entity) {
        // 检查是否启用了Create模组保护且物品正在被处理
        if (Config.PROTECT_CREATE_PROCESSING_ITEMS.get() && isBeingProcessedByCreate(entity)) {
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
     * 过滤掉落物实体，同时返回物品和实体（避免重复过滤）
     * @param itemEntities 掉落物实体列表
     * @return 过滤结果，包含ItemStack列表和Entity列表
     */
    public static FilterResult<ItemEntity> filterItemEntities(List<ItemEntity> itemEntities) {
        List<ItemEntity> validEntities = itemEntities.stream()
                .filter(ItemFilter::shouldCleanItem)
                .collect(Collectors.toList());
        
        List<ItemStack> itemStacks = validEntities.stream()
                .map(ItemEntity::getItem)
                .collect(Collectors.toList());
                
        return new FilterResult<>(itemStacks, validEntities);
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
        return Config.CLEAN_PROJECTILES.get() && 
               Config.projectileTypesCache.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
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

    /**
     * 过滤结果类 - 同时包含ItemStack和Entity，避免重复过滤
     */
    public record FilterResult<T extends Entity>(List<ItemStack> itemStacks, List<T> entities) {
    }
}