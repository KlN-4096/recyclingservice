package com.klnon.recyclingservice.content.trashbox.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 垃圾箱存储系统 - 管理所有维度的垃圾箱存储
 * 职责：
 * - 管理各维度的垃圾箱集合
 * - 创建和获取指定垃圾箱
 * - 处理物品分配到垃圾箱
 */
public class TrashStorage {
    
    // 维度ID -> 垃圾箱列表
    private final Map<ResourceLocation, List<TrashBox>> dimensionBoxes;
    
    public TrashStorage() {
        this.dimensionBoxes = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取或创建指定维度的垃圾箱
     */
    public TrashBox getOrCreateTrashBox(ResourceLocation dimensionId, int boxNumber) {
        if (boxNumber < 1 || boxNumber > Config.GAMEPLAY.maxBoxesPerDimension.get()) {
            return null;
        }
        
        List<TrashBox> boxes = dimensionBoxes.computeIfAbsent(dimensionId, k -> new ArrayList<>());
        
        // 当目标打开的垃圾箱编号大于当前垃圾箱总数才新建
        while (boxes.size() < boxNumber) {
            int newBoxNumber = boxes.size() + 1;
            TrashBox newBox = new TrashBox(Config.GAMEPLAY.trashBoxRows.get()*9, newBoxNumber, dimensionId);
            boxes.add(newBox);
        }
        
        return boxes.get(boxNumber - 1);
    }
    
    /**
     * 为指定维度添加物品到垃圾箱
     */
    public void addItemToDimension(ResourceLocation dimensionId, ItemStack item) {
        if (item.isEmpty()) return;
        TrashBox trashBox = getOrCreateTrashBox(dimensionId, 1);
        if (trashBox != null) {
            trashBox.addItem(item);
        }
    }
    
    /**
     * 获取指定维度的所有垃圾箱
     */
    public List<TrashBox> getDimensionTrashBoxes(ResourceLocation dimensionId) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        return boxes != null ? boxes : new ArrayList<>();
    }

    /**
     * 删除所有垃圾箱
     */
    public void clearAll() {
        dimensionBoxes.clear();
    }
}