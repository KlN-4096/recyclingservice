package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.util.cleanup.ItemFilter;
import com.klnon.recyclingservice.util.cleanup.SimpleReportCache;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ItemEntity上报Mixin
 * 物品实体满足清理条件时主动上报到缓存
 */
@Mixin(ItemEntity.class)
public class ItemEntityReportMixin {
    @Unique private boolean reported = false;
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void checkAndReport(CallbackInfo ci) {
        try {
            ItemEntity self = (ItemEntity)(Object)this;
            
            // 30秒检查一次，分散检查时间避免同时计算
            if (self.tickCount % (20 * 30) != (self.getId() % 20)) {
                return;
            }
            
            boolean shouldReport = shouldReport(self);
            
            if (shouldReport && !reported) {
                SimpleReportCache.report(self);
                reported = true;
            } else if (!shouldReport && reported && !self.level().isClientSide()) {
                SimpleReportCache.cancel(self);
                reported = false;
            }
        } catch (Exception e) {
            // 出错跳过，什么都不做
        }
    }
    
    @Unique
    private boolean shouldReport(ItemEntity self) {
        try {
            // 简单的条件检查，出错就返回false
            return self.getAge() >= 60 * 20 && // 60秒后才考虑清理
                   ItemFilter.shouldCleanItem(self);
        } catch (Exception e) {
            return false;
        }
    }
}