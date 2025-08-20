package com.klnon.recyclingservice.ui;

import java.util.List;

import javax.annotation.Nonnull;

import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.Item.ItemFilter;
import com.klnon.recyclingservice.util.Item.ItemMerge;
import com.klnon.recyclingservice.util.Item.ItemTooltip;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 垃圾箱自定义菜单 - 简化版本
 * 遵循KISS原则：减少重复代码，统一处理逻辑
 */
public class TrashBoxMenu extends ChestMenu {
    
    private static final int MAX_QUICK_MOVE = 64;
    private final UniversalTrashContainer container;
    
    public TrashBoxMenu(int containerId, Inventory playerInventory, UniversalTrashContainer container) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
        this.container = container;
    }
    
    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        
        ItemStack slotItem = slot.getItem();
        int trashSlots = getRowCount() * 9;
        
        if (index < trashSlots) {
            // 从垃圾箱移动到玩家背包
            return moveFromTrashToPlayer(slot, slotItem, trashSlots);
        } else {
            // 从玩家背包移动到垃圾箱
            return moveFromPlayerToTrash(slot, slotItem, trashSlots);
        }
    }
    
    private ItemStack moveFromTrashToPlayer(Slot slot, ItemStack slotItem, int trashSlots) {
        int moveCount = Math.min(slotItem.getCount(), MAX_QUICK_MOVE);
        ItemStack moveItem = slotItem.copyWithCount(moveCount);
        ItemStack cleanItem = ItemTooltip.cleanItemStack(moveItem);
        
        if (!moveItemStackTo(cleanItem, trashSlots, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        
        updateSlotAfterMove(slot, slotItem, moveCount);
        return ItemStack.EMPTY; // 阻止连续提取
    }
    
    private ItemStack moveFromPlayerToTrash(Slot slot, ItemStack slotItem, int trashSlots) {
        ItemStack original = slotItem.copy();
        
        if (moveItemStackTo(slotItem, 0, trashSlots, false)) {
            updateSlotAfterMove(slot, slotItem, 0);
            return original;
        }
        
        return ItemStack.EMPTY;
    }
    
    private void updateSlotAfterMove(Slot slot, ItemStack slotItem, int moveCount) {
        if (moveCount == 0 || slotItem.getCount() <= moveCount) {
            slot.set(ItemStack.EMPTY);
        } else {
            slotItem.shrink(moveCount);
            slot.set(ItemTooltip.enhanceTooltip(slotItem));
        }
    }

    @Override
    protected boolean moveItemStackTo(@Nonnull ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        // 移动到垃圾箱使用智能合并
        if (startIndex == 0 && endIndex <= getRowCount() * 9) {
            return moveToTrashBox(stack);
        }
        
        // 其他情况使用原版逻辑
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }
    
    private boolean moveToTrashBox(ItemStack stack) {
        TrashBox trashBox = container.getTrashBox();
        int originalCount = stack.getCount();
        
        if (ItemMerge.addItemSmart(trashBox.items, stack)) {
            container.setChanged();
            return true;
        }
        
        // 检查是否部分成功
        int movedCount = originalCount - stack.getCount();
        if (movedCount > 0) {
            container.setChanged();
            return true;
        }
        
        return false;
    }

    @Override
    public void clicked(int slotId, int button, @Nonnull ClickType clickType, @Nonnull Player player) {
        boolean hadCarriedItem = !getCarried().isEmpty();
        
        super.clicked(slotId, button, clickType, player);
        
        // 只处理垃圾箱槽位的左键放置
        if (slotId >= 0 && slotId < getRowCount() * 9 && clickType == ClickType.PICKUP && button == 0 && hadCarriedItem) {
            handlePostMerge(slotId);
        }
    }
    

    private void handlePostMerge(int slotId) {
        ItemStack carried = getCarried();
        Slot slot = slots.get(slotId);
        ItemStack slotItem = slot.getItem();
        
        // 检查是否需要合并
        if (carried.isEmpty() || slotItem.isEmpty() || !ItemStack.isSameItem(carried, slotItem) || ItemFilter.isComplexItem(carried)) {
            return;
        }
        
        // 执行合并
        ItemStack cleanCarried = carried.copy();
        ItemStack cleanSlotItem = ItemTooltip.cleanItemStack(slotItem.copy());
        
        List<ItemStack> merged = ItemMerge.combine(cleanCarried, cleanSlotItem);
        if (merged.isEmpty()) {
            return;
        }
        
        // 更新槽位和手持物品
        slot.set(ItemTooltip.enhanceTooltip(merged.get(0)));
        setCarried(merged.size() > 1 ? merged.get(1) : ItemStack.EMPTY);
        container.setChanged();
    }
    
}