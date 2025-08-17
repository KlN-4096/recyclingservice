package com.klnon.recyclingservice.core;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * 垃圾箱实体类 - 简化版，无持久化
 * 专注于临时存储清理的掉落物
 */
public class TrashBox {
    public final NonNullList<ItemStack> items;
    private final int capacity;
    private final int boxNumber;
    
    public TrashBox(int capacity, int boxNumber) {
        this.capacity = capacity;
        this.boxNumber = boxNumber;
        this.items = NonNullList.withSize(capacity, ItemStack.EMPTY);
    }
    
    /**
     * 添加物品到垃圾箱
     */
    public boolean addItem(ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        
        // 利用NonNullList的indexOf找空槽位
        int emptySlot = items.indexOf(ItemStack.EMPTY);
        if (emptySlot != -1) {
            items.set(emptySlot, item.copy());
            return true;
        }
        return false; // 没有空槽位
    }
    
    /**
     * 取出指定位置的物品
     */
    public ItemStack getItem(int index) {
        if (index < 0 || index >= capacity) {
            return ItemStack.EMPTY;
        }
        
        ItemStack item = items.get(index);
        items.set(index, ItemStack.EMPTY);
        return item;
    }

    /**
     * 设置指定位置的物品
     */
    public void setItem(int index, ItemStack item) {
        if (index >= 0 && index < capacity) {
            items.set(index, item.isEmpty() ? ItemStack.EMPTY : item.copy());
        }
    }
    
    /**
     * 清空垃圾箱
     */
    public void clear() {
        items.clear();
    }

    /**
     * 获取垃圾箱编号
     */
    public int getBoxNumber() {
        return boxNumber;
    }
    
    /**
     * 检查垃圾箱是否已满
     */
    public boolean isFull() {
        return !items.contains(ItemStack.EMPTY);
    }
    
    /**
     * 检查垃圾箱是否为空
     */
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    
    /**
     * 获取非空物品列表
     */
    public List<ItemStack> getNonEmptyItems() {
        return items.stream()
                   .filter(item -> !item.isEmpty())
                   .map(ItemStack::copy)
                   .toList();
    }
    
    /**
     * 获取容器大小（固定槽位数）
     */
    public int size() {
        return items.size(); // 直接使用NonNullList的size()
    }
    
    /**
     * 获取当前物品数量（非空槽位）
     */
    public int getItemCount() {
        return (int) items.stream().filter(item -> !item.isEmpty()).count();
    }

    @Override
    public String toString() {
        return String.format("TrashBox{boxNumber=%d, items=%d/%d}", 
                           boxNumber, getItemCount(), capacity);
    }
}