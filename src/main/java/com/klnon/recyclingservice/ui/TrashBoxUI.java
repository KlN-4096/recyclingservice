package com.klnon.recyclingservice.ui;

import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.core.ErrorHandler;
import com.klnon.recyclingservice.core.TrashManager;
import com.klnon.recyclingservice.Recyclingservice;

/**
 * UI工具类 - 提供垃圾箱UI访问的便捷方法
 * 功能：
 * - 简化UI打开流程
 * - 统一错误处理
 */
public class TrashBoxUI {
    
    /**
     * 为玩家打开指定维度的垃圾箱UI
     * 
     * @param player 玩家
     * @param dimensionId 维度ID
     * @param boxNumber 垃圾箱编号
     * @param trashManager 垃圾箱管理器
     * @return 是否成功打开
     */
    public static boolean openTrashBox(ServerPlayer player, ResourceLocation dimensionId, int boxNumber, TrashManager trashManager) {
        return ErrorHandler.handleOperation(player, "openTrashBox", () -> {
            // 获取指定的垃圾箱
            TrashBox trashBox = trashManager.getOrCreateTrashBox(dimensionId, boxNumber);
            if (trashBox == null)
                return false;

            // 创建简洁的标题：例如 "overworld-1"
            String dimensionName = dimensionId.getPath(); // 获取路径部分，例如 "overworld"
            Component title = Component.literal(dimensionName + "-" + boxNumber);
            MenuProvider provider = new DynamicContainerProvider(trashBox, title);

            // 打开容器界面
            player.openMenu(provider);

            // 记录日志（调试用）
            Recyclingservice.LOGGER.debug("Player {} opened trash box {}-{}",
                player.getName().getString(), dimensionId, boxNumber);

            return true;
        }, false);
    }
}