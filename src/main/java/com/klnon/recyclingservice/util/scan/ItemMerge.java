package com.klnon.recyclingservice.util.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.klnon.recyclingservice.util.other.UiUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;

public class ItemMerge {
    /**
     * 合并信息辅助类,由于是同种物品,所以只需要合并数量
     */
    private static class MergeInfo {
        ItemStack template;
        int totalCount = 0;
        
        void addStack(ItemStack stack) {
            if (template == null) {
                template = stack; // 保存引用，不拷贝
            }
            totalCount += stack.getCount();
        }
    }
    
    /**
     * 零拷贝合并：统计相同物品但延迟创建ItemStack
     * @param stacks 输入的ItemStack引用列表
     * @return 合并计划（延迟到真正需要时才创建ItemStack对象）
     */
    public static List<ItemStack> combine(List<ItemStack> stacks) {
        Map<String, MergeInfo> mergeMap = new HashMap<>();
        
        // 统计相同物品数量，但不创建新对象
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            
            // 根据物品类型选择合适的键生成策略
            String key = ItemFilter.isComplexItem(stack) ? 
                generateComplexItemKey(stack) : 
                stack.getItem().toString();
            mergeMap.computeIfAbsent(key, k -> new MergeInfo()).addStack(stack);
        }
        
        List<ItemStack> result = new ArrayList<>();
        
        // 只在最终需要时创建ItemStack对象
        for (MergeInfo info : mergeMap.values()) {
            // 所有物品都按自定义上限分组（无论是否复杂物品）
            int remaining = info.totalCount;
            int mergeLimit = Config.getItemStackMultiplier(info.template);
            
            while (remaining > 0) {
                ItemStack newStack = info.template.copy();
                int count = Math.min(remaining, mergeLimit);
                newStack.setCount(count);
                UiUtils.updateTooltip(newStack);
                result.add(newStack);
                remaining -= count;
            }
        }
        
        return result;
    }

    public static Boolean isSameItem(ItemStack stack1, ItemStack stack2) {
        return generateComplexItemKey(stack1).equals(generateComplexItemKey(stack2));
    }

    /**
     * 为复杂物品生成唯一键，用于区分不同的复杂物品
     * 完全一致的复杂物品（包括所有NBT数据）会生成相同的键
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