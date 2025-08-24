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

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

public class TrashBoxMenu extends ChestMenu {
    
    private final TrashBox trashBox;
    private final int trashSlots;
    
    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, trashBox, 6);
        this.trashBox = trashBox;
        this.trashSlots = getRowCount() * 9;
    }

    @Override
    public void clicked(int slotId, int button, @Nonnull ClickType clickType, @Nonnull Player player) {
        if (slotId >= 0 && slotId < trashSlots) {
            Slot slot = slots.get(slotId);
            ItemStack slotItem = slot.getItem();
            ItemStack carried = getCarried();
            ItemStack result = ItemStack.EMPTY;

            if (clickType == ClickType.PICKUP) {
                result = handlePickupClick(slot, slotItem, carried, button == 0);
            } else if (clickType == ClickType.SWAP) {
                result = handleSwapClick(slot, slotItem, player.getInventory().getItem(button), button, player);
            } else if (clickType == ClickType.PICKUP_ALL) {
                result = handleDoubleClick(slotItem, player);
            } else {
                super.clicked(slotId, button, clickType, player);
            }
            // 统一更新受影响的物品
            UiUtils.updateTooltip(result);
            // 统一更新点击的slot中的物品
//            UiUtils.updateTooltip(slotItem);
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }
    
    private ItemStack handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried, boolean isLeftClick) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品 -> 返回手上物品
            int maxMove = Math.min(64, slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 : 
                (slotItem.getCount() >= 64 ? 32 : (slotItem.getCount() + 1) / 2));
            
            setCarried(slotItem.copyWithCount(moveCount));
            //这里垃圾箱内的物品数量信息会更新,所以只需要return手上物品的数量信息
            UiUtils.updateSlotAfterMove(slot, moveCount);
            trashBox.setChanged();
            return getCarried();
            
        } else if (!carried.isEmpty()) {
            // 放物品到垃圾箱 -> 返回槽位物品
            if (slotItem.isEmpty()) {
                // 空槽位放入
                if (isLeftClick) {
                    // 左键：直接放入全部
                    slot.set(carried.copy());
                    setCarried(ItemStack.EMPTY);
                } else {
                    // 右键：放入1个
                    ItemStack singleItem = carried.copyWithCount(1);
                    slot.set(singleItem);
                    carried.shrink(1);
                    if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
                }
            } else if (ItemStack.isSameItem(carried, slotItem)) {
                // 相同物品合并
                int configLimit = Config.getItemStackMergeLimit();
                if (slotItem.getCount() < configLimit) {
                    if (isLeftClick) {
                        // 左键：尽可能合并至上限
                        int canAdd = Math.min(configLimit - slotItem.getCount(), carried.getCount());
                        slotItem.grow(canAdd);
                        carried.shrink(canAdd);
                        if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
                    } else {
                        // 右键：放入1个
                        slotItem.grow(1);
                        carried.shrink(1);
                        if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
                    }
                }
            } else {
                // 不同物品交换（仅当槽位物品不超过配置上限）
                if (slotItem.getCount() <= Config.getItemStackMergeLimit()) {
                    setCarried(slotItem.copy());
                    slot.set(carried.copy());
                }
            }
            trashBox.setChanged();
            //这里由于是从物品栏/快捷栏到垃圾箱,物品栏/快捷栏不用更新数量,原版自动更新,所以只需要返回垃圾箱格子中的物品
            return slot.getItem();
        }
        
        // 无操作情况
        return getCarried();
    }

    private ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, int button, Player player) {
        // 数字键交换 -> 往垃圾箱放物品，返回玩家物品栏中的物品
        if (slotItem.isEmpty()) {
            // 空槽位直接移动
            slot.set(swapItem.copy());
            player.getInventory().setItem(button, ItemStack.EMPTY);
        } else if (slotItem.getCount() <= Config.getItemStackMergeLimit()) {
            // 交换（仅当槽位物品不超过配置上限）
            int moveCount = Math.min(64, slotItem.getCount());
            swapItem = slotItem.copyWithCount(moveCount);
            player.getInventory().setItem(button, swapItem);
            UiUtils.updateSlotAfterMove(slot, moveCount);
        }
        trashBox.setChanged();
        return swapItem;
    }

    private ItemStack handleDoubleClick(ItemStack clickedItem, Player player) {
        //这个因为是双击,所以始终返回手上物品
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            // 空手双击，创建新的物品堆
            carried = clickedItem.copyWithCount(0);
            setCarried(carried);
        }
        
        if (!ItemStack.isSameItem(carried, clickedItem) && !clickedItem.isEmpty()) return getCarried();
        
        // 收集垃圾箱内所有相同物品到手持物品堆
        int maxStackSize = carried.getMaxStackSize();
        for (int i = 0; i < trashSlots && carried.getCount() < maxStackSize; i++) {
            ItemStack slotItem = trashBox.getItem(i);
            if (ItemStack.isSameItem(carried, slotItem)) {
                // 计算能取出多少（按正常规则：最多64个）
                int maxTake = Math.min(64, slotItem.getCount());
                int canAdd = maxStackSize - carried.getCount();
                int takeAmount = Math.min(maxTake, canAdd);
                
                if (takeAmount > 0) {
                    carried.grow(takeAmount);
                    UiUtils.updateSlotAfterMove(slots.get(i), takeAmount);
                }
            }
        }
        
        trashBox.setChanged();
        return getCarried();
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@Nonnull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        
        
        if (index < trashSlots) {
            // 从垃圾箱到玩家背包：最多64个
            ItemStack slotItem = slot.getItem();
            int moveCount = Math.min(slotItem.getCount(), 64);
            ItemStack moveItem = slotItem.copyWithCount(moveCount);
            // 成功清除LORA并成功移动
            if (moveItemStackTo(moveItem, trashSlots, slots.size(), true)) {
                UiUtils.updateSlotAfterMove(slot, moveCount);
                return ItemStack.EMPTY;
            }
        } else {
            // 从玩家背包到垃圾箱：原版会调用重写过的moveItemStackTo
            return super.quickMoveStack(player, index);
        }

        return ItemStack.EMPTY;
    }
    
    @Override
    protected boolean moveItemStackTo(@Nonnull ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        // 移动到垃圾箱的特殊处理
        if (startIndex == 0 && endIndex <= trashSlots) {
            return moveToTrashBox(stack);
        }
        
        // 其他情况使用原版逻辑
        UiUtils.updateTooltip(stack);
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
                    UiUtils.updateTooltip(slots.get(i).getItem());
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