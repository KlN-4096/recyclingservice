package com.klnon.recyclingservice.util.cleanup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.ui.UiUtils;

public class ItemMerge {
    
    /**
     * 简化的零拷贝合并：使用Map统计，延迟创建ItemStack
     * @param stacks 输入的ItemStack列表
     * @return 合并后的ItemStack列表
     */
    public static List<ItemStack> combine(List<ItemStack> stacks) {
        // 使用Map统计：键 -> (模板ItemStack, 总数量)
        Map<String, ItemStack> templates = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        
        // 使用Stream API统计相同物品数量
        stacks.stream()
            .filter(stack -> !stack.isEmpty())
            .forEach(stack -> {
                String key = ItemFilter.isComplexItem(stack) ? 
                    generateComplexItemKey(stack) : stack.getItem().toString();
                templates.putIfAbsent(key, stack);
                counts.merge(key, stack.getCount(), Integer::sum);
            });
        
        List<ItemStack> result = new ArrayList<>();
        
        // 创建最终的ItemStack列表
        for (String key : templates.keySet()) {
            ItemStack template = templates.get(key);
            int totalCount = counts.get(key);
            int mergeLimit = Config.getItemStackMultiplier(template);
            
            // 按限制分组创建ItemStack
            while (totalCount > 0) {
                int count = Math.min(totalCount, mergeLimit);
                ItemStack newStack = template.copy();
                newStack.setCount(count);
                UiUtils.updateTooltip(newStack);
                result.add(newStack);
                totalCount -= count;
            }
        }
        
        return result;
    }

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