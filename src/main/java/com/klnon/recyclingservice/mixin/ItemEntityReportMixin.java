package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * ItemEntity Mixin - 物品实体清理上报
 * 功能：
 * - 满足清理条件的物品实体主动上报到缓存
 * - 删除信号激活时，将缓存中的物品转移到垃圾箱并删除
 */
@Mixin(ItemEntity.class)
public class ItemEntityReportMixin {

    /** 清理期间检查间隔（5秒） */
    @Unique
    private static final int CLEANUP_CHECK_INTERVAL = 20*5;

    @Inject(method = "tick", at = @At("TAIL"))
    private void recyclingservice$checkAndReport(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;

        // 仅服务端处理
        if (self.level().isClientSide()) {
            return;
        }

        // 分散检查时间，避免同时计算
        if (!recyclingservice$shouldCheckThisTick(self)) {
            return;
        }

        ResourceLocation dimension = self.level().dimension().location();
        UUID uuid = self.getUUID();
        boolean alreadyReported = CleanupManager.isEntityReported(dimension, uuid);
        boolean deleteSignalActive = CleanupManager.shouldDeleteEntity(self.level().getServer());

        // 删除信号激活时：处理已上报的实体
        if (deleteSignalActive && alreadyReported) {
            TrashBoxManager.addItemToDimension(dimension, self.getItem());
            self.discard();
            return;
        }
        // 已上报：刷新时间戳（证明实体还活着）
        if (alreadyReported) {
            CleanupManager.refreshEntity(CleanupManager.ITEM, dimension, uuid);
            return;
        }

        // 非删除期间：上报符合条件的新实体
        if (!deleteSignalActive && !alreadyReported && recyclingservice$shouldReport(self)) {
            CleanupManager.addEntity(CleanupManager.ITEM, dimension, uuid);
        }
    }

    /**
     * 检查本tick是否应该执行检查（分散负载）
     */
    @Unique
    private boolean recyclingservice$shouldCheckThisTick(ItemEntity self) {
        int interval = CleanupManager.isDeleteSignalActive()
                ? CLEANUP_CHECK_INTERVAL
                : Config.getEntityCheckIntervalTicks();
        // 使用实体ID分散检查时机
        int phase = Math.floorMod(self.getId(), interval);
        return self.tickCount % interval == phase;
    }

    /**
     * 检查物品是否应该上报到清理缓存
     */
    @Unique
    private boolean recyclingservice$shouldReport(ItemEntity self) {
        // 存活时间不足则跳过
        if (self.getAge() < Config.getMinAgeForCleanupTicks()) {
            return false;
        }
        // 通过清理过滤器检查
        return CleanupManager.shouldCleanItem(self);
    }
}