package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;
import com.klnon.recyclingservice.util.cleanup.GlobalDeleteSignal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Projectile上报Mixin
 * 弹射物满足清理条件时主动上报到缓存
 */
@Mixin(targets = {
    "net.minecraft.world.entity.projectile.AbstractArrow",
    "net.minecraft.world.entity.projectile.Projectile"
})
public class ProjectileReportMixin {
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void checkAndReport(CallbackInfo ci) {
        try {
            Entity self = (Entity)(Object)this;
            
            // 4秒检查一次
            if (self.tickCount % (20 * 4) != 0) {
                return;
            }
            
            // 检查是否已在缓存中
            boolean alreadyReported = SimpleReportCache.isEntityReported(self);
            
            // 检查是否应该上报
            boolean shouldReport = recyclingservice$shouldReport(self);
            
            if (shouldReport && !alreadyReported && !self.level().isClientSide()) {
                // 应该上报且未上报 -> 上报
                SimpleReportCache.report(self);
            }
            
            // 检查全局删除信号，如果激活且在缓存中则自删除
            if (!self.level().isClientSide() && alreadyReported && 
                GlobalDeleteSignal.shouldDelete(self.level().getServer())) {
                self.discard();
            }
        } catch (Exception e) {
            // 出错跳过
        }
    }
    
    @Unique
    private boolean recyclingservice$shouldReport(Entity self) {
        try {
            return self.tickCount >= 10 * 20 && // 10秒后考虑清理
                   Config.GAMEPLAY.cleanProjectiles.get() &&
                   Config.projectileTypesCache.contains(
                       BuiltInRegistries.ENTITY_TYPE.getKey(self.getType()).toString());
        } catch (Exception e) {
            return false;
        }
    }
}