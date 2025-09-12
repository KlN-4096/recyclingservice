package com.klnon.recyclingservice.content.trashbox.data;

import com.klnon.recyclingservice.foundation.utility.UiHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * 垃圾箱实体类 - 实现Container接口，直接作为容器使用
 * 专注于临时存储清理的掉落物
 */
public class TrashBox implements Container {

    private final TrashData data;

    public TrashBox(int capacity, int boxNumber, ResourceLocation dimensionId) {
        this.data = new TrashData(capacity, boxNumber, dimensionId);
    }

    /**
     * 获取数据层引用
     */
    public TrashData getData() {
        return data;
    }

    /**
     * 添加物品到垃圾箱
     */
    public boolean addItem(ItemStack item, int slot) {
        // 先尝试合并到相同物品槽位
        if (data.tryMergeToExisting(item) && slot == -1) {
            return true; // 完全合并成功
        }

        // 合并失败或部分合并，尝试放入空槽位
        return data.tryAddToEmptySlot(item, slot);
    }
    // === Container接口实现 ===

    /**
     * 获取指定位置的物品（不移除）
     */
    @Override
    public @Nonnull ItemStack getItem(int slot) {
        return (slot >= 0 && slot < data.getCapacity()) ? data.items.get(slot) : ItemStack.EMPTY;
    }

    /**
     * 移除指定数量的物品
     */
    @Override
    public @Nonnull ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= data.getCapacity() || data.items.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = data.items.get(slot);
        ItemStack result = (amount >= stackInSlot.getCount()) ?
                stackInSlot : stackInSlot.split(amount);

        if (amount >= stackInSlot.getCount()) {
            data.items.set(slot, ItemStack.EMPTY);
        }

        setChanged();
        return result;
    }

    /**
     * 移除整个物品堆（不触发setChanged）
     */
    @Override
    public @Nonnull ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= data.getCapacity() || data.items.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = data.items.get(slot);
        data.items.set(slot, ItemStack.EMPTY);
        return stackInSlot;
    }

    /**
     * 设置指定位置的物品
     */
    @Override
    public void setItem(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= data.getCapacity()) return;

        ItemStack oldItem = data.items.get(slot);
        ItemStack newItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();

        if (!newItem.isEmpty()) {
            UiHelper.updateTooltip(newItem);
        }

        data.items.set(slot, newItem);
        data.updateIndex(slot, oldItem, newItem);
        setChanged();
    }

    /**
     * 清空垃圾箱 - Container接口方法
     */
    @Override
    public void clearContent() {
        data.items.clear();
        data.initializeIndex();
        setChanged();
    }

    /**
     * 获取容器大小
     */
    @Override
    public int getContainerSize() {
        return data.getCapacity();
    }

    /**
     * 检查容器是否为空
     */
    @Override
    public boolean isEmpty() {
        return data.items.stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * 标记容器已变更
     */
    @Override
    public void setChanged() {
        // 垃圾箱是临时容器，不需要持久化
    }

    /**
     * 检查玩家是否可以访问容器 - Container接口方法
     */
    @Override
    public boolean stillValid(@Nonnull Player player) {
        return true; // 垃圾箱对所有玩家开放
    }
}