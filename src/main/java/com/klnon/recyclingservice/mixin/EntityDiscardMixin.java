package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.cleanup.EntityCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Entity Mixin - 监听实体删除事件
 * 功能：实体被discard时从清理缓存中移除
 */
@Mixin(Entity.class)
public class EntityDiscardMixin {

    @Inject(method = "discard", at = @At("HEAD"))
    private void recyclingservice$onDiscard(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;

        // 只处理物品和弹射物
        if (!(self instanceof ItemEntity) && !(self instanceof Projectile)) {
            return;
        }

        // 仅服务端处理
        if (self.level().getServer() == null) {
            return;
        }

        ResourceLocation dimension = self.level().dimension().location();
        UUID uuid = self.getUUID();

        // 如果在缓存中，移除
        if (CleanupManager.isEntityReported(dimension, uuid)) {
            EntityCache.EntityType type = (self instanceof ItemEntity)
                    ? CleanupManager.ITEM
                    : CleanupManager.PROJECTILE;
            CleanupManager.removeEntity(type, dimension, uuid);
        }
    }
}