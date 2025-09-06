package com.klnon.recyclingservice.content.cleanup.mixin;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import net.minecraft.resources.ResourceLocation;
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
            boolean alreadyReported = CleanupManager.isEntityReported(self);
            
            // 检查是否应该上报
            boolean shouldReport = recyclingservice$shouldReport(self);
            ResourceLocation dimension = self.level().dimension().location();
            
            if (shouldReport && !alreadyReported) {
                // 应该上报且未上报 -> 上报
                CleanupManager.reportEntity(dimension,self.getUUID(),self);
            } else if (!shouldReport && alreadyReported && !self.level().isClientSide()) {
                // 不应该上报但已上报 -> 取消上报
                CleanupManager.removeReportedEntity(dimension,self);
            }
            
            // 检查全局删除信号，如果激活且在缓存中则自删除
            if (!self.level().isClientSide() && alreadyReported && 
                CleanupManager.shouldDeleteEntity(self.level().getServer())) {
                // 添加物品到垃圾箱
                TrashBoxManager.addItemToDimension(dimension,self.getItem());
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
                   CleanupManager.shouldCleanItem(self); // 预过滤
        } catch (Exception e) {
            return false;
        }
    }
}