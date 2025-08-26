package com.klnon.recyclingservice.ui;

import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.other.ErrorHandler;
import com.klnon.recyclingservice.util.other.MessageSender;
import com.klnon.recyclingservice.util.other.UiUtils;
import com.klnon.recyclingservice.core.DimensionTrashManager;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.Config;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * UI工具类 - 提供垃圾箱UI访问的便捷方法
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
    public static boolean openTrashBox(ServerPlayer player, ResourceLocation dimensionId, int boxNumber, DimensionTrashManager trashManager) {
        return ErrorHandler.handleOperation(player, "openTrashBox", () -> {
            // 获取指定的垃圾箱
            TrashBox trashBox = trashManager.getOrCreateTrashBox(dimensionId, boxNumber);
            if (trashBox == null)
                return false;

            // 创建动态容器提供者
            Component title = Component.translatable("container.recyclingservice.trash_box_with_dimension",
                dimensionId.toString(), boxNumber);
            MenuProvider provider = DynamicContainerProvider.create(trashBox, title);

            // 打开容器界面
            player.openMenu(provider);

            // 记录日志（调试用）
            Recyclingservice.LOGGER.debug("Player {} opened trash box {}-{}",
                player.getName().getString(), dimensionId, boxNumber);

            return true;
        }, false);
    }
    
    /**
     * 获取玩家的UI类型描述
     * 用于调试和状态显示
     * 
     * @param player 玩家
     * @return UI类型描述
     */
    public static String getUIType(Player player) {
        return UiUtils.hasModInstalled(player) ? "Enhanced" : "Vanilla";
    }
    
    /**
     * 测试接口：创建一个测试垃圾箱并打开UI
     * 仅用于开发测试
     * 
     * @param player 玩家
     * @return 是否成功
     */
    public static boolean openTestTrashBox(ServerPlayer player) {
        return ErrorHandler.handleOperation(player, "openTestTrashBox", () -> {
            // 创建测试垃圾箱
            TrashBox testBox = new TrashBox(54, 999);

            // 添加测试物品 - 简化后的代码
            net.minecraft.world.item.Item[] testItems = {
                Items.COBBLESTONE,      // 方块类
                Items.COAL,            // 原材料类
                Items.DIAMOND_PICKAXE, // 工具类
                Items.DIAMOND_SWORD,   // 武器类
                Items.DIAMOND_HELMET,  // 防具类
                Items.BREAD,           // 食物类
                Items.POTION,          // 药水类
                Items.REDSTONE,        // 红石类
                Items.FLOWER_POT,      // 装饰类
                Items.MINECART,        // 运输类
                Items.MUSIC_DISC_CAT,  // 音乐类
                Items.COW_SPAWN_EGG,   // 生物蛋类
                Items.BOOK             // 杂项类
            };
            
            for (net.minecraft.world.item.Item item : testItems) {
                ItemStack itemStack = new ItemStack(item);
                testBox.addItem(new ItemStack(item, Config.getItemStackMultiplier(itemStack)));
            }

            // 为垃圾箱起标题
            Component title = Component.literal(Config.getTestBoxTitle(getUIType(player)));
            MenuProvider provider = DynamicContainerProvider.create(testBox, title);

            player.openMenu(provider);

            // 发送测试信息
            MessageSender.sendMessage(player, Config.getTestBoxOpenedMessage(getUIType(player)), MessageSender.MessageType.DEFAULT);

            return true;
        }, false);
    }
}