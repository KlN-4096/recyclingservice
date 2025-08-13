package com.klnon.recyclingservice.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 物品扫描器 - 扫描所有维度的掉落物并按维度分类
 * 遵循KISS原则：简单直接的扫描逻辑
 */
public class ItemScanner {
    
    /**
     * 扫描所有维度的掉落物，按维度分类返回
     * @param server 服务器实例
     * @return 维度ID -> 掉落物列表的映射
     */
    public static Map<ResourceLocation, List<ItemEntity>> scanAllDimensions(MinecraftServer server) {
        Map<ResourceLocation, List<ItemEntity>> dimensionItems = new HashMap<>();
        
        // 遍历所有已加载的维度
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();
            List<ItemEntity> items = scanDimension(level);
            
            if (!items.isEmpty()) {
                dimensionItems.put(dimensionId, items);
            }
        }
        
        return dimensionItems;
    }
    
    /**
     * 扫描单个维度的所有掉落物
     * @param level 服务器维度
     * @return 掉落物列表
     */
    public static List<ItemEntity> scanDimension(ServerLevel level) {
        List<ItemEntity> items = new ArrayList<>();
        
        // 获取维度中所有的ItemEntity
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof ItemEntity itemEntity) {
                // 检查物品是否有效
                ItemStack itemStack = itemEntity.getItem();
                if (!itemStack.isEmpty()) {
                    items.add(itemEntity);
                }
            }
        });
        
        return items;
    }
    
    /**
     * 扫描指定维度的掉落物
     * @param server 服务器实例
     * @param dimensionKey 维度Key
     * @return 掉落物列表，如果维度不存在返回空列表
     */
    public static List<ItemEntity> scanSpecificDimension(MinecraftServer server, ResourceKey<Level> dimensionKey) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return new ArrayList<>();
        }
        
        return scanDimension(level);
    }
    
    /**
     * 统计所有维度的掉落物数量
     * @param server 服务器实例
     * @return 维度ID -> 掉落物数量的映射
     */
    public static Map<ResourceLocation, Integer> countItemsByDimension(MinecraftServer server) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();
            int count = scanDimension(level).size();
            counts.put(dimensionId, count);
        }
        
        return counts;
    }
    
    /**
     * 获取所有维度的总掉落物数量
     * @param server 服务器实例
     * @return 总数量
     */
    public static int getTotalItemCount(MinecraftServer server) {
        return countItemsByDimension(server).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
    }
    
    /**
     * 检查指定维度是否有掉落物
     * @param server 服务器实例
     * @param dimensionKey 维度Key
     * @return 是否有掉落物
     */
    public static boolean hasDroppedItems(MinecraftServer server, ResourceKey<Level> dimensionKey) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return false;
        }
        
        return !scanDimension(level).isEmpty();
    }
    
    /**
     * 获取掉落物统计信息的字符串
     * @param server 服务器实例
     * @return 统计信息
     */
    public static String getStatistics(MinecraftServer server) {
        Map<ResourceLocation, Integer> counts = countItemsByDimension(server);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        
        StringBuilder sb = new StringBuilder();
        sb.append("Total items: ").append(total).append("\n");
        
        for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        
        return sb.toString();
    }
}