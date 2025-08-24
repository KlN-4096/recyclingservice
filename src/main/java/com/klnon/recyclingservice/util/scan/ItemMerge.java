package com.klnon.recyclingservice.util.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.klnon.recyclingservice.util.other.UiUtils;
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
                    UiUtils.updateTooltip(newStack);
                    result.add(newStack);
                    remaining -= count;
                }
            }
        }
        
        return result;
    }
}