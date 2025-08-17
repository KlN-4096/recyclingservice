package com.klnon.recyclingservice.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

public class ItemMerge {
    /**
     * 合并ItemStack列表，相同物品堆叠至999上限
     * @param stacks 输入的ItemStack列表
     * @return 合并后的ItemStack列表
     */
    public static List<ItemStack> combine(List<ItemStack> stacks) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, ItemStack> templates = new HashMap<>();
        
        // 统计相同物品数量
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            
            String key = stack.getItem().toString();
            counts.merge(key, stack.getCount(), Integer::sum);
            templates.putIfAbsent(key, stack.copy());
        }
        
        List<ItemStack> result = new ArrayList<>();
        
        // 生成合并后的ItemStack
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ItemStack template = templates.get(entry.getKey());
            int total = entry.getValue();
            
            // 不可堆叠物品(maxStackSize=1)保持原样
            if (template.getMaxStackSize() == 1) {
                for (int i = 0; i < total; i++) {
                    ItemStack single = template.copy();
                    single.setCount(1);
                    result.add(single);
                }
            } else {
                // 可堆叠物品按6400上限分组
                while (total > 0) {
                    ItemStack newStack = template.copy();
                    int count = Math.min(total, 6400);
                    newStack.setCount(count);
                    result.add(newStack);
                    total -= count;
                }
            }
        }
        
        return result;
    }
}
