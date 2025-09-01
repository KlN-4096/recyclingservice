package com.klnon.recyclingservice.util.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.util.core.ErrorHandler;
import com.klnon.recyclingservice.util.core.MessageFormatter;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class UiUtils {

    /**
     * 根据配置的行数获取对应的菜单类型
     */
    public static MenuType<ChestMenu> getMenuTypeForRows() {
        return switch(Config.TRASH_BOX_ROWS.get()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    public static boolean hasModInstalled(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        return ErrorHandler.handleOperation(null, "modDetection", () -> {
            // 检查客户端是否注册了我们的mod网络通道
            ResourceLocation modChannel = ResourceLocation.fromNamespaceAndPath(
                    Recyclingservice.MODID, "main"
            );

            // NeoForge网络通道检测
            return serverPlayer.connection.hasChannel(modChannel);
        }, false); // 如果检测失败，默认认为客户端无mod（安全策略）
    }

    /**
     * 在物品交换完毕后更新垃圾箱内物品数量
     */
    public static void updateSlotAfterMove(Slot slot, int moveCount) {
        ItemStack slotItem = slot.getItem();
        //这里检查一下是否是原版的最大数量上限,比如药水,护甲等
        moveCount = Math.min(moveCount, slotItem.getMaxStackSize());
        if (slotItem.getCount() <= moveCount) {
            slot.set(ItemStack.EMPTY);
        } else{
            slotItem.shrink(moveCount);
            updateTooltip(slotItem);
            slot.set(slotItem);
        }
    }

    // 我们的lore标识符 - 前后空格作为唯一标识
    private static final String LORE_PREFIX = "  "; // 2个空格前缀
    private static final String LORE_SUFFIX = " "; // 1个空格后缀

    /**
     * 增强物品Tooltip显示真实数量
     * 使用1.21.1的DataComponent系统添加Lore信息,先清除再添加
     *
     * @param stack 原始物品堆
     */
    public static void updateTooltip(ItemStack stack) {
        // 先精确清除我们的LORE并确保数量大于64
        if (stack.getCount() <= stack.getMaxStackSize()) {
            cleanItemStack(stack);
            return;
        }

        // 获取现有lore（如果有）
        ItemLore existingLore = stack.get(DataComponents.LORE);
        List<Component> loreLines = new ArrayList<>();

        // 保留非我们添加的lore
        if (existingLore != null) {
            for (Component line : existingLore.lines()) {
                if (!isOurLoreLine(line)) {
                    loreLines.add(line);
                }
            }
        }

        // 添加我们的真实数量信息（带标识符）
        loreLines.add(Component.literal(LORE_PREFIX + LORE_SUFFIX)); // 空行分隔符
        loreLines.add(Component.literal(
                LORE_PREFIX + MessageFormatter.formatTemplate(Config.ITEM_COUNT_DISPLAY_FORMAT.get(), Map.of(
                        "current", String.valueOf(stack.getCount()),
                        "max", String.valueOf(Config.getItemStackMultiplier(stack))
                )) + LORE_SUFFIX
        ).withStyle(style -> style.withItalic(false)));

        // 应用新的lore
        stack.set(DataComponents.LORE, new ItemLore(loreLines));
    }

    /**
     * 检查是否是我们添加的lore行 - 通过前后空格标识符识别
     */
    private static boolean isOurLoreLine(Component line) {
        String text = line.getString();

        // 检查是否同时包含我们的前缀和后缀
        return text.startsWith(LORE_PREFIX) && text.endsWith(LORE_SUFFIX);
    }

    /**
     * 精确清理ItemStack的Lore，只移除我们添加的内容
     * KISS原则：最简单的解决方案
     *
     * @param item 可能包含自定义Lore的物品
     */
    public static void cleanItemStack(ItemStack item) {
        if (item.isEmpty()) {
            return;
        }

        ItemLore existingLore = item.get(DataComponents.LORE);
        if (existingLore == null) {
            return; // 没有lore就不需要清理
        }

        // 过滤掉我们的lore行，保留其他lore
        List<Component> filteredLines = existingLore.lines().stream()
                .filter(line -> !isOurLoreLine(line))
                .toList();

        // 无论过滤后是否为空，都保持LORE组件以维持组件数量一致
        item.set(DataComponents.LORE, new ItemLore(filteredLines));
    }
}
