package com.klnon.recyclingservice.core;

import net.minecraft.resources.ResourceLocation;
import com.klnon.recyclingservice.Config;

/**
 * 垃圾箱工厂类 - 负责创建垃圾箱实例
 * 遵循KISS原则：简单的工厂模式，统一创建逻辑
 */
public class TrashBoxFactory {
    
    /**
     * 创建垃圾箱
     * @param boxNumber 垃圾箱编号
     * @param capacity 自定义容量（如果为-1则使用配置文件的值）
     * @return 垃圾箱实例
     */
    public static TrashBox createTrashBox(int boxNumber, int capacity) {
        // 如果capacity为-1，使用配置文件的值
        int actualCapacity = capacity == -1 ? Config.TRASH_BOX_SIZE.get() : capacity;
        
        // 确保容量在合理范围内
        int safeCapacity = Math.max(9, Math.min(actualCapacity, 108));
        return new TrashBox(safeCapacity, boxNumber);
    }
    
    /**
     * 使用配置文件默认容量创建垃圾箱
     * @param boxNumber 垃圾箱编号
     * @return 垃圾箱实例
     */
    public static TrashBox createTrashBox(int boxNumber) {
        return createTrashBox(boxNumber, -1);
    }
    
    /**
     * 为指定维度创建垃圾箱
     * @param dimensionId 维度ID
     * @param boxNumber 垃圾箱编号
     * @return 垃圾箱实例，如果维度不支持返回null
     */
    public static TrashBox createForDimension(ResourceLocation dimensionId, int boxNumber) {
        // 检查维度是否在支持列表中（如果启用了自动创建，则支持所有维度）
        if (!Config.AUTO_CREATE_DIMENSION_TRASH.get() && 
            !Config.isDimensionSupported(dimensionId.toString())) {
            return null;
        }
        
        // 检查垃圾箱编号是否在范围内
        if (boxNumber < 1 || boxNumber > Config.MAX_BOXES_PER_DIMENSION.get()) {
            return null;
        }
        
        return createTrashBox(boxNumber);
    }
    
    /**
     * 验证垃圾箱编号是否有效
     * @param boxNumber 垃圾箱编号
     * @return 是否有效
     */
    public static boolean isValidBoxNumber(int boxNumber) {
        return boxNumber >= 1 && boxNumber <= Config.MAX_BOXES_PER_DIMENSION.get();
    }
    
    /**
     * 验证垃圾箱容量是否有效
     * @param capacity 容量
     * @return 是否有效
     */
    public static boolean isValidCapacity(int capacity) {
        return capacity >= 9 && capacity <= 108 && capacity % 9 == 0;
    }
    
    /**
     * 获取下一个可用的垃圾箱编号
     * @param existingBoxCount 现有垃圾箱数量
     * @return 下一个可用编号，如果已达到最大数量返回-1
     */
    public static int getNextAvailableBoxNumber(int existingBoxCount) {
        int nextNumber = existingBoxCount + 1;
        return nextNumber <= Config.MAX_BOXES_PER_DIMENSION.get() ? nextNumber : -1;
    }
}