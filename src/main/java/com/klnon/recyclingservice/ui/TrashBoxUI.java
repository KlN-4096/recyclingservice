package com.klnon.recyclingservice.ui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.UiChoose;
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
     * 获取玩家的UI类型描述
     * 用于调试和状态显示
     * 
     * @param player 玩家
     * @return UI类型描述
     */
    public static String getUIType(Player player) {
        return UiChoose.hasModInstalled(player) ? "Enhanced" : "Vanilla";
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
            // 方块类 (Blocks)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 6400));

            // 原材料类 (Raw Materials)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 6400));

            // 工具类 (Tools)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE, 1));

            // 武器类 (Weapons)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD, 1));

            // 防具类 (Armor)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET, 1));

            // 食物类 (Food)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 6400));

            // 药水类 (Potions)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION, 6400));

            // 红石类 (Redstone)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE, 6400));

            // 装饰类 (Decorative)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FLOWER_POT, 6400));

            // 运输类 (Transportation)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MINECART, 6400));

            // 音乐类 (Music)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_CAT, 1));

            // 生物蛋类 (Spawn Eggs)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG, 6400));

            // 杂项类 (Miscellaneous)
            testBox.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK, 6400));
            
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