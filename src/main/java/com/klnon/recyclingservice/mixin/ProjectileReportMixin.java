package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;
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
    @Unique private boolean reported = false;
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void checkAndReport(CallbackInfo ci) {
        try {
            Entity self = (Entity)(Object)this;
            
            // 10秒检查一次
            if (self.tickCount % (20 * 10) != 0) {
                return;
            }
            
            boolean shouldReport = shouldReport(self);
            
            if (shouldReport && !reported) {
                SimpleReportCache.report(self);
                reported = true;
            }
        } catch (Exception e) {
            // 出错跳过
        }
    }
    
    @Unique
    private boolean shouldReport(Entity self) {
        try {
            return self.tickCount >= 30 * 20 && // 30秒后考虑清理
                   Config.shouldCleanProjectiles() &&
                   Config.isProjectileTypeToClean(self.getType().toString());
        } catch (Exception e) {
            return false;
        }
    }
}