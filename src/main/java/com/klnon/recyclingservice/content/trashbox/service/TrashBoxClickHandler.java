package com.klnon.recyclingservice.content.trashbox.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxMenu;
import com.klnon.recyclingservice.foundation.utility.UiHelper;
import com.klnon.recyclingservice.content.trashbox.data.TrashBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 垃圾箱点击处理器 - 处理所有点击相关的逻辑
 */
public record TrashBoxClickHandler(TrashBox trashBox, TrashBoxMenu menu) {



    /**
     * 处理垃圾箱槽位的点击事件
     */
    public void handleTrashBoxSlotClick(int slotId, int button, ClickType clickType, Player player) {
        Slot slot = menu.slots.get(slotId);
        ItemStack carried = menu.getCarried();
        ItemStack slotItem = slot.getItem();
        ItemStack beforeItem = slotItem.copy();
        int beforeTotalSame = -1;
        int expectedExtractCount = 0;
        int expectedBaseFlag = 0;
        TrashPaymentHandler.ExtractInfo extractInfo = TrashPaymentHandler.getExtractInfo(
                menu.getTrashSlots(), trashBox, slotId, button, clickType, player, menu.slots, carried);
        int expectedCost = 0;
        if (clickType == ClickType.PICKUP_ALL && !slotItem.isEmpty()) {
            beforeTotalSame = getSameItemTotal(slotItem);
            expectedExtractCount = getPickupAllExpectedCount(slotItem, carried, beforeTotalSame);
            expectedBaseFlag = trashBox.getBaseFlag(slotItem);
        } else if (extractInfo != null && extractInfo.count() > 0) {
            expectedExtractCount = extractInfo.count();
            expectedBaseFlag = extractInfo.baseFlag();
        }
        if (expectedExtractCount > 0) {
            ResourceLocation trashDim = trashBox.getData().getDimensionId();
            expectedCost = TrashPaymentHandler.previewExtractCost(player, trashDim,
                    expectedExtractCount, expectedBaseFlag);
            if (expectedCost > 0 && !TrashPaymentHandler.hasEnoughPaymentItems(player, expectedCost)) {
                TrashPaymentHandler.sendPaymentErrorMessage(player, expectedCost);
                return;
            }
        }
        ItemStack result;

        // 直接处理各种点击类型的逻辑
        if (clickType == ClickType.PICKUP) {
            result = handlePickupClick(slot, slotItem, carried, button == 0);
            menu.setCarried(result);
        } else if (clickType == ClickType.SWAP && slotItem.getCount() > slotItem.getMaxStackSize()) {
            result = handleSwapClick(slot, slotItem, player.getInventory().getItem(button),
                    button, player);
        } else if (clickType == ClickType.PICKUP_ALL) {
            result = handleDoubleClick(slotItem, carried);
            menu.setCarried(result);
        } else if (clickType == ClickType.QUICK_MOVE) {
            result = menu.quickMoveStack(player, slotId);
        } else if (clickType == ClickType.THROW && menu.getCarried().isEmpty()) {
            result = handleThrowClick(slot, button, player);
        } else {
            // 委托给父类处理
            menu.superClicked(slotId, button, clickType, player);
            return;
        }

        // 统一更新受影响的物品
        ItemStack updatedSlotItem = slot.getItem();
        UiHelper.updateTooltip(updatedSlotItem);
        UiHelper.updateTooltip(result);

        int actualExtractCount = expectedExtractCount;
        int actualBaseFlag = expectedBaseFlag;
        if (clickType == ClickType.PICKUP_ALL && beforeTotalSame >= 0) {
            actualExtractCount = beforeTotalSame - getSameItemTotal(beforeItem);
            actualBaseFlag = trashBox.getBaseFlag(beforeItem);
        }
        if (expectedCost > 0 && didExtract(beforeItem, updatedSlotItem)) {
            TrashPaymentHandler.finalizeExtractPayment(player, trashBox.getData().getDimensionId(),
                    actualExtractCount, actualBaseFlag);
        }
    }

