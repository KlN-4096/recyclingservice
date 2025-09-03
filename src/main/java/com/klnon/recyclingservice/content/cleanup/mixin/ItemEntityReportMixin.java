package com.klnon.recyclingservice.content.cleanup.mixin;

import com.klnon.recyclingservice.content.cleanup.entity.EntityFilter;
import com.klnon.recyclingservice.content.cleanup.entity.EntityReportCache;
import com.klnon.recyclingservice.content.cleanup.signal.GlobalDeleteSignal;
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
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void checkAndReport(CallbackInfo ci) {
        try {
            ItemEntity self = (ItemEntity)(Object)this;
            
            // 4秒检查一次，分散检查时间避免同时计算
            if (self.tickCount % (20*4) != (self.getId() % 20)) {
                return;
            }
            
            // 检查是否已在缓存中
            boolean alreadyReported = EntityReportCache.isEntityReported(self);
            
            // 检查是否应该上报
            boolean shouldReport = recyclingservice$shouldReport(self);
            
            if (shouldReport && !alreadyReported) {
                // 应该上报且未上报 -> 上报
                EntityReportCache.report(self);
            } else if (!shouldReport && alreadyReported && !self.level().isClientSide()) {
                // 不应该上报但已上报 -> 取消上报
                EntityReportCache.remove(self);
            }
            
            // 检查全局删除信号，如果激活且在缓存中则自删除
            if (!self.level().isClientSide() && alreadyReported && 
                GlobalDeleteSignal.shouldDelete(self.level().getServer())) {
                self.discard();
            }
        } catch (Exception e) {
            // 出错跳过，什么都不做
        }
    }
    
    @Unique
    private boolean recyclingservice$shouldReport(ItemEntity self) {
        try {
            // 性能优化：预过滤逻辑 - 只上报需要清理的物品实体
            return self.getAge() >= 10 * 20 && // 10秒后才考虑清理
                   EntityFilter.shouldCleanItem(self); // 预过滤
        } catch (Exception e) {
            return false;
        }
    }
}