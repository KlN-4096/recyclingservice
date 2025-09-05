package com.klnon.recyclingservice.content.trashbox;

import com.klnon.recyclingservice.content.cleanup.entity.EntityMerger;
import com.klnon.recyclingservice.content.trashbox.core.TrashBox;
import com.klnon.recyclingservice.content.trashbox.core.TrashStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 垃圾箱管理器 - trashbox包的统一入口
 * 对外提供垃圾箱创建、物品存储等核心功能
 */
public class TrashBoxManager {
    
    private static final TrashStorage trashStorage = new TrashStorage();
    
    /**
     * 获取或创建指定维度的垃圾箱
     * 
     * @param dimensionId 维度ID
     * @param boxNumber 垃圾箱编号
     * @return 垃圾箱实例
     */
    public static TrashBox getOrCreateTrashBox(ResourceLocation dimensionId, int boxNumber) {
        return trashStorage.getOrCreateTrashBox(dimensionId, boxNumber);
    }
    
    /**
     * 将物品添加到指定维度的垃圾箱系统
     * 
     * @param dimensionId 维度ID
     * @param items 要添加的物品列表
     */
    public static void addItemToDimension(ResourceLocation dimensionId, ItemStack item) {
        if (item.isEmpty()) return;
        TrashBox trashBox = getOrCreateTrashBox(dimensionId, 1);
        if (trashBox != null) {
            trashBox.addItem(item);
        }
    }
    
    /**
     * 获取指定维度的所有垃圾箱
     * 
     * @param dimensionId 维度ID
     * @return 垃圾箱列表
     */
    public static List<TrashBox> getDimensionTrashBoxes(ResourceLocation dimensionId) {
        return trashStorage.getDimensionTrashBoxes(dimensionId);
    }
    
    /**
     * 清空所有垃圾箱
     */
    public static void clearAll() {
        trashStorage.clearAll();
    }
    
    // === UI辅助功能 ===
    
    /**
     * 检查两个物品是否为同一种物品（用于UI操作中的物品比较）
     * @param stack1 第一个物品
     * @param stack2 第二个物品
     * @return 是否为同一种物品
     */
    public static boolean isSameItem(ItemStack stack1, ItemStack stack2) {
        return EntityMerger.isSameItem(stack1, stack2);
    }
}