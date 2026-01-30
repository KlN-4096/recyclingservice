package com.klnon.recyclingservice.foundation.utility;

import com.klnon.recyclingservice.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UI工具类 - 负责菜单类型获取和物品Tooltip管理
 */
public class UiHelper {

    // ==================== 常量 ====================

    /**
     * Lore行标识符（用于识别和清理我们添加的lore）
     */
    private static final String LORE_PREFIX = "  ";
    private static final String LORE_SUFFIX = " ";

    // ==================== 菜单类型 ====================

    /**
     * 根据配置的行数获取对应的箱子菜单类型
     */
    public static MenuType<ChestMenu> getMenuTypeForRows() {
        return switch (Config.GAMEPLAY.trashBoxRows.get()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    // ==================== Tooltip 管理 ====================

    /**
     * 更新物品Tooltip显示真实数量
     */
    public static void updateTooltip(ItemStack stack) {
        updateTooltip(stack, 0);
    }

    /**
     * 更新物品Tooltip显示真实数量和邮费
     * 使用1.21.1的DataComponent系统添加Lore信息
     *
     * @param stack       物品堆
     * @param postageCost 邮费（0表示不显示）
     */
    public static void updateTooltip(ItemStack stack, int postageCost) {
        cleanItemStack(stack);

        if (stack.isEmpty()) {
            return;
        }

        // 只有超量或有邮费时才显示
        boolean shouldShow = stack.getCount() > stack.getMaxStackSize() || postageCost > 0;
        if (!shouldShow) {
            return;
        }

        // 获取现有lore
        List<Component> loreLines = new ArrayList<>();
        ItemLore existingLore = stack.get(DataComponents.LORE);
        if (existingLore != null) {
            loreLines.addAll(existingLore.lines());
        }

        // 添加空行分隔
        loreLines.add(createLoreLine(""));

        // 添加数量信息
        String countText = MessageHelper.formatTemplate(Config.MESSAGE.itemCountDisplayFormat.get(), Map.of(
                "current", String.valueOf(stack.getCount()),
                "max", String.valueOf(Config.getItemStackMultiplier(stack))
        ));
        loreLines.add(createLoreLine(countText));

        // 添加邮费信息
        if (postageCost > 0) {
            String costText = MessageHelper.formatTemplate(Config.MESSAGE.postageCostDisplayFormat.get(), Map.of(
                    "cost", String.valueOf(postageCost),
                    "item", formatItemName(Config.getPaymentItem().getPath())
            ));
            loreLines.add(createLoreLine(costText));
        }

        stack.set(DataComponents.LORE, new ItemLore(loreLines));
    }

    /**
     * 清理物品上我们添加的lore行
     */
    public static void cleanItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemLore existingLore = stack.get(DataComponents.LORE);
        if (existingLore == null) {
            return;
        }

        // 过滤掉我们的lore行
        List<Component> filteredLines = existingLore.lines().stream()
                .filter(line -> !isOurLoreLine(line))
                .toList();
        // 无论过滤后是否为空，都保持LORE组件以维持组件数量一致
        stack.set(DataComponents.LORE, new ItemLore(filteredLines));
    }

    // ==================== 私有工具方法 ====================

    /**
     * 创建带标识符的lore行
     */
    private static Component createLoreLine(String text) {
        return Component.literal(LORE_PREFIX + text + LORE_SUFFIX)
                .withStyle(style -> style.withItalic(false));
    }

    /**
     * 检查是否是我们添加的lore行
     */
    private static boolean isOurLoreLine(Component line) {
        String text = line.getString();
        return text.startsWith(LORE_PREFIX) && text.endsWith(LORE_SUFFIX);
    }

    /**
     * 格式化物品名称（snake_case → Title Case）
     * 例如：diamond_sword → Diamond Sword
     */
    private static String formatItemName(String itemPath) {
        String[] parts = itemPath.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }

        return result.toString();
    }
}