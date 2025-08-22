package com.klnon.recyclingservice.util;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.util.Item.ItemTooltip;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class UiUtils {
    public static boolean hasModInstalled(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        
        return ErrorHandler.handleStaticOperation("modDetection", () -> {
            // 检查客户端是否注册了我们的mod网络通道
            ResourceLocation modChannel = ResourceLocation.fromNamespaceAndPath(
                Recyclingservice.MODID, "main"
            );
            
            // NeoForge网络通道检测
            return serverPlayer.connection.hasChannel(modChannel);
        }, false); // 如果检测失败，默认认为客户端无mod（安全策略）
    }

    /**
     * 在物品交换完毕后更新垃圾箱内物品数量
     * @param slot
     * @param slotItem
     * @param moveCount
    */
    public static void updateSlotAfterMove(Slot slot, ItemStack slotItem, int moveCount) {
        if (moveCount == 0 || slotItem.getCount() <= moveCount) {
            slot.set(ItemStack.EMPTY);
        } else {
            slotItem.shrink(moveCount);
            slot.set(ItemTooltip.enhanceTooltip(slotItem));
        }
    }

}
