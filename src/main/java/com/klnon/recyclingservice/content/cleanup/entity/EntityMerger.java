package com.klnon.recyclingservice.content.cleanup.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public class EntityMerger {

    public static Boolean isSameItem(ItemStack stack1, ItemStack stack2) {
        return generateComplexItemKey(stack1).equals(generateComplexItemKey(stack2));
    }

    /**
     * 为复杂物品生成唯一键，用于区分不同的复杂物品
     */
    public static String generateComplexItemKey(ItemStack stack) {
        StringBuilder keyBuilder = new StringBuilder();

        // 基础物品类型
        keyBuilder.append(stack.getItem());

        // 创建一个临时的ItemStack副本，移除LORE组件后计算哈希值
        ItemStack tempStack = stack.copy();
        tempStack.remove(DataComponents.LORE);

        // 添加除LORE外的所有组件数据的哈希值
        keyBuilder.append("_components_").append(tempStack.getComponents().hashCode());

        // 添加损坏值（如果有）
        if (stack.isDamaged()) {
            keyBuilder.append("_damage_").append(stack.getDamageValue());
        }

        return keyBuilder.toString();
    }
}