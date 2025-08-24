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
        });
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

            // 添加一些测试物品，使用自定义堆叠倍数
            // 方块类 (Blocks)
            ItemStack cobblestone = new ItemStack(Items.COBBLESTONE);
            testBox.addItem(new ItemStack(Items.COBBLESTONE, Config.getItemStackMultiplier(cobblestone)));

            // 原材料类 (Raw Materials)
            ItemStack coal = new ItemStack(Items.COAL);
            testBox.addItem(new ItemStack(Items.COAL, Config.getItemStackMultiplier(coal)));

            // 工具类 (Tools)
            ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
            testBox.addItem(new ItemStack(Items.DIAMOND_PICKAXE, Config.getItemStackMultiplier(pickaxe)));

            // 武器类 (Weapons)
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            testBox.addItem(new ItemStack(Items.DIAMOND_SWORD, Config.getItemStackMultiplier(sword)));

            // 防具类 (Armor)
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            testBox.addItem(new ItemStack(Items.DIAMOND_HELMET, Config.getItemStackMultiplier(helmet)));

            // 食物类 (Food)
            ItemStack bread = new ItemStack(Items.BREAD);
            testBox.addItem(new ItemStack(Items.BREAD, Config.getItemStackMultiplier(bread)));

            // 药水类 (Potions)
            ItemStack potion = new ItemStack(Items.POTION);
            testBox.addItem(new ItemStack(Items.POTION, Config.getItemStackMultiplier(potion)));

            // 红石类 (Redstone)
            ItemStack redstone = new ItemStack(Items.REDSTONE);
            testBox.addItem(new ItemStack(Items.REDSTONE, Config.getItemStackMultiplier(redstone)));

            // 装饰类 (Decorative)
            ItemStack flowerPot = new ItemStack(Items.FLOWER_POT);
            testBox.addItem(new ItemStack(Items.FLOWER_POT, Config.getItemStackMultiplier(flowerPot)));

            // 运输类 (Transportation)
            ItemStack minecart = new ItemStack(Items.MINECART);
            testBox.addItem(new ItemStack(Items.MINECART, Config.getItemStackMultiplier(minecart)));

            // 音乐类 (Music)
            ItemStack musicDisc = new ItemStack(Items.MUSIC_DISC_CAT);
            testBox.addItem(new ItemStack(Items.MUSIC_DISC_CAT, Config.getItemStackMultiplier(musicDisc)));

            // 生物蛋类 (Spawn Eggs)
            ItemStack spawnEgg = new ItemStack(Items.COW_SPAWN_EGG);
            testBox.addItem(new ItemStack(Items.COW_SPAWN_EGG, Config.getItemStackMultiplier(spawnEgg)));

            // 杂项类 (Miscellaneous)
            ItemStack book = new ItemStack(Items.BOOK);
            testBox.addItem(new ItemStack(Items.BOOK, Config.getItemStackMultiplier(book)));

            // 为垃圾箱起标题
            Component title = Component.literal(Config.getTestBoxTitle(getUIType(player)));
            MenuProvider provider = DynamicContainerProvider.create(testBox, title);

            player.openMenu(provider);

            // 发送测试信息
            MessageSender.sendMessage(player, Config.getTestBoxOpenedMessage(getUIType(player)));

            return true;
        });
    }
}