package com.klnon.recyclingservice.util.other;

import java.util.ArrayList;
import java.util.List;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

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
    public static void updateSlotAfterMove(Slot slot, int moveCount) {
        ItemStack slotItem = slot.getItem();
        if (moveCount == 0 || slotItem.getCount() <= moveCount) {
            slot.set(ItemStack.EMPTY);
        } else {
            slotItem.shrink(moveCount);
            slot.set(updateTooltip(slotItem));
        }
    }

        /**
     * 增强物品Tooltip显示真实数量
     * 使用1.21.1的DataComponent系统添加Lore信息,先清除再添加
     * 
     * @param original 原始物品堆
     * @return 增强后的物品堆
     */
    public static ItemStack updateTooltip(ItemStack original) {
        // cleanItemStack(original);
        if (original.getCount() <= 64) {
            return original.copy();
        }
        
        ItemStack enhanced = original.copy();
        
        // 使用DataComponent系统添加Lore
        List<Component> loreLines = new ArrayList<>();
        
        // 添加真实数量信息
        loreLines.add(Component.empty()); // 空行分隔
        loreLines.add(Component.literal(Config.getItemCountDisplay(original.getCount()))
            .withStyle(style -> style.withItalic(false)));
        
        // 应用新的lore
        enhanced.set(DataComponents.LORE, new ItemLore(loreLines));
        
        return enhanced;
    }

    /**
     * 清理ItemStack的Lore，返回原始物品
     * KISS原则：最简单的解决方案
     * 
     * @param item 可能包含自定义Lore的物品
     * @return 清理后的原始物品
     */
    public static ItemStack cleanItemStack(ItemStack item) {
        if (item.isEmpty()) {
            return item;
        }
        ItemStack cleaned = item.copy();
        cleaned.remove(DataComponents.LORE);
        return cleaned;
    }
}
