package com.klnon.recyclingservice.foundation.utility;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

import com.klnon.recyclingservice.Config;
import java.util.Map;

/**
 * 消息工具类 - 负责消息格式化和发送
 * 包含：模板处理、消息构建、多种发送方式
 */
public class MessageHelper {
    
    /**
     * 消息类型枚举 - 定义消息颜色
     */
    public static final int WARNING = 0xFFAA00;
    
    // === 消息格式化功能 ===
    
    /**
     * 统一的字符串模板处理工具
     * @param template 模板字符串，包含{key}占位符
     * @param params 参数映射
     * @return 格式化后的字符串
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
        return formatTemplate(Config.MESSAGE.warningMessage.get(), 
            Map.of("time", String.valueOf(remainingSeconds)));
    }

    /**
     * 构建详细清理完成消息
     */
    public static Component getDetailedCleanupMessage(MinecraftServer server) {
        MutableComponent mainComponent = Component.literal(Config.MESSAGE.cleanupResultHeader.get());

        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();

            int itemCount = CleanupManager.getEntityCount(CleanupManager.ITEM,dimensionId);
            int projectileCount = CleanupManager.getEntityCount(CleanupManager.PROJECTILE, dimensionId);

            // 只有有实体的维度才添加到消息中
            if (itemCount > 0 || projectileCount > 0) {
                Component dimensionEntry = formatDimensionEntry(dimensionId, itemCount, projectileCount);
                mainComponent.append(Component.literal("\n")).append(dimensionEntry);
            }
        }

        return mainComponent;
    }

    /**
     * 格式化单个维度的清理条目
     */
    private static Component formatDimensionEntry(ResourceLocation dimensionId, int itemCount, int projectileCount) {
        String dimensionName = dimensionId.toString();
        dimensionName = dimensionName.substring(dimensionName.indexOf(':') + 1);

        // 创建基础文本
        String baseText = formatTemplate(Config.MESSAGE.dimensionEntryFormat.get(), Map.of(
            "name", dimensionName,
            "items", String.valueOf(itemCount),
            "entities", String.valueOf(projectileCount)
        ));

        // 创建可点击的按钮
        String buttonText = formatTemplate(Config.MESSAGE.trashBoxButtonText.get(),
            Map.of("name", dimensionName));
        String hoverText = formatTemplate(Config.MESSAGE.trashBoxButtonHover.get(),
            Map.of("name", dimensionName));

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

    
    // === 消息发送功能 ===
    /**
     * 发送Component消息给所有玩家
     */
    public static void sendChatToAll(MinecraftServer server, Component component) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    /**
     * 发送消息给所有玩家的Actionbar上
     */
    public static void sendActionBarToAll(MinecraftServer server, String message, int color) {
        Component component = Component.literal(message).withStyle(style ->
                style.withColor(color));

        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(component);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
}
