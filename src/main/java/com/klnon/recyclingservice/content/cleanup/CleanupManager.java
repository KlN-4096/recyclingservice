package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.content.cleanup.entity.EntityFilter;
import com.klnon.recyclingservice.content.cleanup.entity.EntityReportCache;
import com.klnon.recyclingservice.content.cleanup.service.CleanupService;
import com.klnon.recyclingservice.content.cleanup.service.CleanupService.CleanupResult;
import com.klnon.recyclingservice.content.cleanup.signal.GlobalDeleteSignal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 清理管理器 - 接口层
 * 提供清理功能的统一API入口，委托具体实现给CleanupService
 */
public class CleanupManager {
    
    // === 公共API：实体上报管理（供Mixin使用） ===
    
    /**
     * 上报实体到清理缓存
     * @param entity 要上报的实体
     */
    public static void reportEntity(Entity entity) {
        EntityReportCache.report(entity);
    }
    
    /**
     * 从清理缓存中移除实体
     * @param entity 要移除的实体
     */
    public static void removeReportedEntity(Entity entity) {
        EntityReportCache.remove(entity);
    }
    
    /**
     * 检查实体是否已在清理缓存中
     * @param entity 要检查的实体
     * @return 是否已上报
     */
    public static boolean isEntityReported(Entity entity) {
        return EntityReportCache.isEntityReported(entity);
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
    
    // === 核心清理功能 ===
    
    /**
     * 执行自动清理
     */
    public static CleanupResult performAutoCleanup(MinecraftServer server) {
        return CleanupService.performAutoCleanup(server);
    }
}