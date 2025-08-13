package com.klnon.recyclingservice.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维度垃圾箱管理器 - 简化版，内存存储
 * 服务器重启时自动清空，符合垃圾桶的临时存储特性
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
        if (!isDimensionSupported(dimensionId) || !isValidBoxNumber(boxNumber)) {
            return null;
        }
        
        List<TrashBox> boxes = dimensionBoxes.computeIfAbsent(dimensionId, k -> new ArrayList<>());
        
        // 确保列表足够大
        while (boxes.size() < boxNumber) {
            int newBoxNumber = boxes.size() + 1;
            TrashBox newBox = new TrashBox(Config.TRASH_BOX_SIZE.get(), newBoxNumber);
            boxes.add(newBox);
        }
        
        return boxes.get(boxNumber - 1);
    }
    
    /**
     * 获取指定维度的垃圾箱，不创建新的
     */
    public TrashBox getTrashBox(ResourceLocation dimensionId, int boxNumber) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        if (boxes == null || !isValidBoxNumber(boxNumber) || boxNumber > boxes.size()) {
            return null;
        }
        return boxes.get(boxNumber - 1);
    }
    
    /**
     * 为指定维度添加物品到垃圾箱
     */
    public int addItemsToDimension(ResourceLocation dimensionId, List<ItemStack> items) {
        if (items.isEmpty()) {
            return 0;
        }
        
        int totalAdded = 0;
        int currentBoxNumber = 1;
        
        for (ItemStack item : items) {
            boolean added = false;
            
            // 从当前垃圾箱开始尝试添加
            while (currentBoxNumber <= Config.MAX_BOXES_PER_DIMENSION.get() && !added) {
                TrashBox box = getOrCreateTrashBox(dimensionId, currentBoxNumber);
                if (box != null && box.addItem(item)) {
                    totalAdded++;
                    added = true;
                } else {
                    currentBoxNumber++;
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
     * 获取所有已创建的维度ID
     */
    public Set<ResourceLocation> getAllDimensions() {
        return new HashSet<>(dimensionBoxes.keySet());
    }
    
    /**
     * 清空指定维度的所有垃圾箱
     */
    public void clearDimension(ResourceLocation dimensionId) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        if (boxes != null) {
            boxes.forEach(TrashBox::clear);
        }
    }
    
    /**
     * 删除指定维度的所有垃圾箱
     */
    public void removeDimension(ResourceLocation dimensionId) {
        dimensionBoxes.remove(dimensionId);
    }
    
    /**
     * 获取指定维度的垃圾箱数量
     */
    public int getDimensionBoxCount(ResourceLocation dimensionId) {
        List<TrashBox> boxes = dimensionBoxes.get(dimensionId);
        return boxes != null ? boxes.size() : 0;
    }
    
    /**
     * 检查维度是否受支持
     */
    private boolean isDimensionSupported(ResourceLocation dimensionId) {
        if (Config.AUTO_CREATE_DIMENSION_TRASH.get()) {
            return true;
        }
        return Config.isDimensionSupported(dimensionId.toString());
    }
    
    /**
     * 检查垃圾箱编号是否有效
     */
    private boolean isValidBoxNumber(int boxNumber) {
        return boxNumber >= 1 && boxNumber <= Config.MAX_BOXES_PER_DIMENSION.get();
    }
    
    /**
     * 获取统计信息
     */
    public String getStatistics() {
        int totalDimensions = dimensionBoxes.size();
        int totalBoxes = dimensionBoxes.values().stream().mapToInt(List::size).sum();
        int totalItems = dimensionBoxes.values().stream()
                .flatMap(List::stream)
                .mapToInt(TrashBox::getItemCount)
                .sum();
        
        return String.format("Dimensions: %d, Boxes: %d, Items: %d", 
                           totalDimensions, totalBoxes, totalItems);
    }
    
    /**
     * 清空所有垃圾箱（服务器重启或维护时使用）
     */
    public void clearAll() {
        dimensionBoxes.clear();
    }
}