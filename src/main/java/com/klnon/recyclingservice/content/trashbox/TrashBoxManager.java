package com.klnon.recyclingservice.content.trashbox;

import com.klnon.recyclingservice.content.trashbox.core.TrashBox;
import com.klnon.recyclingservice.content.trashbox.core.TrashManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 垃圾箱管理器 - trashbox包的统一入口
 * 对外提供垃圾箱创建、物品存储等核心功能
 */
public class TrashBoxManager {
    
    private static final TrashManager trashManager = new TrashManager();
    
    /**
     * 获取或创建指定维度的垃圾箱
     * 
     * @param dimensionId 维度ID
     * @param boxNumber 垃圾箱编号
     * @return 垃圾箱实例
     */
    public static TrashBox getOrCreateTrashBox(ResourceLocation dimensionId, int boxNumber) {
        return trashManager.getOrCreateTrashBox(dimensionId, boxNumber);
    }
    
    /**
     * 将物品添加到指定维度的垃圾箱系统
     * 
     * @param dimensionId 维度ID
     * @param items 要添加的物品列表
     */
    public static void addItemsToDimension(ResourceLocation dimensionId, List<ItemStack> items) {
        trashManager.addItemsToDimension(dimensionId, items);
    }
    
    /**
     * 获取指定维度的所有垃圾箱
     * 
     * @param dimensionId 维度ID
     * @return 垃圾箱列表
     */
    public static List<TrashBox> getDimensionTrashBoxes(ResourceLocation dimensionId) {
        return trashManager.getDimensionTrashBoxes(dimensionId);
    }
    
    /**
     * 清空所有垃圾箱
     */
    public static void clearAll() {
        trashManager.clearAll();
    }
}