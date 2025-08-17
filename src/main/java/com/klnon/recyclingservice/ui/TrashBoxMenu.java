package com.klnon.recyclingservice.ui;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * 垃圾箱自定义菜单 - 限制快速移动数量
 * 
 * 继承ChestMenu，重写quickMoveStack方法来限制Shift+点击的取出数量
 * 保持与原版UI的完全兼容，只修改行为逻辑
 */
public class TrashBoxMenu extends ChestMenu {
    
    public TrashBoxMenu(int containerId, Inventory playerInventory, UniversalTrashContainer container) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
    }
    
    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        var slot = this.slots.get(index);
        
        if (slot != null && slot.hasItem()) {
            ItemStack slotItem = slot.getItem();
            UniversalTrashContainer container = (UniversalTrashContainer) this.getContainer();
            
            // 判断是否是从垃圾箱槽位移动到玩家背包
            if (index < this.getRowCount() * 9) {
                // 从垃圾箱移动到玩家背包 - 限制最多64个
                ItemStack limitedItem = slotItem.copy();
                limitedItem.setCount(Math.min(slotItem.getCount(), 64));
                
                // 尝试移动限制后的物品到玩家背包,并清除lora
                if (!this.moveItemStackTo(container.cleanItemStack(limitedItem), this.getRowCount() * 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;  // 移动失败
                }
                
                // 从原槽位减少对应数量
                if (slotItem.getCount() <= 64) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slotItem.shrink(64);
                    
                    // 使用已有的container重新生成带有更新Lore的ItemStack
                    ItemStack updatedItem = container.enhanceTooltip(slotItem);
                    slot.set(updatedItem);
                    
                    slot.setChanged();
                }
                return limitedItem;  // 返回实际移动的物品（最多64个）
                
            } else {
                // 从玩家背包移动到垃圾箱
                itemstack = slotItem.copy();  // 这里才复制原物品
                if (!this.moveItemStackTo(slotItem, 0, this.getRowCount() * 9, false)) {
                    return ItemStack.EMPTY;
                }
                
                if (slotItem.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                
                return itemstack;  // 返回完整移动的物品
            }
        }
        
        return ItemStack.EMPTY;
    }
}