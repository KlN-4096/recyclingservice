package com.klnon.recyclingservice.core;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.ui.UiUtils;

import javax.annotation.Nonnull;

/**
 * 垃圾箱实体类 - 实现Container接口，直接作为容器使用
 * 专注于临时存储清理的掉落物
 */
public class TrashBox implements Container {
    public final NonNullList<ItemStack> items;
    private final int capacity;
    private final int boxNumber;
    private final ResourceLocation dimensionId;
    
    public TrashBox(int capacity, int boxNumber, ResourceLocation dimensionId) {
        this.capacity = capacity;
        this.boxNumber = boxNumber;
        this.dimensionId = dimensionId;
        this.items = NonNullList.withSize(capacity, ItemStack.EMPTY);
    }
    
    /**
     * 添加物品到垃圾箱
     */
    public void addItem(ItemStack item) {
        if (item.isEmpty()) {
            return;
        }
        
        // 利用NonNullList的indexOf找空槽位
        int emptySlot = items.indexOf(ItemStack.EMPTY);
        if (emptySlot != -1) {
            setItem(emptySlot, item);
        }
    }
    
    /**
     * 验证slot索引是否有效
     */
    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < capacity;
    }

    /**
     * 获取指定位置的物品（不移除）- Container接口方法
     */
    @Override
    public @Nonnull ItemStack getItem(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return items.get(slot);
    }

    /**
     * 移除指定数量的物品 - Container接口方法
     */
    @Override
    public @Nonnull ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = items.get(slot);
        if (stackInSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result;
        if (amount >= stackInSlot.getCount()) {
            // 移除整个物品堆
            result = stackInSlot;
            items.set(slot, ItemStack.EMPTY);
        } else {
            // 移除部分物品
            result = stackInSlot.split(amount);
        }

        setChanged();
        return result;
    }

    /**
     * 移除整个物品堆（不触发setChanged） - Container接口方法
     */
    @Override
    public @Nonnull ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = items.get(slot);
        if (stackInSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 移除整个物品堆，不触发变更事件
        items.set(slot, ItemStack.EMPTY);
        return stackInSlot;
    }

    /**
     * 设置指定位置的物品 - Container接口方法
     */
    @Override
    public void setItem(int slot,@Nonnull ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        
        if (stack.isEmpty()) {
            items.set(slot, ItemStack.EMPTY);
        } else {
            ItemStack itemCopy = stack.copy();
            UiUtils.updateTooltip(itemCopy);
            items.set(slot, itemCopy);
        }
        setChanged();
    }
    
    /**
     * 清空垃圾箱 - Container接口方法
     */
    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    /**
     * 获取容器大小 - Container接口方法
     */
    @Override
    public int getContainerSize() {
        return capacity;
    }

    /**
     * 检查容器是否为空 - Container接口方法
     */
    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * 标记容器已变更 - Container接口方法
     */
    @Override
    public void setChanged() {
        // 垃圾箱是临时容器，不需要持久化，这里留空即可
    }

    /**
     * 检查玩家是否可以访问容器 - Container接口方法
     */
    @Override
    public boolean stillValid(@Nonnull Player player) {
        // 垃圾箱对所有玩家开放
        return true;
    }

    /**
     * 获取垃圾箱所在维度ID
     */
    public ResourceLocation getDimensionId() {
        return dimensionId;
    }
    
    /**
     * 检查当前维度是否允许玩家主动放入物品到垃圾箱
     */
    public boolean isAllowedToPutIn() {
        return Config.isDimensionAllowPutIn(dimensionId.toString());
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