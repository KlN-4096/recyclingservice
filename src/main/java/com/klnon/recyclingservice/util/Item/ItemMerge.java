package com.klnon.recyclingservice.util.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.UiUtils;

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
            
            String key = stack.getItem().toString();
            mergeMap.computeIfAbsent(key, k -> new MergeInfo()).addStack(stack);
        }
        
        List<ItemStack> result = new ArrayList<>();
        int mergeLimit = Config.getItemStackMergeLimit();
        
        // 只在最终需要时创建ItemStack对象
        for (MergeInfo info : mergeMap.values()) {
            if (ItemFilter.isComplexItem(info.template)) {
                // 复杂物品：创建单个堆叠
                for (int i = 0; i < info.totalCount; i++) {
                    ItemStack newStack = info.template.copy();
                    newStack.setCount(1);
                    result.add(newStack);
                }
            } else {
                // 可堆叠物品：按配置上限分组
                int remaining = info.totalCount;
                while (remaining > 0) {
                    ItemStack newStack = info.template.copy();
                    int count = Math.min(remaining, mergeLimit);
                    newStack.setCount(count);
                    result.add(newStack);
                    remaining -= count;
                }
            }
        }
        
        return result;
    }
    

    /**
     * 合并2个ItemStack，相同物品堆叠至上限
     * @param item1 第一个ItemStack
     * @param item2 第二个ItemStack
     * @return 合并后的ItemStack列表
     */
    public static List<ItemStack> combine(ItemStack item1, ItemStack item2) {
        List<ItemStack> result = new ArrayList<>();
        int totalCount = item1.getCount() + item2.getCount();
        int mergeLimit = Config.getItemStackMergeLimit();
        
        // 按配置上限分组
        while (totalCount > 0) {
            ItemStack newStack = item1.copy();
            int count = Math.min(totalCount, mergeLimit);
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
        if (itemToAdd.isEmpty()) return false;
        
        ItemStack remaining = UiUtils.cleanItemStack(itemToAdd.copy());
        
        // 从前往后搜寻
        for (int i = 0; i < container.size() && !remaining.isEmpty(); i++) {
            ItemStack current = container.get(i);
            
            if (current.isEmpty()) {
                // 空格子，直接放入
                container.set(i, UiUtils.enhanceTooltip(remaining.copy()));
                remaining = ItemStack.EMPTY;
            } else {
                // 尝试合并
                ItemStack cleanCurrent = UiUtils.cleanItemStack(current.copy());
                if (ItemStack.isSameItemSameComponents(cleanCurrent, remaining) && 
                    !ItemFilter.isComplexItem(cleanCurrent)) {
                    int canAdd = Config.getItemStackMergeLimit() - cleanCurrent.getCount();
                    if (canAdd > 0) {
                        int addAmount = Math.min(canAdd, remaining.getCount());
                        cleanCurrent.setCount(cleanCurrent.getCount() + addAmount);
                        container.set(i, UiUtils.enhanceTooltip(cleanCurrent));
                        remaining.shrink(addAmount);
                    }
                }
            }
        }
        
        return remaining.isEmpty();
    }
}