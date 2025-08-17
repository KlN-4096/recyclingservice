package com.klnon.recyclingservice.ui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import com.klnon.recyclingservice.core.TrashBox;

import javax.annotation.Nonnull;

import com.klnon.recyclingservice.Recyclingservice;

/**
 * 动态容器提供者 - 智能UI选择
 * 
 * 根据客户端是否安装mod，动态选择UI类型：
 * - 无mod客户端：使用原版ChestMenu.sixRows() - 100%兼容
 * - 有mod客户端：使用定制TrashBoxMenu - 增强功能
 * 
 * 优势：
 * - 零破坏性：无mod客户端完全正常使用
 * - 渐进增强：有mod客户端获得更好体验
 * - 统一数据：两种UI共享同一TrashBox数据
 */
public class DynamicContainerProvider implements MenuProvider {
    
    private final TrashBox trashBox;
    private final Component title;
    
    /**
     * 创建动态容器提供者
     * 
     * @param trashBox 垃圾箱数据实例
     * @param title 容器标题
     */
    public DynamicContainerProvider(TrashBox trashBox, Component title) {
        this.trashBox = trashBox;
        this.title = title;
    }
    
    /**
     * 便捷构造方法 - 使用默认标题
     * 
     * @param trashBox 垃圾箱数据实例
     */
    public DynamicContainerProvider(TrashBox trashBox) {
        this(trashBox, Component.translatable("container.recyclingservice.trash_box", trashBox.getBoxNumber()));
    }
    
    @Override
    public Component getDisplayName() {
        return title;
    }
    
    @Override
    public AbstractContainerMenu createMenu(int containerId,@Nonnull Inventory playerInventory,@Nonnull Player player) {
        // 创建通用容器适配器
        UniversalTrashContainer container = new UniversalTrashContainer(trashBox);
        
        // 检测客户端是否有mod
        if (hasModOnClient(player)) {
            // TODO: 客户端有mod时，返回定制TrashBoxMenu或特殊UI
            // return new EnhancedTrashBoxMenu(containerId, playerInventory, container);
            
            // 暂时使用TrashBoxMenu，等特殊UI实现后再切换
            return new TrashBoxMenu(containerId, playerInventory, container);
        } else {
            // 客户端无mod，使用TrashBoxMenu（保持64个限制）
            return new TrashBoxMenu(containerId, playerInventory, container);
        }
    }
    
    /**
     * 检测客户端是否安装了本mod
     * 
     * 原理：
     * - NeoForge会在客户端连接时协商网络通道
     * - 如果客户端有mod，会注册对应的网络通道
     * - 通过检查通道是否存在来判断mod安装状态
     * 
     * @param player 玩家实例
     * @return 客户端是否有mod
     */
    private static boolean hasModOnClient(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        
        try {
            // 检查客户端是否注册了我们的mod网络通道
            ResourceLocation modChannel = ResourceLocation.fromNamespaceAndPath(
                Recyclingservice.MODID, "main"
            );
            
            // NeoForge网络通道检测
            return serverPlayer.connection.hasChannel(modChannel);
        } catch (Exception e) {
            // 如果检测失败，默认认为客户端无mod（安全策略）
            Recyclingservice.LOGGER.debug("Client mod detection failed for player {}: {}", 
                player.getName().getString(), e.getMessage());
            return false;
        }
    }
    
    /**
     * 创建垃圾箱容器提供者的工厂方法
     * 
     * @param trashBox 垃圾箱实例
     * @return 配置好的容器提供者
     */
    public static MenuProvider create(TrashBox trashBox) {
        return new DynamicContainerProvider(trashBox);
    }
    
    /**
     * 创建带自定义标题的垃圾箱容器提供者
     * 
     * @param trashBox 垃圾箱实例
     * @param title 自定义标题
     * @return 配置好的容器提供者
     */
    public static MenuProvider create(TrashBox trashBox, Component title) {
        return new DynamicContainerProvider(trashBox, title);
    }
}