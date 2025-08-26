package com.klnon.recyclingservice.core;

import net.minecraft.resources.ResourceLocation;
import com.klnon.recyclingservice.Config;

/**
 * 垃圾箱工厂类 - 负责创建垃圾箱实例
 * 遵循KISS原则：简单的工厂模式，统一创建逻辑
 */
public class TrashBoxFactory {
    

    /**
     * 为指定维度创建垃圾箱
     * @param dimensionId 维度ID
     * @param boxNumber 垃圾箱编号
     * @return 垃圾箱实例，如果维度不支持返回null
     */
    public static TrashBox createForDimension(ResourceLocation dimensionId, int boxNumber) {
        // 检查垃圾箱编号是否在范围内
        if (boxNumber < 1 || boxNumber > Config.getMaxBoxes()) {
            return null;
        }
        return new TrashBox(Config.getTrashBoxRows()*9, boxNumber, dimensionId);
    }
    
    /**
     * 验证垃圾箱编号是否有效
     * @param boxNumber 垃圾箱编号
     * @return 是否有效
     */
    public static boolean isValidBoxNumber(int boxNumber) {
        return boxNumber >= 1 && boxNumber <= Config.getMaxBoxes();
    }
}