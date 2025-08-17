package com.klnon.recyclingservice.ui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.ItemMerge;

import java.util.List;
import java.util.ArrayList;

import javax.annotation.Nonnull;

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
        
        // 限制每次最多取出64个
        int actualAmount = Math.min(amount, 64);
        
        ItemStack removed;
        if (itemStack.getCount() <= actualAmount) {
            removed = itemStack;
            items.set(slot, ItemStack.EMPTY);
        } else {
            removed = itemStack.split(actualAmount);
        }
        
        if (!removed.isEmpty()) {
            syncToTrashBox();
            setChanged();
        }
        
        return cleanItemStack(removed); // 返回原始物品，移除Lore
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
        
        return cleanItemStack(removed); // 返回原始物品，移除Lore
    }
    
    @Override
    public void setItem(int slot,@Nonnull ItemStack stack) {
        ensureDataSynced();
        if (slot < 0 || slot >= items.size()) {
            return;
        }
        
        if (stack.isEmpty()) {
            // 设置为空物品，使用原逻辑
            items.set(slot, ItemStack.EMPTY);
            syncToTrashBox();
            setChanged();
        } else {
            // 尝试智能合并添加物品
            items.set(slot, ItemStack.EMPTY);
            if (!addItemWithMerge(stack)) {
                // 智能合并失败（容量不足），回退到原逻辑
                // 直接放入指定槽位，覆盖原有物品
                items.set(slot, enhanceTooltip(cleanItemStack(stack)));
                syncToTrashBox();
                setChanged();
            }
            // 如果智能合并成功，addItemWithMerge已经处理了同步和变更标记
        }
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
    protected void syncFromTrashBox() {
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
                items.set(i, enhanceTooltip(item)); // 使用增强版本
            }
        }
        
        needsSync = false;
    }
    
    /**
     * 将Container数据同步回TrashBox
     * 在容器内容变更时调用
     */
    void syncToTrashBox() {
        trashBox.clear(); // 清空原有内容
        
        // 将非空物品添加回TrashBox (清理后的原始版本)
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                trashBox.addItem(cleanItemStack(item)); // 存储原始版本
            }
        }
        
        // 标记外部TrashBox可能已变更，下次读取时需重新同步
        needsSync = true;
    }
    
    /**
     * 智能合并添加物品到容器
     * 将新物品与现有物品智能合并，充分利用空间
     * 
     * @param itemToAdd 要添加的物品
     * @return 是否成功添加
     */
    public boolean addItemWithMerge(ItemStack itemToAdd) {
        if (itemToAdd.isEmpty()) {
            return false;
        }
        
        ensureDataSynced();
        
        // 1. 收集当前所有非空物品 (使用原始版本，不包含增强Tooltip)
        List<ItemStack> currentItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                currentItems.add(cleanItemStack(item.copy()));
            }
        }
        
        // 2. 添加新物品 (确保使用原始版本)
        currentItems.add(cleanItemStack(itemToAdd.copy()));
        
        // 3. 调用ItemMerge进行智能合并
        List<ItemStack> mergedItems = ItemMerge.combine(currentItems);
        
        // 4. 检查是否超出容器容量
        if (mergedItems.size() > CONTAINER_SIZE) {
            return false; // 容量不足，拒绝添加
        }
        
        // 5. 清空现有槽位并重新分配合并后的物品
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        
        // 6. 将合并后的物品放入槽位 (使用增强版本显示)
        for (int i = 0; i < mergedItems.size() && i < CONTAINER_SIZE; i++) {
            ItemStack mergedItem = mergedItems.get(i);
            if (!mergedItem.isEmpty()) {
                items.set(i, enhanceTooltip(mergedItem));
            }
        }
        
        // 7. 同步到TrashBox并标记变更
        syncToTrashBox();
        setChanged();
        
        return true;
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
    
    /**
     * 增强物品Tooltip显示真实数量
     * 使用1.21.1的DataComponent系统添加Lore信息
     * 
     * @param original 原始物品堆
     * @return 增强后的物品堆
     */
    public ItemStack enhanceTooltip(ItemStack original) {
        if (original.getCount() <= 64) {
            return original.copy();
        }
        
        ItemStack enhanced = original.copy();
        
        // 使用DataComponent系统添加Lore
        List<Component> loreLines = new ArrayList<>();
        
        // 保留原有的lore
        ItemLore existingLore = enhanced.get(DataComponents.LORE);
        if (existingLore != null) {
            loreLines.addAll(existingLore.lines());
        }
        
        // 添加真实数量信息
        loreLines.add(Component.empty()); // 空行分隔
        loreLines.add(Component.literal("§7可取出: §a" + original.getCount())
            .withStyle(style -> style.withItalic(false)));
        
        // 应用新的lore
        enhanced.set(DataComponents.LORE, new ItemLore(loreLines));
        
        return enhanced;
    }
    
    /**
     * 清理ItemStack的Lore，返回原始物品
     * KISS原则：最简单的解决方案
     * 
     * @param item 可能包含自定义Lore的物品
     * @return 清理后的原始物品
     */
    public ItemStack cleanItemStack(ItemStack item) {
        if (item.isEmpty()) {
            return item;
        }
        item.remove(DataComponents.LORE);
        
        return item;
    }
}