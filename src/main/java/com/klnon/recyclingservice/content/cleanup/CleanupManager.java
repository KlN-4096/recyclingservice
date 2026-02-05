package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.content.cleanup.function.EntityCompare;
import com.klnon.recyclingservice.content.cleanup.function.EntityFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 清理管理器 - 接口层
 * 提供清理功能的统一API入口，委托具体实现给CleanupService
 */
public class CleanupManager {

    public static final EntityCache.EntityType ITEM = EntityCache.EntityType.ITEM;
    public static final EntityCache.EntityType PROJECTILE = EntityCache.EntityType.PROJECTILE;

    // === 核心清理功能 ===

    /**
     * 检查全局删除信号状态
     */
    public static boolean shouldDeleteEntity(MinecraftServer server) {
        return CleanupController.shouldDelete(server);
    }

    // === 实体过滤判断（供Mixin使用） ===

    /**
     * 检查是否应该清理物品实体
     */
    public static boolean shouldCleanItem(ItemEntity itemEntity) {
        return EntityFilter.shouldCleanItem(itemEntity);
    }

    /**
     * 检查是否应该清理弹射物
     */
    public static boolean shouldCleanProjectile(Entity entity) {
        return EntityFilter.shouldCleanProjectile(entity);
    }

    // === 实体缓存管理（供Mixin使用） ===

    /**
     * 上报实体到清理缓存
     */
    public static void addEntity(EntityCache.EntityType type, ResourceLocation dimension, UUID uuid) {
        EntityCache.addEntity(type, dimension, uuid);
    }

    /**
     * 从清理缓存中移除实体
     */
    public static void removeEntity(EntityCache.EntityType type, ResourceLocation dimension, UUID uuid) {
        EntityCache.removeEntity(type, dimension, uuid);
    }

    /**
     * 检查实体是否已在清理缓存中
     */
    public static boolean isEntityReported(ResourceLocation dimension, UUID uuid) {
        return EntityCache.isEntityReported(PROJECTILE, dimension, uuid)
                || EntityCache.isEntityReported(ITEM, dimension, uuid);
    }

    public static void pruneExpiredCache(){
        EntityCache.pruneExpiredCache();
    }

    public static void refreshEntity(EntityCache.EntityType type, ResourceLocation dimension, UUID uuid){
        EntityCache.refreshEntity(type,dimension,uuid);
    }
    // === 统计信息查询 ===

    /**
     * 获取所有维度的物品数量
     */
    public static int getAllEntityCount(EntityCache.EntityType type) {
        return EntityCache.getAllEntityCount(type);
    }

    /**
     * 获取总的上报实体数量
     */
    public static int getTotalReportedCount() {
        return getAllEntityCount(ITEM) + CleanupManager.getAllEntityCount(PROJECTILE);
    }

    // === 工具方法 ===

    /**
     * 生成复杂物品键
     */
    public static String generateItemHash(ItemStack stack) {
        return EntityCompare.generateItemHash(stack);
    }

    /**
     * 检查两个物品是否为同一种物品（用于UI操作中的物品比较）
     *
     * @param stack1 第一个物品
     * @param stack2 第二个物品
     * @return 是否为同一种物品
     */
    public static boolean isSameItem(ItemStack stack1, ItemStack stack2) {
        return EntityCompare.isSameItem(stack1, stack2);
    }

    // === 清理操作 ===

    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        EntityCache.clearAll();
    }

    public static boolean isDeleteSignalActive() {
        return CleanupController.isDeleteSignalActive();
    }

    // === 清理统计（真实被清理的实体数）===

    public static void resetCleanedCounts() {
        EntityCache.resetCleanedCounts();
    }

    public static void recordCleanedItem(ResourceLocation dimension) {
        EntityCache.recordCleanedItem(dimension);
    }

    public static void recordCleanedProjectile(ResourceLocation dimension) {
        EntityCache.recordCleanedProjectile(dimension);
    }

    public static java.util.Map<ResourceLocation, Integer> getCleanedItemCountsByDimension() {
        return EntityCache.getCleanedItemCountsByDimension();
    }

    public static java.util.Map<ResourceLocation, Integer> getCleanedProjectileCountsByDimension() {
        return EntityCache.getCleanedProjectileCountsByDimension();
    }
}
