package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.content.chunk.ChunkCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

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
            
            // 10秒检查一次，分散检查时间避免同时计算
            if (self.tickCount % (20*10) != (self.getId() % 20)) {
                return;
            }
            
            ResourceLocation dimension = self.level().dimension().location();
            UUID uuid = self.getUUID();
            ChunkPos chunkPos = new ChunkPos(self.blockPosition());
            
            // 检查是否已在缓存中
            boolean alreadyReported = CleanupManager.isItemReported(dimension, uuid);
            
            // 检查是否应该上报
            boolean shouldReport = recyclingservice$shouldReport(self);
            
            if (shouldReport && !alreadyReported && !CleanupManager.shouldDeleteEntity(self.level().getServer())) {
                // 应该上报且未上报 -> 上报
                CleanupManager.reportItem(dimension, uuid);
                // 增加区块计数
                ChunkCache.incrementEntityCount(dimension, chunkPos);
            }
            
            // 检查全局删除信号，如果激活且在缓存中则自删除
            if (!self.level().isClientSide() && alreadyReported && 
                CleanupManager.shouldDeleteEntity(self.level().getServer())) {
                // 添加物品到垃圾箱
                TrashBoxManager.addItemToDimension(dimension, self.getItem());
                // discard钩子会处理计数减少和缓存移除
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
    
    /**
     * 监听实体discard事件，确保计数器正确减少
     */
    @Inject(method = "discard", at = @At("HEAD"))
    private void onDiscard(CallbackInfo ci) {
        try {
            ItemEntity self = (ItemEntity)(Object)this;
            ResourceLocation dimension = self.level().dimension().location();
            UUID uuid = self.getUUID();
            
            // 检查是否已上报
            if (CleanupManager.isItemReported(dimension, uuid)) {
                ChunkPos chunkPos = new ChunkPos(self.blockPosition());
                
                // 减少计数
                ChunkCache.decrementEntityCount(dimension, chunkPos);
                
                // 从缓存中移除
                CleanupManager.removeReportedItem(dimension, uuid);
            }
        } catch (Exception e) {
            // 出错跳过
        }
    }
}