package com.klnon.recyclingservice.content.cleanup.function;

import com.klnon.recyclingservice.foundation.utility.UiHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class EntityCompare {

    /**
     * 检查两个ItemStack是否为相同类型的复杂物品
     * 直接比较，避免生成字符串键的开销
     */
    public static boolean isSameItem(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return stack1.isEmpty() && stack2.isEmpty();
        }

        // 基础物品类型必须相同
        if (!stack1.getItem().equals(stack2.getItem())) {
            return false;
        }

        // 创建副本进行比较，避免修改原物品
        ItemStack temp1 = stack1.copy();
        ItemStack temp2 = stack2.copy();

        // 清理LORE
        UiHelper.cleanItemStack(temp1);
        UiHelper.cleanItemStack(temp2);

        // 比较损坏值和组件
        return temp1.getDamageValue() == temp2.getDamageValue() && temp1.getComponents().equals(temp2.getComponents());
    }

    /**
     * 为复杂物品生成唯一键，用于区分不同的复杂物品
     */
    public static String generateItemHash(ItemStack stack) {
        //先清洗掉我们的LORA
        UiHelper.cleanItemStack(stack);

        // 创建临时副本并移除LORE组件
        ItemStack tempStack = stack.copy();
        tempStack.remove(DataComponents.LORE);

        // 使用多个关键属性计算hash，减少冲突概率
        return String.valueOf(Objects.hash(
                tempStack.getItem(),           // 物品类型
                tempStack.getComponents(),     // 所有组件（除LORE外）
                tempStack.getDamageValue()     // 损坏值
        ));
    }
}