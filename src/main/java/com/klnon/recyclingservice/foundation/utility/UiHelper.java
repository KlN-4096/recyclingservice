package com.klnon.recyclingservice.foundation.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.klnon.recyclingservice.Config;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class UiHelper {

    // 我们的lore标识符 - 前后空格作为唯一标识
    private static final String LORE_PREFIX = "  ";
    private static final String LORE_SUFFIX = " ";

    /**
     * 根据配置的行数获取对应的菜单类型
     */
    public static MenuType<ChestMenu> getMenuTypeForRows() {
        return switch(Config.GAMEPLAY.trashBoxRows.get()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    /**
     * 增强物品Tooltip显示真实数量
     * 使用1.21.1的DataComponent系统添加Lore信息,先清除再添加
     *
     * @param stack 原始物品堆
     */
    public static void updateTooltip(ItemStack stack) {
        // 先清理现有的我们添加的lore，然后获取干净的lore
        cleanItemStack(stack);
        if (stack.getCount() <= stack.getMaxStackSize()) return;
        // 获取清理后的lore（如果有）
        ItemLore existingLore = stack.get(DataComponents.LORE);
        List<Component> loreLines = new ArrayList<>();

        // 保留现有的其他lore
        if (existingLore != null) {
            loreLines.addAll(existingLore.lines());
        }

        // 添加我们的真实数量信息（带标识符）
        loreLines.add(Component.literal(LORE_PREFIX + LORE_SUFFIX)); // 空行分隔符
        loreLines.add(Component.literal(
                LORE_PREFIX + MessageHelper.formatTemplate(Config.MESSAGE.itemCountDisplayFormat.get(), Map.of(
                        "current", String.valueOf(stack.getCount()),
                        "max", String.valueOf(Config.getItemStackMultiplier(stack))
                )) + LORE_SUFFIX
        ).withStyle(style -> style.withItalic(false)));

        // 应用新的lore
        stack.set(DataComponents.LORE, new ItemLore(loreLines));
    }

    /**
     * 精确清理ItemStack的Lore，只移除我们添加的内容
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

    /**
     * 检查是否是我们添加的lore行 - 通过前后空格标识符识别
     */
    private static boolean isOurLoreLine(Component line) {
        String text = line.getString();
        // 检查是否同时包含我们的前缀和后缀
        return text.startsWith(LORE_PREFIX) && text.endsWith(LORE_SUFFIX);
    }
}