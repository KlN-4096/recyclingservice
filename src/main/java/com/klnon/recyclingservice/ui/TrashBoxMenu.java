package com.klnon.recyclingservice.ui;

import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.ui.UiUtils;
import com.klnon.recyclingservice.util.ui.PaymentValidator;
import com.klnon.recyclingservice.util.ui.ItemHandler;
import com.klnon.recyclingservice.Config;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

/**
 * 垃圾箱菜单 - 简化后专注于UI事件分发
 */
public class TrashBoxMenu extends ChestMenu {
    
    private final TrashBox trashBox;
    private final int trashSlots;

    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(UiUtils.getMenuTypeForRows(), containerId, playerInventory, trashBox, Config.TRASH_BOX_ROWS.get());
        this.trashBox = trashBox;
        this.trashSlots = Config.TRASH_BOX_ROWS.get() * 9;
    }

    @Override
    public void clicked(int slotId, int button, @Nonnull ClickType clickType, @Nonnull Player player) {
        // 邮费检查和扣除
        if (!PaymentValidator.validateAndProcessPayment(slotId, button, clickType, player, trashBox, slots, getCarried(), trashSlots)) {
            return; // 邮费不足，阻止操作
        }
        
        // 全面检查：如果是垃圾箱槽位且维度不允许放入，拦截所有可能的放入操作
        if (slotId >= 0 && !trashBox.isAllowedToPutIn()) {
            if (clickType==ClickType.QUICK_MOVE && slotId>=trashSlots)
                return;
            if ((!getCarried().isEmpty() || (clickType==ClickType.SWAP && slotId>trashSlots)) && slotId < trashSlots)
                return;
        }
        
        // 处理垃圾箱槽位的点击
        if (slotId >= 0 && slotId < trashSlots) {
            handleTrashBoxSlotClick(slotId, button, clickType, player);
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }
    
    /**
     * 处理垃圾箱槽位的点击事件
     */
    private void handleTrashBoxSlotClick(int slotId, int button, ClickType clickType, Player player) {
        Slot slot = slots.get(slotId);
        ItemStack carried = getCarried();
        ItemStack slotItem = slot.getItem();
        ItemStack result = ItemStack.EMPTY;
        
        if (clickType == ClickType.PICKUP && slotItem.getCount() >= slotItem.getMaxStackSize()) {
            result = ItemHandler.handlePickupClick(slot, slotItem, carried, button == 0, trashBox);
            setCarried(result);
        } else if (clickType == ClickType.SWAP && slotItem.getCount() > slotItem.getMaxStackSize()) {
            result = ItemHandler.handleSwapClick(slot, slotItem, player.getInventory().getItem(button), button, player, trashBox);
        } else if (clickType == ClickType.PICKUP_ALL) {
            result = ItemHandler.handleDoubleClick(slotItem, carried, trashBox, trashSlots);
            setCarried(result);
        } else if (clickType == ClickType.QUICK_MOVE) {
            result = quickMoveStack(player, slotId);
        } else if (clickType == ClickType.THROW && getCarried().isEmpty()) {
            handleThrowClick(slot, button, player);
            return;
        } else {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        
        // 统一更新受影响的物品
        UiUtils.updateTooltip(slotItem);
        UiUtils.updateTooltip(result);
    }
    
    /**
     * 处理丢弃物品的点击
     */
    private void handleThrowClick(Slot slot, int button, Player player) {
        ItemStack result = slot.getItem();
        int throwCount = button == 0 ? 1 : result.getCount();
        UiUtils.cleanItemStack(result);
        result = slot.safeTake(throwCount, Integer.MAX_VALUE, player);
        player.drop(result, true);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@Nonnull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack slotItem = slot.getItem();
        if (index < trashSlots) {
            // 从垃圾箱到玩家背包：最多64个
            int moveCount = Math.min(slotItem.getCount(), slotItem.getMaxStackSize());
            ItemStack moveItem = slotItem.copyWithCount(moveCount);

            if (moveItemStackTo(moveItem, trashSlots, slots.size(), true)) {
                UiUtils.updateSlotAfterMove(slot, moveCount);
                return ItemStack.EMPTY;
            }
        } else {
            // 从玩家背包到垃圾箱：手动实现
            if (!slotItem.isEmpty()) {
                ItemStack originalStack = slotItem.copy();

                // 尝试移动到垃圾箱槽位 (0 到 trashSlots-1)
                if (moveItemStackTo(slotItem, 0, trashSlots, false)) {
                    // 移动成功后更新槽位
                    if (slotItem.isEmpty()) {
                        slot.setByPlayer(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                    return originalStack;
                }
            }
        }

        return ItemStack.EMPTY;
    }
    
    @Override
    protected boolean moveItemStackTo(@Nonnull ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        // 移动到垃圾箱的特殊处理
        if (startIndex == 0 && endIndex <= trashSlots) {
            return ItemHandler.moveToTrashBox(stack, trashBox, trashSlots);
        }
        
        // 其他情况使用原版逻辑
        UiUtils.updateTooltip(stack);
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }
}