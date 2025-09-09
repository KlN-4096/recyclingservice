package com.klnon.recyclingservice.content.cleanup.entity;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级实体上报缓存系统
 * 仅存储UUID，分别管理物品和弹射物
 */
public class EntityCache {
    
    // 分别存储物品和弹射物的UUID集合
    private static final Map<ResourceLocation, Set<UUID>> reportedItems = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Set<UUID>> reportedProjectiles = new ConcurrentHashMap<>();
    
    // === 物品管理 ===
    
    /**
     * 添加物品到缓存
     */
    public static void addItem(ResourceLocation dimension, UUID uuid) {
        reportedItems.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }
    
    /**
     * 从缓存中移除物品
     */
    public static void removeItem(ResourceLocation dimension, UUID uuid) {
        Set<UUID> items = reportedItems.get(dimension);
        if (items != null) {
            items.remove(uuid);
            if (items.isEmpty()) {
                reportedItems.remove(dimension);
            }
        }
    }
    
    /**
     * 检查物品是否已上报
     */
    public static boolean isItemReported(ResourceLocation dimension, UUID uuid) {
        Set<UUID> items = reportedItems.get(dimension);
        return items != null && items.contains(uuid);
    }
    
    /**
     * 获取指定维度的物品数量
     */
    public static int getItemCount(ResourceLocation dimension) {
        Set<UUID> items = reportedItems.get(dimension);
        return items != null ? items.size() : 0;
    }
    
    // === 弹射物管理 ===
    
    /**
     * 添加弹射物到缓存
     */
    public static void addProjectile(ResourceLocation dimension, UUID uuid) {
        reportedProjectiles.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }
    
    /**
     * 从缓存中移除弹射物
     */
    public static void removeProjectile(ResourceLocation dimension, UUID uuid) {
        Set<UUID> projectiles = reportedProjectiles.get(dimension);
        if (projectiles != null) {
            projectiles.remove(uuid);
            if (projectiles.isEmpty()) {
                reportedProjectiles.remove(dimension);
            }
        }
    }
    
    /**
     * 检查弹射物是否已上报
     */
    public static boolean isProjectileReported(ResourceLocation dimension, UUID uuid) {
        Set<UUID> projectiles = reportedProjectiles.get(dimension);
        return projectiles != null && projectiles.contains(uuid);
    }
    
    /**
     * 获取指定维度的弹射物数量
     */
    public static int getProjectileCount(ResourceLocation dimension) {
        Set<UUID> projectiles = reportedProjectiles.get(dimension);
        return projectiles != null ? projectiles.size() : 0;
    }
    
    // === 通用方法 ===
    
    /**
     * 获取指定维度的总实体数量
     */
    public static int getTotalCount(ResourceLocation dimension) {
        return getItemCount(dimension) + getProjectileCount(dimension);
    }
    
    /**
     * 获取所有维度缓存的实体总数
     */
    public static int getTotalReportedCount() {
        int itemTotal = reportedItems.values().stream().mapToInt(Set::size).sum();
        int projectileTotal = reportedProjectiles.values().stream().mapToInt(Set::size).sum();
        return itemTotal + projectileTotal;
    }
    
    /**
     * 清空指定维度的所有缓存
     */
    public static void clearDimension(ResourceLocation dimension) {
        reportedItems.remove(dimension);
        reportedProjectiles.remove(dimension);
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        reportedItems.clear();
        reportedProjectiles.clear();
    }
}