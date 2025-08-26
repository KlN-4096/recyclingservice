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
        if (!TrashBoxFactory.isValidBoxNumber(boxNumber)) {
            return null;
        }
        
        List<TrashBox> boxes = dimensionBoxes.computeIfAbsent(dimensionId, k -> new ArrayList<>());
        
        // 当目标打开的垃圾箱编号大于当前垃圾箱总数才新建
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
    public void addItemsToDimension(ResourceLocation dimensionId, List<ItemStack> items) {
        if (items.isEmpty())
            return;
        
        final int maxBoxes = Config.getMaxBoxes();
        final int capacity = Config.getTrashBoxRows()*9;
        
        // 保持原有的排序逻辑
        items.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        
        // 计算需要的垃圾箱数量，不超过最大限制
        int requiredBoxes = (int) Math.ceil((double) items.size() / capacity);
        int actualBoxes = Math.min(requiredBoxes, maxBoxes);
        
        // 分段批量分配到各个垃圾箱
        for (int boxIndex = 0; boxIndex < actualBoxes; boxIndex++) {
            TrashBox box = getOrCreateTrashBox(dimensionId, boxIndex + 1);
            if (box == null) break;
            
            // 计算当前垃圾箱应处理的物品范围
            int startIndex = boxIndex * capacity;
            int endIndex = Math.min(startIndex + capacity, items.size());
            
            // 直接批量放入物品到垃圾箱
            for (int i = startIndex; i < endIndex; i++) {
                box.items.set(i - startIndex, items.get(i).copy());
            }
            box.setChanged();
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
     * 清空指定维度的所有垃圾箱内容
     */
    public void clearDimension(ResourceLocation dimensionId) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        if (boxes != null) {
            boxes.forEach(TrashBox::clearContent);
        }
    }
    
    /**
     * 删除所有垃圾箱
     */
    public void clearAll() {
        dimensionBoxes.clear();
    }
}