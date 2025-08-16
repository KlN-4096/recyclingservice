package com.klnon.recyclingservice.ui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.core.DimensionTrashManager;
import com.klnon.recyclingservice.Recyclingservice;

/**
 * UI工具类 - 提供垃圾箱UI访问的便捷方法
 * 
 * 功能：
 * - 简化UI打开流程
 * - 统一错误处理
 * - 提供调试和测试接口
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
    public static boolean openTrashBox(Player player, ResourceLocation dimensionId, int boxNumber, DimensionTrashManager trashManager) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        
        try {
            // 获取指定的垃圾箱
            TrashBox trashBox = trashManager.getOrCreateTrashBox(dimensionId, boxNumber);
            if (trashBox == null) {
                sendErrorMessage(serverPlayer, "trash_box.error.not_found");
                return false;
            }
            
            // 创建动态容器提供者
            Component title = Component.translatable("container.recyclingservice.trash_box_with_dimension", 
                dimensionId.toString(), boxNumber);
            MenuProvider provider = DynamicContainerProvider.create(trashBox, title);
            
            // 打开容器界面
            serverPlayer.openMenu(provider);
            
            // 记录日志（调试用）
            Recyclingservice.LOGGER.debug("Player {} opened trash box {}-{}", 
                player.getName().getString(), dimensionId, boxNumber);
            
            return true;
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to open trash box for player {}: {}", 
                player.getName().getString(), e.getMessage());
            sendErrorMessage(serverPlayer, "trash_box.error.general");
            return false;
        }
    }
    
    /**
     * 为玩家打开当前维度的垃圾箱UI
     * 
     * @param player 玩家
     * @param boxNumber 垃圾箱编号
     * @param trashManager 垃圾箱管理器
     * @return 是否成功打开
     */
    public static boolean openCurrentDimensionTrashBox(Player player, int boxNumber, DimensionTrashManager trashManager) {
        ResourceLocation dimensionId = player.level().dimension().location();
        return openTrashBox(player, dimensionId, boxNumber, trashManager);
    }
    
    /**
     * 检测玩家客户端是否有mod
     * 对外暴露的检测接口，可用于命令等场景
     * 
     * @param player 玩家
     * @return 客户端是否有mod
     */
    public static boolean hasModInstalled(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        
        try {
            ResourceLocation modChannel = ResourceLocation.fromNamespaceAndPath(
                Recyclingservice.MODID, "main"
            );
            return serverPlayer.connection.hasChannel(modChannel);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取玩家的UI类型描述
     * 用于调试和状态显示
     * 
     * @param player 玩家
     * @return UI类型描述
     */
    public static String getUIType(Player player) {
        return hasModInstalled(player) ? "Enhanced" : "Vanilla";
    }
    
    /**
     * 发送错误消息给玩家
     * 
     * @param player 玩家
     * @param translationKey 翻译键
     */
    private static void sendErrorMessage(ServerPlayer player, String translationKey) {
        Component message = Component.translatable(translationKey).withStyle(style -> 
            style.withColor(0xFF5555)); // 红色错误消息
        player.sendSystemMessage(message);
    }
    
    /**
     * 测试接口：创建一个测试垃圾箱并打开UI
     * 仅用于开发测试
     * 
     * @param player 玩家
     * @return 是否成功
     */
    public static boolean openTestTrashBox(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        
        try {
            // 创建测试垃圾箱
            TrashBox testBox = new TrashBox(54, 999);
            
            // 添加一些测试物品
            testBox.addItem(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.COBBLESTONE, 96));
            testBox.addItem(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.DIRT, 32));
            
            // 创建提供者并打开
            Component title = Component.literal("§6测试垃圾箱 §7(UI类型: " + getUIType(player) + ")");
            MenuProvider provider = DynamicContainerProvider.create(testBox, title);
            
            serverPlayer.openMenu(provider);
            
            // 发送测试信息
            Component info = Component.literal("§a已打开测试垃圾箱 §7| UI类型: §b" + getUIType(player));
            serverPlayer.sendSystemMessage(info);
            
            return true;
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to open test trash box: {}", e.getMessage());
            return false;
        }
    }
}