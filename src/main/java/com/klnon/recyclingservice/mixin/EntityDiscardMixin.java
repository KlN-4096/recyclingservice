package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Entity.class)
public class EntityDiscardMixin {
    /**
     * 监听实体discard事件，确保计数器正确减少
     */
    @Inject(method = "discard", at = @At("HEAD"))
    private void onDiscard(CallbackInfo ci) {
        try {
            Entity self = (Entity)(Object)this;
            if (!(self instanceof ItemEntity) && !(self instanceof Projectile)) {
                return;
            }
            ResourceLocation dimension = self.level().dimension().location();
            UUID uuid = self.getUUID();

            // 检查是否已上报
            if (CleanupManager.isEntityReported(dimension, uuid)) {

                // 从缓存中移除 - 修正的部分
                if (self instanceof ItemEntity) {
                    CleanupManager.removeEntity(CleanupManager.ITEM,dimension, uuid);
                } else if (self instanceof Projectile) {
                    CleanupManager.removeEntity(CleanupManager.PROJECTILE,dimension, uuid);
                }
            }
        } catch (Exception e) {
            // Recyclingservice.LOGGER.debug("Error in discard mixin", e);
        }
    }
}