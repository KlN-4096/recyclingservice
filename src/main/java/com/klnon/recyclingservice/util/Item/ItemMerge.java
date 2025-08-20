package com.klnon.recyclingservice.util.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

public class ItemMerge {
    /**
     * 合并ItemStack列表，相同物品堆叠至上限
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
            if (ItemFilter.isComplexItem(template)) {
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

    /**
     * 合并2个ItemStack，相同物品堆叠至上限
     * @param item1,item2 输入的2个ItemStack
     * @return 合并后的ItemStack列表
     */
    public static List<ItemStack> combine(ItemStack item1, ItemStack item2) {
        List<ItemStack> result = new ArrayList<>();
        
        // 相同物品，进行合并
        int totalCount = item1.getCount() + item2.getCount();
        
        // 可堆叠物品按6400上限分组
        while (totalCount > 0) {
            ItemStack newStack = item1.copy();
            int count = Math.min(totalCount, 6400);
            newStack.setCount(count);
            result.add(newStack);
            totalCount -= count;
        }
        
        return result;
    }

    /**
     * 智能添加物品到容器，从前往后搜寻合并
     * @param container 目标容器
     * @param itemToAdd 要添加的物品
     * @return 是否完全添加成功
     */
    public static boolean addItemSmart(NonNullList<ItemStack> container, ItemStack itemToAdd) {
        if (itemToAdd.isEmpty()) {
            return false;
        }
        
        ItemStack remaining = ItemTooltip.cleanItemStack(itemToAdd.copy());
        
        // 从前往后搜寻
        for (int i = 0; i < container.size() && !remaining.isEmpty(); i++) {
            ItemStack current = container.get(i);
            
            // 情况1：空格子，直接放入
            if (current.isEmpty()) {
                container.set(i, ItemTooltip.enhanceTooltip(remaining.copy()));
                remaining = ItemStack.EMPTY;
                break;
            }
            
            // 情况2：相同物品，尝试合并
            ItemStack cleanCurrent = ItemTooltip.cleanItemStack(current.copy());
            if (ItemStack.isSameItemSameComponents(cleanCurrent, remaining)&&!ItemFilter.isComplexItem(cleanCurrent)) {
                int canAdd = 6400 - cleanCurrent.getCount();
                if (canAdd > 0) {
                    int addAmount = Math.min(canAdd, remaining.getCount());
                    cleanCurrent.setCount(cleanCurrent.getCount() + addAmount);
                    container.set(i, ItemTooltip.enhanceTooltip(cleanCurrent));
                    
                    remaining.shrink(addAmount);
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }
        }
        
        return remaining.isEmpty();
    }
}
