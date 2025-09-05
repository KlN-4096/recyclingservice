package com.klnon.recyclingservice.content.trashbox.core;

import com.klnon.recyclingservice.foundation.utility.UiHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import com.klnon.recyclingservice.Config;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.klnon.recyclingservice.content.cleanup.entity.EntityMerger;

/**
 * 垃圾箱实体类 - 实现Container接口，直接作为容器使用
 * 专注于临时存储清理的掉落物
 */
public class TrashBox implements Container {
    public final NonNullList<ItemStack> items;
    private final int capacity;
    private final int boxNumber;
    private final ResourceLocation dimensionId;
    
    // 统一索引：物品类型->槽位列表，EMPTY表示空位置
    private final Map<String, List<Integer>> itemTypeSlots = new HashMap<>();
    private static final String EMPTY_KEY = "EMPTY";
    
    public TrashBox(int capacity, int boxNumber, ResourceLocation dimensionId) {
        this.capacity = capacity;
        this.boxNumber = boxNumber;
        this.dimensionId = dimensionId;
        this.items = NonNullList.withSize(capacity, ItemStack.EMPTY);
        initializeIndex();
    }
    
    /**
     * 初始化索引 - 全部设置为空位置
     */
    private void initializeIndex() {
        itemTypeSlots.clear();
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = capacity - 1; i >= 0; i--) {  // 从大到小,从前往后填充
            emptySlots.add(i);
        }
        itemTypeSlots.put(EMPTY_KEY, emptySlots);
    }
    
    /**
     * 添加物品到垃圾箱
     */
    public boolean addItem(ItemStack item) {
        // 1. 尝试合并到相同物品槽位
        if (tryMergeToExisting(item)) {
            return true;
        }
        
        // 2. 放入空槽位
        return tryAddToEmptySlot(item,-1);
    }

    public boolean tryMergeToExisting(ItemStack item) {
        String itemKey = EntityMerger.generateComplexItemKey(item);
        List<Integer> sameTypeSlots = itemTypeSlots.get(itemKey);
        
        if (sameTypeSlots == null) return false;
        
        for (Integer slot : sameTypeSlots) {
            ItemStack slotItem = getItem(slot);
            
            int configLimit = Config.getItemStackMultiplier(slotItem);
            int canAdd = configLimit - slotItem.getCount();
            if (canAdd <= 0) continue;
            
            int addAmount = Math.min(canAdd, item.getCount());
            slotItem.grow(addAmount);
            UiHelper.updateTooltip(slotItem);
            item.shrink(addAmount);
        }
        return item.isEmpty();
    }

    public boolean tryAddToEmptySlot(ItemStack item,int slot) {
        List<Integer> emptySlots = itemTypeSlots.get(EMPTY_KEY);
        if (emptySlots != null && !emptySlots.isEmpty()) {
            Integer emptySlot = slot ==-1 ? emptySlots.remove(emptySlots.size() - 1) : slot;
            setItem(emptySlot, item.copy());
            return true;
        }
        return false;
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
        return isValidSlot(slot) ? items.get(slot) : ItemStack.EMPTY;
    }

    /**
     * 移除指定数量的物品 - Container接口方法
     */
    @Override
    public @Nonnull ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot) || items.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = items.get(slot);
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
        if (!isValidSlot(slot) || items.get(slot).isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 移除整个物品堆，不触发变更事件
        ItemStack stackInSlot = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stackInSlot;
    }

    /**
     * 设置指定位置的物品 - Container接口方法
     */
    @Override
    public void setItem(int slot, @Nonnull ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        
        ItemStack oldItem = items.get(slot);
        
        // 准备新物品
        ItemStack newItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        UiHelper.updateTooltip(newItem);
        
        // 更新存储
        items.set(slot, newItem);
        
        // 统一更新索引：先移除旧的，再添加新的
        if (!oldItem.isEmpty()) {
            removeFromIndex(slot, oldItem);
        } else {
            removeFromIndex(slot, ItemStack.EMPTY);
        }
        // 添加新索引
        addToIndex(slot, newItem);
        
        setChanged();
    }


    private void removeFromIndex(int slot, ItemStack item) {
        String key = item.isEmpty() ? EMPTY_KEY : EntityMerger.generateComplexItemKey(item);
        List<Integer> slots = itemTypeSlots.get(key);
        if (slots != null) {
            slots.remove(Integer.valueOf(slot));
            if (slots.isEmpty()) {
                itemTypeSlots.remove(key);
            }
        }
    }

    private void addToIndex(int slot, ItemStack item) {
        String key = item.isEmpty() ? EMPTY_KEY : EntityMerger.generateComplexItemKey(item);
        itemTypeSlots.computeIfAbsent(key, k -> new ArrayList<>()).add(slot);
    }
    
    /**
     * 获取相同物品的槽位列表（用于UI优化）
     */
    public List<Integer> getSameItemSlots(ItemStack item) {
        if (item.isEmpty()) return Collections.emptyList();
        String key = EntityMerger.generateComplexItemKey(item);
        return itemTypeSlots.getOrDefault(key, Collections.emptyList());
    }
    
    /**
     * 清空垃圾箱 - Container接口方法
     */
    @Override
    public void clearContent() {
        items.clear();
        initializeIndex();
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