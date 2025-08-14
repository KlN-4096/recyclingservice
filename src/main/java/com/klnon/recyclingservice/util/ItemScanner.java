package com.klnon.recyclingservice.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 物品扫描器 - 扫描维度中的掉落物和弹射物
 * 遵循KISS原则：专注于核心扫描功能
 */
public class ItemScanner {
    
    // 空结果常量，避免重复创建
    private static final ScanResult EMPTY_RESULT = new ScanResult(
        Collections.emptyList(), Collections.emptyList()
    );
    
    /**
     * 扫描单个维度的所有掉落物和弹射物
     * @param level 服务器维度
     * @return 扫描结果，包含物品和弹射物
     */
    public static ScanResult scanDimension(ServerLevel level) {
        List<ItemEntity> items = new ArrayList<>();
        List<Entity> projectiles = new ArrayList<>();
        
        // 一次遍历同时检测物品和弹射物
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof ItemEntity itemEntity) {
                // 检查物品是否有效
                ItemStack itemStack = itemEntity.getItem();
                if (!itemStack.isEmpty()) {
                    items.add(itemEntity);
                }
            } else if (ItemFilter.shouldCleanProjectile(entity)) {
                // 检查弹射物是否应该被清理
                projectiles.add(entity);
            }
        });
        
        return new ScanResult(items, projectiles);
    }

    /**
     * 扫描指定维度的掉落物和弹射物
     * @param server 服务器实例
     * @param dimensionKey 维度Key
     * @return 扫描结果，如果维度不存在返回空结果
     */
    public static ScanResult scanDimension(MinecraftServer server, ResourceKey<Level> dimensionKey) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            return EMPTY_RESULT;
        }
        
        return scanDimension(level);
    }

    /**
     * 扫描所有维度的掉落物和弹射物，按维度分类返回
     * @param server 服务器实例
     * @return 维度ID -> 扫描结果的映射
     */
    public static Map<ResourceLocation, ScanResult> scanAllDimensions(MinecraftServer server) {
        Map<ResourceLocation, ScanResult> dimensionResults = new HashMap<>();
        
        // 遍历所有已加载的维度
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();
            ScanResult result = scanDimension(level);
            
            if (!result.isEmpty()) {
                dimensionResults.put(dimensionId, result);
            }
        }
        
        return dimensionResults;
    }
    
    /**
     * 扫描结果类 - 包含物品和弹射物
     */
    public static class ScanResult {
        private final List<ItemEntity> items;
        private final List<Entity> projectiles;
        
        public ScanResult(List<ItemEntity> items, List<Entity> projectiles) {
            this.items = items;
            this.projectiles = projectiles;
        }
        
        public List<ItemEntity> getItems() {
            return items;
        }
        
        public List<Entity> getProjectiles() {
            return projectiles;
        }
        
        public boolean isEmpty() {
            return items.isEmpty() && projectiles.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("ScanResult{items=%d, projectiles=%d}", 
                               items.size(), projectiles.size());
        }
    }
}