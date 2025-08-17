package com.klnon.recyclingservice.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维度垃圾箱管理器
 */
public class DimensionTrashManager {
    
    // 维度ID -> 垃圾箱列表
    private final Map<ResourceLocation, List<TrashBox>> dimensionBoxes;
    
    public DimensionTrashManager() {
        this.dimensionBoxes = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取或创建指定维度的垃圾箱
     */
    public TrashBox getOrCreateTrashBox(ResourceLocation dimensionId, int boxNumber) {
        if (!Config.isDimensionSupported(dimensionId.toString()) || !TrashBoxFactory.isValidBoxNumber(boxNumber)) {
            return null;
        }
        
        List<TrashBox> boxes = dimensionBoxes.computeIfAbsent(dimensionId, k -> new ArrayList<>());
        
        // 确保列表足够大，使用工厂创建
        while (boxes.size() < boxNumber) {
            int newBoxNumber = boxes.size() + 1;
            TrashBox newBox = TrashBoxFactory.createForDimension(dimensionId, newBoxNumber);
            boxes.add(newBox);
        }
        
        return boxes.get(boxNumber - 1);
    }
    
    /**
     * 为指定维度添加物品到垃圾箱
     */
    public int addItemsToDimension(ResourceLocation dimensionId, List<ItemStack> items) {
        if (items.isEmpty())
            return 0;
        
        int totalAdded = 0;
        int currentBoxNumber = 1;
        final int maxBoxes = Config.MAX_BOXES_PER_DIMENSION.get(); // 只调用一次配置
        items.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        for (ItemStack item : items) {
            boolean added = false;
            int attempts = 0;
            
            // 限制搜索次数，避免无限循环
            while (attempts < maxBoxes && !added) {
                TrashBox box = getOrCreateTrashBox(dimensionId, currentBoxNumber);
                if (box != null && box.addItem(item)) {
                    totalAdded++;
                    added = true;
                } else {
                    currentBoxNumber = (currentBoxNumber % maxBoxes) + 1; // 循环搜索
                    attempts++;
                }
            }
            
            if (!added) {
                break; // 所有垃圾箱都满了
            }
        }
        
        return totalAdded;
    }
    
    /**
     * 获取指定维度的所有垃圾箱
     */
    public List<TrashBox> getDimensionTrashBoxes(ResourceLocation dimensionId) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        return boxes != null ? new ArrayList<>(boxes) : new ArrayList<>();
    }
    
    /**
     * 清空指定维度的所有垃圾箱内容
     */
    public void clearDimension(ResourceLocation dimensionId) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        if (boxes != null) {
            boxes.forEach(TrashBox::clear);
        }
    }
    
    /**
     * 删除所有垃圾箱
     */
    public void clearAll() {
        dimensionBoxes.clear();
    }
}