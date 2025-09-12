package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

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
            
            // 10秒检查一次,清扫时1秒检查一次
            if(!CleanupManager.isDeleteSignalActive()){
                if (self.tickCount % 20 != 0) {
                    return;
                }
            }else {
                if (self.tickCount % (20 * 10) != 0) {
                    return;
                }
            }
            
            ResourceLocation dimension = self.level().dimension().location();
            UUID uuid = self.getUUID();
            ChunkPos chunkPos = new ChunkPos(self.blockPosition());
            
            // 检查是否已在缓存中
            boolean alreadyReported = CleanupManager.isEntityReported(dimension, uuid);
            
            // 检查是否应该上报
            boolean shouldReport = recyclingservice$shouldReport(self);
            
            if (shouldReport && !alreadyReported && !self.level().isClientSide() && !CleanupManager.shouldDeleteEntity(self.level().getServer())) {
                // 应该上报且未上报 -> 上报
                CleanupManager.addEntity(CleanupManager.PROJECTILE,dimension, uuid);
                // 增加区块计数
                ChunkDataCache.incrementEntityCount(dimension, chunkPos);
            } 
            
            // 检查全局删除信号，如果激活且在缓存中则自删除
            if (!self.level().isClientSide() && alreadyReported && 
                CleanupManager.shouldDeleteEntity(self.level().getServer())) {
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
                   CleanupManager.shouldCleanProjectile(self);
        } catch (Exception e) {
            return false;
        }
    }
}