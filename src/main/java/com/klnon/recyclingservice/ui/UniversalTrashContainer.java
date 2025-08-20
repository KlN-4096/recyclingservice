package com.klnon.recyclingservice.ui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.Item.ItemTooltip;

import javax.annotation.Nonnull;

/**
 * 通用垃圾箱容器适配器 - 简化版
 * 
 * 直接使用TrashBox作为数据源，移除复杂的同步机制
 * 
 * 设计原则：
 * - 直接操作TrashBox，无需双向同步
 * - 保持54格固定大小(6行×9列)  
 * - KISS原则：简单直接
 */
public class UniversalTrashContainer implements Container {
    
    public static final int CONTAINER_SIZE = 54; // 6行×9列
    
    private final TrashBox trashBox;
    
    public UniversalTrashContainer(TrashBox trashBox) {
        this.trashBox = trashBox;
        // 确保TrashBox容量与Container大小匹配
        if (trashBox.size() != CONTAINER_SIZE) {
            throw new IllegalArgumentException("TrashBox size must be " + CONTAINER_SIZE);
        }
    }
    
    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }
    
    @Override
    public boolean isEmpty() {
        return trashBox.isEmpty();
    }
    
    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= CONTAINER_SIZE) {
            return ItemStack.EMPTY;
        }
        
        // 直接从TrashBox获取，并增强显示
        ItemStack item = trashBox.items.get(slot);
        return item.isEmpty() ? ItemStack.EMPTY : ItemTooltip.enhanceTooltip(item);
    }
    
    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= CONTAINER_SIZE) {
            return ItemStack.EMPTY;
        }
        
        ItemStack itemStack = trashBox.items.get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        // 限制每次最多取出64个
        int actualAmount = Math.min(amount, 64);
        
        ItemStack removed;
        if (itemStack.getCount() <= actualAmount) {
            removed = itemStack.copy();
            trashBox.items.set(slot, ItemStack.EMPTY);
        } else {
            removed = itemStack.copy();
            removed.setCount(actualAmount);
            itemStack.shrink(actualAmount);
        }
        
        if (!removed.isEmpty()) {
            setChanged();
        }
        
        return ItemTooltip.cleanItemStack(removed); // 返回原始物品，移除Lore
    }
    
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= CONTAINER_SIZE) {
            return ItemStack.EMPTY;
        }
        
        ItemStack removed = trashBox.items.get(slot).copy();
        trashBox.items.set(slot, ItemStack.EMPTY);
        
        return ItemTooltip.cleanItemStack(removed); // 返回原始物品，移除Lore
    }
    
    @Override
    public void setItem(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= CONTAINER_SIZE) {
            return;
        }
        
        if (stack.isEmpty()) {
            trashBox.items.set(slot, ItemStack.EMPTY);
        } else {
            // 直接设置，不做任何合并逻辑
            ItemStack cleanStack = ItemTooltip.cleanItemStack(stack.copy());
            trashBox.items.set(slot, ItemTooltip.enhanceTooltip(cleanStack));
        }
        setChanged();
    }
    
    @Override
    public void setChanged() {
        // 在1.21.1中，setChanged()方法通常为空实现
        // 容器变更通知由Menu层处理
    }
    
    @Override
    public boolean stillValid(@Nonnull Player player) {
        // 垃圾箱始终可访问（这是设计特性）
        return true;
    }
    
    @Override
    public void clearContent() {
        trashBox.clear();
        setChanged();
    }
    
    /**
     * 获取关联的TrashBox实例
     * 用于调试和扩展功能
     */
    public TrashBox getTrashBox() {
        return trashBox;
    }
}