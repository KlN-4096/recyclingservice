package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Projectile Mixin - 弹射物清理上报
 * 功能：
 * - 满足清理条件的弹射物主动上报到缓存
 * - 删除信号激活时删除缓存中的弹射物
 */
@Mixin(targets = {
        "net.minecraft.world.entity.projectile.AbstractArrow",
        "net.minecraft.world.entity.projectile.Projectile"
})
public class ProjectileReportMixin {

    /** 清理期间检查间隔（5秒） */
    @Unique
    private static final int CLEANUP_CHECK_INTERVAL = 20*5;

    @Inject(method = "tick", at = @At("TAIL"))
    private void recyclingservice$checkAndReport(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        MinecraftServer server = self.level().getServer();
        if (server == null) {
            return;
        }

        // 分散检查时间，避免同时计算
        if (!recyclingservice$shouldCheckThisTick(self)) {
            return;
        }

        ResourceLocation dimension = self.level().dimension().location();
        UUID uuid = self.getUUID();
        boolean alreadyReported = CleanupManager.isEntityReported(dimension, uuid);
        boolean deleteSignalActive = CleanupManager.shouldDeleteEntity(server);

        // 删除信号激活时：删除已上报的实体
        if (deleteSignalActive && alreadyReported) {
            self.discard();
            return;
        }
        // 已上报：刷新时间戳（证明实体还活着）
        if (alreadyReported) {
            CleanupManager.refreshEntity(CleanupManager.PROJECTILE, dimension, uuid);
            return;
        }

        // 非删除期间：上报符合条件的新实体
        if (!deleteSignalActive && !alreadyReported && recyclingservice$shouldReport(self)) {
            CleanupManager.addEntity(CleanupManager.PROJECTILE, dimension, uuid);
        }
    }

    /**
     * 检查本tick是否应该执行检查（分散负载）
     */
    @Unique
    private boolean recyclingservice$shouldCheckThisTick(Entity self) {
        int interval = CleanupManager.isDeleteSignalActive()
                ? CLEANUP_CHECK_INTERVAL
                : Config.getEntityCheckIntervalTicks();
        // 使用实体ID分散检查时机
        int phase = Math.floorMod(self.getId(), interval);
        return self.tickCount % interval == phase;
    }

    /**
     * 检查弹射物是否应该上报到清理缓存
     */
    @Unique
    private boolean recyclingservice$shouldReport(Entity self) {
        if (self.tickCount < Config.getMinAgeForCleanupTicks()) {
            return false;
        }
        return CleanupManager.shouldCleanProjectile(self);
    }
}