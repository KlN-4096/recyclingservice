package com.klnon.recyclingservice.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class ItemTooltip {
    /**
     * 增强物品Tooltip显示真实数量
     * 使用1.21.1的DataComponent系统添加Lore信息,先清除再添加
     * 
     * @param original 原始物品堆
     * @return 增强后的物品堆
     */
    public static ItemStack enhanceTooltip(ItemStack original) {
        // cleanItemStack(original);
        if (original.getCount() <= 64) {
            return original.copy();
        }
        
        ItemStack enhanced = original.copy();
        
        // 使用DataComponent系统添加Lore
        List<Component> loreLines = new ArrayList<>();
        
        // 添加真实数量信息
        loreLines.add(Component.empty()); // 空行分隔
        loreLines.add(Component.literal("§7可取出: §a" + original.getCount())
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
