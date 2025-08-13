package com.klnon.recyclingservice.util;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import com.klnon.recyclingservice.Config;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 物品过滤器 - 根据配置决定哪些物品应该被清理
 * 遵循KISS原则：简单的过滤逻辑，基于配置文件
 */
public class ItemFilter {
    
    /**
     * 检查单个物品是否应该被清理
     * @param itemStack 物品堆
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }
        
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        String itemIdString = itemId.toString();
        
        // 检查永不清理列表
        if (Config.NEVER_CLEAN_ITEMS.get().contains(itemIdString)) {
            return false;
        }
        
        // 如果启用了"仅清理指定物品"模式
        if (Config.ONLY_CLEAN_LISTED_ITEMS.get()) {
            return Config.ALWAYS_CLEAN_ITEMS.get().contains(itemIdString);
        }
        
        // 默认模式：清理所有不在永不清理列表中的物品
        return true;
    }
    
    /**
     * 检查掉落物实体是否应该被清理
     * @param itemEntity 掉落物实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanItemEntity(ItemEntity itemEntity) {
        return shouldCleanItem(itemEntity.getItem());
    }
    
    /**
     * 过滤掉落物实体列表，返回应该被清理的物品
     * @param itemEntities 掉落物实体列表
     * @return 应该被清理的掉落物实体列表
     */
    public static List<ItemEntity> filterItemEntities(List<ItemEntity> itemEntities) {
        return itemEntities.stream()
                .filter(ItemFilter::shouldCleanItemEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 过滤物品堆列表，返回应该被清理的物品
     * @param itemStacks 物品堆列表
     * @return 应该被清理的物品堆列表
     */
    public static List<ItemStack> filterItemStacks(List<ItemStack> itemStacks) {
        return itemStacks.stream()
                .filter(ItemFilter::shouldCleanItem)
                .collect(Collectors.toList());
    }
    
    /**
     * 将掉落物实体转换为物品堆并过滤
     * @param itemEntities 掉落物实体列表
     * @return 过滤后的物品堆列表
     */
    public static List<ItemStack> convertAndFilter(List<ItemEntity> itemEntities) {
        return itemEntities.stream()
                .filter(ItemFilter::shouldCleanItemEntity)
                .map(ItemEntity::getItem)
                .map(ItemStack::copy)  // 创建副本避免修改原物品
                .collect(Collectors.toList());
    }
    
    /**
     * 统计应该被清理的物品数量
     * @param itemEntities 掉落物实体列表
     * @return 应该被清理的数量
     */
    public static int countCleanableItems(List<ItemEntity> itemEntities) {
        return (int) itemEntities.stream()
                .filter(ItemFilter::shouldCleanItemEntity)
                .count();
    }
    
    /**
     * 检查物品ID是否在永不清理列表中
     * @param itemId 物品ID字符串
     * @return 是否在永不清理列表中
     */
    public static boolean isNeverCleanItem(String itemId) {
        return Config.NEVER_CLEAN_ITEMS.get().contains(itemId);
    }
    
    /**
     * 检查物品ID是否在总是清理列表中
     * @param itemId 物品ID字符串
     * @return 是否在总是清理列表中
     */
    public static boolean isAlwaysCleanItem(String itemId) {
        return Config.ALWAYS_CLEAN_ITEMS.get().contains(itemId);
    }
    
    /**
     * 获取过滤规则的描述信息
     * @return 规则描述
     */
    public static String getFilterRuleDescription() {
        if (Config.ONLY_CLEAN_LISTED_ITEMS.get()) {
            return "只清理指定物品模式 - 仅清理 " + Config.ALWAYS_CLEAN_ITEMS.get().size() + " 种指定物品";
        } else {
            return "清理所有物品模式 - 除了 " + Config.NEVER_CLEAN_ITEMS.get().size() + " 种保护物品";
        }
    }
}