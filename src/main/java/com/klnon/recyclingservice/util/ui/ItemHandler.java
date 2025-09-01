package com.klnon.recyclingservice.util.ui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;

/**
 * 物品处理器 - 处理垃圾箱和玩家背包之间的物品移动逻辑
 * 职责：
 * - 处理各种点击操作的物品移动
 * - 管理物品堆叠和合并
 * - 处理物品移动到垃圾箱的逻辑
 */
public class ItemHandler {

    /**
     * 处理拾取点击（左键/右键点击）
     */
    public static ItemStack handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried, 
                                           boolean isLeftClick, TrashBox trashBox) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品 -> 返回手上物品
            int maxMove = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 : 
                (slotItem.getCount() >= slotItem.getMaxStackSize() ? slotItem.getMaxStackSize()/2 : (slotItem.getCount() + 1) / 2));
            
            ItemStack result = slotItem.copyWithCount(moveCount);
            UiUtils.updateSlotAfterMove(slot, moveCount);
            trashBox.setChanged();
            return result;
            
        } else if (!carried.isEmpty()) {
            // 放物品到垃圾箱 -> 返回槽位物品
            if (slotItem.isEmpty()) {
                // 空槽位放入
                if (isLeftClick) {
                    // 左键：直接放入全部
                    slot.set(carried.copy());
                    return ItemStack.EMPTY; // 清空手持
                } else {
                    // 右键：放入1个
                    ItemStack singleItem = carried.copyWithCount(1);
                    slot.set(singleItem);
                    ItemStack remainingCarried = carried.copy();
                    remainingCarried.shrink(1);
                    return remainingCarried.isEmpty() ? ItemStack.EMPTY : remainingCarried;
                }
            } else if (ItemMerge.isSameItem(carried, slotItem)) {
                // 相同物品合并
                int configLimit = Config.getItemStackMultiplier(slotItem);
                if (slotItem.getCount() < configLimit) {
                    ItemStack remainingCarried = carried.copy();
                    if (isLeftClick) {
                        // 左键：尽可能合并至上限
                        int canAdd = Math.min(configLimit - slotItem.getCount(), carried.getCount());
                        slotItem.grow(canAdd);
                        remainingCarried.shrink(canAdd);
                    } else {
                        // 右键：放入1个
                        slotItem.grow(1);
                        remainingCarried.shrink(1);
                    }
                    return remainingCarried.isEmpty() ? ItemStack.EMPTY : remainingCarried;
                }
            } else {
                // 不同物品交换（仅当槽位物品不超过配置上限）
                if (slotItem.getCount() <= slotItem.getMaxStackSize()) {
                    ItemStack result = slotItem.copy();
                    slot.set(carried.copy());
                    return result;
                }
            }
            trashBox.setChanged();
            return slot.getItem();
        }
        
        return carried; // 无操作情况
    }

    /**
     * 处理数字键交换点击
     */
    public static ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, 
                                          int button, Player player, TrashBox trashBox) {
        ItemStack result = swapItem;
        
        if (slotItem.isEmpty()) {
            // 空槽位直接移动
            slot.set(swapItem.copy());
            player.getInventory().setItem(button, ItemStack.EMPTY);
            result = ItemStack.EMPTY;
        } else if (slotItem.getCount() <= slotItem.getMaxStackSize() || swapItem.isEmpty()) {
            // 交换（仅当槽位物品不超过配置上限）
            int moveCount = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            result = slotItem.copyWithCount(moveCount);
            player.getInventory().setItem(button, result);
            UiUtils.updateSlotAfterMove(slot, moveCount);
        }
        
        trashBox.setChanged();
        return result;
    }

    /**
     * 处理双击收集
     */
    public static ItemStack handleDoubleClick(ItemStack clickedItem, ItemStack carried, TrashBox trashBox, int trashSlots) {
        ItemStack result = carried;
        
        if (carried.isEmpty()) {
            // 空手双击，创建新的物品堆
            result = clickedItem.copyWithCount(0);
        }
        
        if (!ItemMerge.isSameItem(result, clickedItem) && !clickedItem.isEmpty()) {
            return result;
        }
        
        // 收集垃圾箱内所有相同物品到手持物品堆
        int maxStackSize = result.getMaxStackSize();
        for (int i = 0; i < trashSlots && result.getCount() < maxStackSize; i++) {
            ItemStack slotItem = trashBox.getItem(i);
            if (ItemMerge.isSameItem(result, slotItem)) {
                // 计算能取出多少（按正常规则：最多64个）
                int maxTake = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
                int canAdd = maxStackSize - result.getCount();
                int takeAmount = Math.min(maxTake, canAdd);
                
                if (takeAmount > 0) {
                    result.grow(takeAmount);
                    // 这里需要创建临时slot来调用updateSlotAfterMove
                    Slot tempSlot = new Slot(trashBox, i, 0, 0) {};
                    UiUtils.updateSlotAfterMove(tempSlot, takeAmount);
                }
            }
        }
        
        trashBox.setChanged();
        return result;
    }

    /**
     * 移动物品到垃圾箱
     */
    public static boolean moveToTrashBox(ItemStack stack, TrashBox trashBox, int trashSlots) {
        if (stack.isEmpty()) return false;
        
        boolean moved = false;
        int configLimit = Config.getItemStackMultiplier(stack);
        
        // 按顺序搜索：空位和相同物品谁先找到就用谁
        for (int i = 0; i < trashSlots && !stack.isEmpty(); i++) {
            ItemStack slotItem = trashBox.getItem(i);
            
            if (slotItem.isEmpty()) {
                // 找到空槽位，直接放入
                trashBox.setItem(i, stack.copy());
                stack.setCount(0);
                moved = true;
                break;
            } else if (ItemMerge.isSameItem(stack, slotItem)) {
                // 找到相同物品，尝试合并
                int canAdd = configLimit - slotItem.getCount();
                if (canAdd > 0) {
                    int addAmount = Math.min(canAdd, stack.getCount());
                    slotItem.grow(addAmount);
                    UiUtils.updateTooltip(slotItem);
                    stack.shrink(addAmount);
                    moved = true;
                    // 注意：这里不break，因为可能还有剩余物品需要继续寻找下一个槽位
                }
            }
        }
        
        if (moved) {
            trashBox.setChanged();
        }
        
        return moved;
    }
}