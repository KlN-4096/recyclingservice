package com.klnon.recyclingservice.util.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import com.klnon.recyclingservice.Config;

import java.util.Map;

/**
 * 消息格式化工具类 - 处理所有消息模板和格式化逻辑
 */
public class MessageFormatter {

    /**
     * 统一的字符串模板处理工具
     * @param template 模板字符串，使用{key}格式的占位符
     * @param params 替换参数映射
     * @return 替换后的字符串
     */
    public static String formatTemplate(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 获取格式化的警告消息
     */
    public static String getWarningMessage(int remainingSeconds) {
        return formatTemplate(Config.WARNING_MESSAGE.get(), Map.of("time", String.valueOf(remainingSeconds)));
    }

    /**
     * 获取格式化的物品过多警告消息（支持点击传送）
     */
    public static Component getItemWarningMessage(int itemCount, int worldX, int worldZ, int ticketLevel) {
        String message = formatTemplate(Config.TOO_MANY_ITEMS_WARNING_MESSAGE.get(), Map.of(
            "count", String.valueOf(itemCount),
            "x", String.valueOf(worldX),
            "z", String.valueOf(worldZ),
            "ticket", String.valueOf(ticketLevel)
        ));
        
        return Component.literal(message)
                .withStyle(style -> style
                    .withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/tp @s " + worldX + " ~ " + worldZ))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§7Click to teleport (OP required)\n§7Coordinate: " + worldX + ", " + worldZ + "\n§7Ticket Level: " + ticketLevel)))
                );
    }

    /**
     * 构建详细清理完成消息
     * @param dimensionStats 各维度清理统计信息
     * @return 清理结果的聊天组件
     */
    public static Component getDetailedCleanupMessage(Map<ResourceLocation, ?> dimensionStats) {
        MutableComponent mainComponent = Component.literal(Config.CLEANUP_RESULT_HEADER.get());
        
        // 按字典序排序处理所有维度
        dimensionStats.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(ResourceLocation::compareTo))
            .forEach(entry -> {
                Component dimensionEntry = formatDimensionEntry(entry.getKey(), entry.getValue());
                if (dimensionEntry != null) {
                    mainComponent.append(Component.literal("\n")).append(dimensionEntry);
                }
            });
        
        return mainComponent;
    }

    /**
     * 格式化单个维度的清理条目
     * @param dimensionId 维度ID
     * @param stats 统计数据对象
     * @return 格式化后的条目Component，如果获取失败返回null
     */
    public static Component formatDimensionEntry(ResourceLocation dimensionId, Object stats) {
        if (!(stats instanceof com.klnon.recyclingservice.service.CleanupService.DimensionCleanupStats dimensionStats)) {
            return null;
        }
        
        // 创建基础文本
        String baseText = formatTemplate(Config.DIMENSION_ENTRY_FORMAT.get(), Map.of(
            "name", getDimensionDisplayName(dimensionId),
            "items", String.valueOf(dimensionStats.itemsCleaned()),
            "entities", String.valueOf(dimensionStats.projectilesCleaned())
        ));
        
        // 创建可点击的按钮
        String buttonText = formatTemplate(Config.TRASH_BOX_BUTTON_TEXT.get(), 
            Map.of("name", getDimensionDisplayName(dimensionId)));
        String hoverText = formatTemplate(Config.TRASH_BOX_BUTTON_HOVER.get(), 
            Map.of("name", getDimensionDisplayName(dimensionId)));
            
        MutableComponent button = Component.literal(buttonText)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                "/bin open " + dimensionId + " 1"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hoverText).withStyle(ChatFormatting.YELLOW))));
        
        // 组合文本和按钮
        return Component.literal(baseText).append(button);
    }

    /**
     * 获取维度的显示名称 - 去前缀处理
     * @param dimensionId 维度ID
     * @return 去前缀后的维度名称
     */
    public static String getDimensionDisplayName(ResourceLocation dimensionId) {
        String dimString = dimensionId.toString();
        return dimString.contains(":") ? 
            dimString.substring(dimString.indexOf(':') + 1) : dimString;
    }
}