    /**
     * 处理拾取点击（左键/右键点击）
     */
    private ItemStack handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried,
                                        boolean isLeftClick) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品
            int maxMove = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 :
                    (slotItem.getCount() >= slotItem.getMaxStackSize() ?
                            slotItem.getMaxStackSize() / 2 : (slotItem.getCount() + 1) / 2));

            ItemStack result = slotItem.copyWithCount(moveCount);
            updateSlotAfterMove(slot, moveCount);
            trashBox.setChanged();
            return result;

        } else if (!carried.isEmpty()) {
            // 放物品到垃圾箱
            if (slotItem.isEmpty()) {
                // 空槽位：左键放全部，右键放一个
                ItemStack toAdd = isLeftClick ? carried : carried.copyWithCount(1);
                int moveCount = toAdd.getCount();
                if (trashBox.addItem(toAdd, slot.index)) {
                    carried.shrink(moveCount);
                    return carried.isEmpty() ? ItemStack.EMPTY : carried;
                }
            } else if (CleanupManager.isSameItem(carried, slotItem)) {
                // 相同物品：尝试合并
                int stackLimit = Config.getItemStackMultiplier(slotItem);
                int canAdd = stackLimit - slotItem.getCount();
                if (canAdd <= 0) {
                    return carried;
                }
                int moveCount = isLeftClick ? Math.min(canAdd, carried.getCount()) : 1;
                slotItem.grow(moveCount);
                carried.shrink(moveCount);
                return carried.isEmpty() ? ItemStack.EMPTY : carried;
            } else {
                // 不同物品：交换
                if (slotItem.getCount() <= slotItem.getMaxStackSize()) {
                    ItemStack result = slotItem.copy();
                    slot.set(carried.copy());
                    return result;
                }
            }
            return carried;
        }

        return carried;
    }

    /**
     * 处理数字键交换点击
     */
    private ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem,
                                      int button, Player player) {
        if (slotItem.isEmpty() && !swapItem.isEmpty()) {
            if (trashBox.addItem(swapItem, slot.index)) {
                player.getInventory().setItem(button, ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
        } else if (!slotItem.isEmpty() && swapItem.isEmpty()) {
            // 有物品：交换
            int moveCount = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            ItemStack result = slotItem.copyWithCount(moveCount);
            player.getInventory().setItem(button, result);
            updateSlotAfterMove(slot, moveCount);
            return result;
        }

        return swapItem;
    }

    /**
     * 处理双击收集
     */
    private ItemStack handleDoubleClick(ItemStack clickedItem, ItemStack carried) {
        ItemStack result = carried;

        if (carried.isEmpty()) {
            result = clickedItem.copyWithCount(0);
        }

        if (!CleanupManager.isSameItem(result, clickedItem) && !clickedItem.isEmpty()) {
            return result;
        }

        // 收集垃圾箱内所有相同物品
        int maxStackSize = result.getMaxStackSize();
        List<Integer> sameItemSlots = trashBox.getData().getSameItemSlots(result);

        for (Integer slotIndex : sameItemSlots) {
            if (result.getCount() >= maxStackSize) break;

            ItemStack slotItem = trashBox.getItem(slotIndex);
            int maxTake = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int canAdd = maxStackSize - result.getCount();
            int takeAmount = Math.min(maxTake, canAdd);

            if (takeAmount > 0) {
                result.grow(takeAmount);
                Slot tempSlot = new Slot(trashBox, slotIndex, 0, 0) {
                };
                updateSlotAfterMove(tempSlot, takeAmount);
            }
        }

        trashBox.setChanged();
        return result;
    }

    /**
     * 处理丢弃物品的点击
     */
    private ItemStack handleThrowClick(Slot slot, int button, Player player) {
        ItemStack result = slot.getItem();
        int throwCount = button == 0 ? 1 : Math.min(result.getCount(), result.getMaxStackSize());
        result = slot.safeTake(throwCount, Integer.MAX_VALUE, player);
        player.drop(result, true);
        return result;
    }

    /**
     * 在物品交换完毕后更新垃圾箱内物品数量
     */
    public void updateSlotAfterMove(Slot slot, int moveCount) {
        ItemStack slotItem = slot.getItem();
        //这里检查一下是否是原版的最大数量上限,比如药水,护甲等
        moveCount = Math.min(moveCount, slotItem.getMaxStackSize());
        ItemStack beforeItem = slotItem.copy();
        if (slotItem.getCount() <= moveCount) {
            slot.set(ItemStack.EMPTY);
            trashBox.getData().updateIndex(slot.index, beforeItem, ItemStack.EMPTY);
        } else{
            slotItem.shrink(moveCount);
            trashBox.getData().updateIndex(slot.index, beforeItem, slotItem);
        }
    }

    /**
     * 检查当前维度是否允许玩家主动放入物品到垃圾箱
     */
    public boolean isAllowedToPutIn(Player player) {
        return Config.isDimensionAllowPutIn(trashBox.getData().getDimensionId().toString(),
                player.level().dimension().location().toString());
    }

    private boolean didExtract(ItemStack beforeItem, ItemStack afterItem) {
        if (beforeItem.isEmpty()) {
            return false;
        }
        if (afterItem.isEmpty()) {
            return true;
        }
        if (!CleanupManager.isSameItem(beforeItem, afterItem)) {
            return true;
        }
        return afterItem.getCount() < beforeItem.getCount();
    }

    private int getSameItemTotal(ItemStack item) {
        int total = 0;
        for (Integer slotIndex : trashBox.getData().getSameItemSlots(item)) {
            total += trashBox.getItem(slotIndex).getCount();
        }
        return total;
    }

    private int getPickupAllExpectedCount(ItemStack clickedItem, ItemStack carried, int totalBefore) {
        int currentCount = carried.isEmpty() ? 0 : carried.getCount();
        int maxAdd = clickedItem.getMaxStackSize() - currentCount;
        if (maxAdd <= 0) {
            return 0;
        }
        return Math.min(totalBefore, maxAdd);
    }
}
