package com.klnon.recyclingservice.ui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import com.klnon.recyclingservice.core.TrashBox;

import java.util.List;

/**
 * 通用垃圾箱容器适配器
 * 
 * 将TrashBox适配为标准Container接口，支持：
 * - 原版ChestMenu.sixRows()直接使用
 * - 定制TrashBoxMenu也可使用
 * - 完全兼容Container接口规范
 * 
 * 设计原则：
 * - 54格固定大小(6行×9列)
 * - 双向数据同步：TrashBox ↔ Container
 * - 性能优化：lazy同步，只在需要时更新
 */
public class UniversalTrashContainer implements Container {
    
    public static final int CONTAINER_SIZE = 54; // 6行×9列
    
    private final TrashBox trashBox;
    private final NonNullList<ItemStack> items;
    private boolean needsSync = true; // 标记是否需要从TrashBox同步数据
    
    public UniversalTrashContainer(TrashBox trashBox) {
        this.trashBox = trashBox;
        this.items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
        syncFromTrashBox(); // 初始化时同步数据
    }
    
    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }
    
    @Override
    public boolean isEmpty() {
        ensureDataSynced();
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public ItemStack getItem(int slot) {
        ensureDataSynced();
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }
    
    @Override
    public ItemStack removeItem(int slot, int amount) {
        ensureDataSynced();
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        
        ItemStack itemStack = items.get(slot);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        
        ItemStack removed;
        if (itemStack.getCount() <= amount) {
            removed = itemStack;
            items.set(slot, ItemStack.EMPTY);
        } else {
            removed = itemStack.split(amount);
        }
        
        if (!removed.isEmpty()) {
            syncToTrashBox();
            setChanged();
        }
        
        return removed;
    }
    
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ensureDataSynced();
        if (slot < 0 || slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        
        ItemStack removed = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        
        if (!removed.isEmpty()) {
            syncToTrashBox();
        }
        
        return removed;
    }
    
    @Override
    public void setItem(int slot, ItemStack stack) {
        ensureDataSynced();
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        
        items.set(slot, stack);
        syncToTrashBox();
        setChanged();
    }
    
    @Override
    public void setChanged() {
        // 在1.21.1中，setChanged()方法通常为空实现
        // 容器变更通知由Menu层处理
    }
    
    @Override
    public boolean stillValid(Player player) {
        // 垃圾箱始终可访问（这是设计特性）
        return true;
    }
    
    @Override
    public void clearContent() {
        ensureDataSynced();
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        syncToTrashBox();
        setChanged();
    }
    
    
    /**
     * 从TrashBox同步数据到Container格式
     * 性能优化：只在数据可能变更时调用
     */
    private void syncFromTrashBox() {
        // 清空现有数据
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        
        // 从TrashBox获取物品列表
        List<ItemStack> trashItems = trashBox.getItems();
        
        // 复制物品到容器槽位（最多54个）
        int copyCount = Math.min(trashItems.size(), CONTAINER_SIZE);
        for (int i = 0; i < copyCount; i++) {
            ItemStack item = trashItems.get(i);
            if (!item.isEmpty()) {
                items.set(i, item.copy()); // 创建副本避免引用问题
            }
        }
        
        needsSync = false;
    }
    
    /**
     * 将Container数据同步回TrashBox
     * 在容器内容变更时调用
     */
    private void syncToTrashBox() {
        trashBox.clear(); // 清空原有内容
        
        // 将非空物品添加回TrashBox
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                trashBox.addItem(item.copy()); // 创建副本
            }
        }
        
        // 标记外部TrashBox可能已变更，下次读取时需重新同步
        needsSync = true;
    }
    
    /**
     * 确保数据已同步
     * lazy加载机制：只在实际访问时同步
     */
    private void ensureDataSynced() {
        if (needsSync) {
            syncFromTrashBox();
        }
    }
    
    /**
     * 获取关联的TrashBox实例
     * 用于调试和扩展功能
     */
    public TrashBox getTrashBox() {
        return trashBox;
    }
    
    /**
     * 强制重新同步数据
     * 在TrashBox外部变更后调用
     */
    public void markNeedsSync() {
        needsSync = true;
    }
}