package com.klnon.recyclingservice.ui;

import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.other.UiUtils;
import com.klnon.recyclingservice.Config;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TrashBoxMenu extends ChestMenu {
    
    private final TrashBox trashBox;
    private final int trashSlots;
    
    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, trashBox, 6);
        this.trashBox = trashBox;
        this.trashSlots = getRowCount() * 9;
    }
    
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // 拦截垃圾箱槽位的特殊操作
        if (slotId >= 0 && slotId < trashSlots && needsCustomHandling(clickType, button)) {
            handleTrashBoxClick(slotId, button, clickType, player);
            return;
        }
        
        // 其他操作使用原版逻辑
        super.clicked(slotId, button, clickType, player);
    }
    
    private boolean needsCustomHandling(ClickType clickType, int button) {
        return (clickType == ClickType.PICKUP && (button == 0 || button == 1)) || 
               (clickType == ClickType.SWAP && (button >= 0 && button <= 40));
    }
    
    private void handleTrashBoxClick(int slotId, int button, ClickType clickType, Player player) {
        Slot slot = slots.get(slotId);
        ItemStack slotItem = slot.getItem();
        ItemStack carried = getCarried();
        
        if (clickType == ClickType.PICKUP) {
            handlePickupClick(slot, slotItem, carried, button == 0);
        } else if (clickType == ClickType.SWAP) {
            handleSwapClick(slot, slotItem, player.getInventory().getItem(button), button, player);
        }
    }
    
    private void handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried, boolean isLeftClick) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品
            int maxMove = Math.min(64, slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 : 
                (slotItem.getCount() >= 64 ? 32 : (slotItem.getCount() + 1) / 2));
            
            setCarried(slotItem.copyWithCount(moveCount));
            UiUtils.updateSlotAfterMove(slot, moveCount);
            
        } else if (!carried.isEmpty()) {
            // 放物品到垃圾箱
            if (slotItem.isEmpty()) {
                // 空槽位直接放入
                slot.set(carried.copy());
                setCarried(ItemStack.EMPTY);
            } else if (ItemStack.isSameItem(carried, slotItem)) {
                // 相同物品合并
                int configLimit = Config.getItemStackMergeLimit();
                int canAdd = Math.min(configLimit - slotItem.getCount(), carried.getCount());
                if (canAdd > 0) {
                    slotItem.grow(canAdd);
                    carried.shrink(canAdd);
                    if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
                }
            } else {
                // 不同物品交换（仅当槽位物品不超过配置上限）
                if (slotItem.getCount() <= Config.getItemStackMergeLimit()) {
                    setCarried(slotItem.copy());
                    slot.set(carried.copy());
                }
            }
        }
        trashBox.setChanged();
    }
    
    private void handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, int button, Player player) {
        if (slotItem.isEmpty()) {
            // 空槽位直接移动
            slot.set(swapItem.copy());
            player.getInventory().setItem(button, ItemStack.EMPTY);
        } else if (slotItem.getCount() <= Config.getItemStackMergeLimit()) {
            // 交换（仅当槽位物品不超过配置上限）
            int moveCount = Math.min(64, slotItem.getCount());
            player.getInventory().setItem(button, slotItem.copyWithCount(moveCount));
            UiUtils.updateSlotAfterMove(slot, moveCount);
            if (!swapItem.isEmpty()) {
                slot.set(swapItem.copy());
            }
        }
        trashBox.setChanged();
    }
    
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        
        
        if (index < trashSlots) {
            // 先清除tooltip再移到玩家背包
            ItemStack slotItem = UiUtils.cleanItemStack(slot.getItem());
            // 从垃圾箱到玩家背包：最多64个
            int moveCount = Math.min(slotItem.getCount(), 64);
            ItemStack moveItem = slotItem.copyWithCount(moveCount);
            
            if (moveItemStackTo(moveItem, trashSlots, slots.size(), true)) {
                UiUtils.updateSlotAfterMove(slot, moveCount);
                return ItemStack.EMPTY;
            }
        } else {
            // 从玩家背包到垃圾箱：智能放置
            return super.quickMoveStack(player, index);
        }
        
        return ItemStack.EMPTY;
    }
    
    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        // 移动到垃圾箱的特殊处理
        if (startIndex == 0 && endIndex <= trashSlots) {
            return moveToTrashBox(stack);
        }
        
        // 其他情况使用原版逻辑
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }

    private boolean moveToTrashBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        boolean moved = false;
        int configLimit = Config.getItemStackMergeLimit();
        
        // 按顺序搜索：空位和相同物品谁先找到就用谁
        for (int i = 0; i < trashSlots && !stack.isEmpty(); i++) {
            ItemStack slotItem = trashBox.getItem(i);
            
            if (slotItem.isEmpty()) {
                // 找到空槽位，直接放入
                trashBox.setItem(i, stack.copy());
                stack.setCount(0);
                moved = true;
                break;
            } else if (ItemStack.isSameItem(stack, slotItem)) {
                // 找到相同物品，尝试合并
                int canAdd = configLimit - slotItem.getCount();
                if (canAdd > 0) {
                    int addAmount = Math.min(canAdd, stack.getCount());
                    slotItem.grow(addAmount);
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