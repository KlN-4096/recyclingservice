package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.content.chunk.ChunkCache;
import com.klnon.recyclingservice.content.cleanup.entity.EntityFilter;
import com.klnon.recyclingservice.content.cleanup.entity.EntityMerger;
import com.klnon.recyclingservice.content.cleanup.entity.EntityCache;
import com.klnon.recyclingservice.content.cleanup.CleanupService.CleanupResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 清理管理器 - 接口层
 * 提供清理功能的统一API入口，委托具体实现给CleanupService
 */
public class CleanupManager {
    
    // === 公共API：实体上报管理（供Mixin使用） ===
    
    /**
     * 上报物品实体到清理缓存
     * @param dimension 维度
     * @param uuid 实体UUID
     */
    public static void reportItem(ResourceLocation dimension, UUID uuid) {
        EntityCache.addItem(dimension, uuid);
    }
    
    /**
     * 上报弹射物到清理缓存
     * @param dimension 维度
     * @param uuid 实体UUID
     */
    public static void reportProjectile(ResourceLocation dimension, UUID uuid) {
        EntityCache.addProjectile(dimension, uuid);
    }
    
    /**
     * 从清理缓存中移除物品实体
     * @param dimension 维度
     * @param uuid 实体UUID
     */
    public static void removeReportedItem(ResourceLocation dimension, UUID uuid) {
        EntityCache.removeItem(dimension, uuid);
    }
    
    /**
     * 从清理缓存中移除弹射物
     * @param dimension 维度
     * @param uuid 实体UUID
     */
    public static void removeReportedProjectile(ResourceLocation dimension, UUID uuid) {
        EntityCache.removeProjectile(dimension, uuid);
    }
    
    /**
     * 检查物品实体是否已在清理缓存中
     * @param dimension 维度
     * @param uuid 实体UUID
     * @return 是否已上报
     */
    public static boolean isItemReported(ResourceLocation dimension, UUID uuid) {
        return EntityCache.isItemReported(dimension, uuid);
    }
    
    /**
     * 检查弹射物是否已在清理缓存中
     * @param dimension 维度
     * @param uuid 实体UUID
     * @return 是否已上报
     */
    public static boolean isProjectileReported(ResourceLocation dimension, UUID uuid) {
        return EntityCache.isProjectileReported(dimension, uuid);
    }
    
    /**
     * 检查全局删除信号状态
     * @param server 服务器实例
     * @return 是否应该删除
     */
    public static boolean shouldDeleteEntity(MinecraftServer server) {
        return GlobalDeleteSignal.shouldDelete(server);
    }
    
    /**
     * 检查是否应该清理弹射物（供Mixin使用）
     * @param entity 弹射物实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanProjectile(Entity entity) {
        return EntityFilter.shouldCleanProjectile(entity);
    }
    
    /**
     * 检查是否应该清理物品实体（供Mixin使用）
     * @param itemEntity 物品实体
     * @return 是否应该清理
     */
    public static boolean shouldCleanItem(ItemEntity itemEntity) {
        return EntityFilter.shouldCleanItem(itemEntity);
    }

    /**
     * 执行自动清理
     */
    public static String generateComplexItemKey(ItemStack stack) {
        return EntityMerger.generateComplexItemKey(stack);
    }

    public static String getStateName(ChunkCache.ChunkInfo chunkInfo) {
        return chunkInfo.getStateName();
    }

    public static int getStateByName(String name) {
        return ChunkCache.ChunkInfo.getStateByName(name);
    }
    // === 核心清理功能 ===
    
    /**
     * 执行自动清理
     */
    public static CleanupResult performAutoCleanup(MinecraftServer server) {
        return CleanupService.performAutoCleanup(server);
    }
}