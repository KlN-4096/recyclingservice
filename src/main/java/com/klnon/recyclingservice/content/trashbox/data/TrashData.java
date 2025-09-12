package com.klnon.recyclingservice.content.trashbox.data;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.foundation.utility.UiHelper;
import com.klnon.recyclingservice.Config;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 垃圾箱数据类 - 负责数据存储和索引管理
 */
public class TrashData {

    public final NonNullList<ItemStack> items;
    private final int capacity;
    private final int boxNumber;
    private final ResourceLocation dimensionId;

    // 统一索引：物品类型->槽位列表，EMPTY表示空位置
    private final Map<String, Deque<Integer>> itemTypeSlots = new HashMap<>();
    private static final String EMPTY_KEY = "EMPTY";

    public TrashData(int capacity, int boxNumber, ResourceLocation dimensionId) {
        this.capacity = capacity;
        this.boxNumber = boxNumber;
        this.dimensionId = dimensionId;
        this.items = NonNullList.withSize(capacity, ItemStack.EMPTY);
        initializeIndex();
    }

    /**
     * 初始化索引 - 全部设置为空位置
     */
    public void initializeIndex() {
        itemTypeSlots.clear();
        Deque<Integer> emptySlots = new ArrayDeque<>();
        for (int i = capacity - 1; i >= 0; i--) {  // 从大到小,从前往后填充
            emptySlots.add(i);
        }
        itemTypeSlots.put(EMPTY_KEY, emptySlots);
    }

    /**
     * 统一更新索引：先移除旧的，再添加新的
     */
    public void updateIndex(int slot, ItemStack oldItem, ItemStack newItem) {
        // 移除旧索引
        String oldKey = oldItem.isEmpty() ? EMPTY_KEY : CleanupManager.generateItemHash(oldItem);
        Deque<Integer> oldSlots = itemTypeSlots.get(oldKey);
        if (oldSlots != null) {
            oldSlots.remove(slot);
            if (oldSlots.isEmpty()) {
                itemTypeSlots.remove(oldKey);
            }
        }

        // 添加新索引
        String newKey = newItem.isEmpty() ? EMPTY_KEY : CleanupManager.generateItemHash(newItem);
        itemTypeSlots.computeIfAbsent(newKey, k -> new ArrayDeque<>()).addLast(slot);
    }

    /**
     * 尝试添加到相同物品槽位
     */
    public boolean tryMergeToExisting(ItemStack item) {
        String itemKey = CleanupManager.generateItemHash(item);
        Deque<Integer> sameTypeSlots = itemTypeSlots.get(itemKey);
        if (sameTypeSlots == null) return false;

        for (Integer slot : sameTypeSlots) {
            ItemStack slotItem = items.get(slot);
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

    /**
     * 尝试添加到空槽位
     */
    public boolean tryAddToEmptySlot(ItemStack item, int slot) {
        if (slot != -1) {
            items.set(slot, item.copy());
            return true;
        }
        Deque<Integer> emptySlots = itemTypeSlots.get(EMPTY_KEY);
        if (emptySlots == null || emptySlots.isEmpty()) return false;

        Integer emptySlot = emptySlots.removeLast();
        items.set(emptySlot, item.copy());
        updateIndex(emptySlot, ItemStack.EMPTY, item);
        item.shrink(item.getCount());
        return true;
    }

    /**
     * 获取相同物品的槽位列表
     */
    public List<Integer> getSameItemSlots(ItemStack item) {
        if (item.isEmpty()) return Collections.emptyList();
        String key = CleanupManager.generateItemHash(item);
        Deque<Integer> deque = itemTypeSlots.getOrDefault(key, new ArrayDeque<>());
        return new ArrayList<>(deque);
    }

    // === Getters ===

    public int getCapacity() {
        return capacity;
    }

    public int getBoxNumber() {
        return boxNumber;
    }

    public ResourceLocation getDimensionId() {
        return dimensionId;
    }
}