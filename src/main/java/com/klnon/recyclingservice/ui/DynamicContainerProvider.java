package com.klnon.recyclingservice.ui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.ui.UiUtils;

import javax.annotation.Nonnull;


/**
 * 动态容器提供者 - 智能UI选择
 * 根据客户端是否安装mod，动态选择UI类型：
 * - 无mod客户端：使用原版ChestMenu.sixRows() - 100%兼容
 * - 有mod客户端：使用定制TrashBoxMenu - 增强功能
 * 优势：
 * - 零破坏性：无mod客户端完全正常使用
 * - 渐进增强：有mod客户端获得更好体验
 * - 统一数据：两种UI共享同一TrashBox数据
 */
public record DynamicContainerProvider(TrashBox trashBox, Component title) implements MenuProvider {

    /**
     * 创建动态容器提供者
     *
     * @param trashBox 垃圾箱数据实例
     * @param title    容器标题
     */
    public DynamicContainerProvider {
    }

    @Override
    public @Nonnull Component getDisplayName() {
        return title;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInventory, @Nonnull Player player) {
        // 直接使用TrashBox作为Container

        // 检测客户端是否有mod
        if (UiUtils.hasModInstalled(player)) {
            // TODO: 客户端有mod时，返回定制TrashBoxMenu或特殊UI
            // return new EnhancedTrashBoxMenu(containerId, playerInventory, trashBox);
            // 暂时使用TrashBoxMenu，等特殊UI实现后再切换
            return new TrashBoxMenu(containerId, playerInventory, trashBox);
        } else {
            // 客户端无mod，使用TrashBoxMenu（保持64个限制）
            return new TrashBoxMenu(containerId, playerInventory, trashBox);
        }
    }

    /**
     * 创建带自定义标题的垃圾箱容器提供者
     *
     * @param trashBox 垃圾箱实例
     * @param title    自定义标题
     * @return 配置好的容器提供者
     */
    public static MenuProvider create(TrashBox trashBox, Component title) {
        return new DynamicContainerProvider(trashBox, title);
    }
}