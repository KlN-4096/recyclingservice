package com.klnon.recyclingservice.core;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * 垃圾箱实体类 - 简化版，无持久化
 * 专注于临时存储清理的掉落物
 */
public class TrashBox {
    private final List<ItemStack> items;
    private final int capacity;
    private final int boxNumber;
    
    public TrashBox(int capacity, int boxNumber) {
        this.capacity = capacity;
        this.boxNumber = boxNumber;
        this.items = new ArrayList<>();
    }
    
    /**
     * 添加物品到垃圾箱
     */
    public boolean addItem(ItemStack item) {
        if (items.size() >= capacity || item.isEmpty()) {
            return false;
        }
        items.add(item.copy());
        return true;
    }
    
    /**
     * 批量添加物品
     */
    public int addItems(List<ItemStack> itemsToAdd) {
        int addedCount = 0;
        final int remainingSpace = capacity - items.size(); // 预计算剩余空间
        
        for (ItemStack item : itemsToAdd) {
            if (addedCount >= remainingSpace || item.isEmpty()) {
                break; // 提前终止，优化性能
            }
            items.add(item.copy());
            addedCount++;
        }
        return addedCount;
    }
    
    /**
     * 取出指定位置的物品
     */
    public ItemStack takeItem(int index) {
        if (index < 0 || index >= items.size()) {
            return ItemStack.EMPTY;
        }
        return items.remove(index);
    }
    
    /**
     * 查看指定位置的物品（不取出）
     */
    public ItemStack getItem(int index) {
        if (index < 0 || index >= items.size()) {
            return ItemStack.EMPTY;
        }
        return items.get(index);
    }
    
    /**
     * 清空垃圾箱
     */
    public void clear() {
        items.clear();
    }
    
    /**
     * 获取当前物品数量
     */
    public int getItemCount() {
        return items.size();
    }
    
    /**
     * 获取垃圾箱容量
     */
    public int getCapacity() {
        return capacity;
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
        return items.size() >= capacity;
    }
    
    /**
     * 检查垃圾箱是否为空
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    /**
     * 获取所有物品的只读视图
     */
    public List<ItemStack> getItems() {
        return new ArrayList<>(items);
    }
    
    @Override
    public String toString() {
        return String.format("TrashBox{boxNumber=%d, items=%d/%d}", 
                           boxNumber, items.size(), capacity);
    }
}