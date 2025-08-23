package com.klnon.recyclingservice.util.other;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.Config;

public class MessageSender {
    /**
     * 显示ActionBar消息
     */
    public static void showActionBar(MinecraftServer server, String message, int color) {
        Component component = Component.literal(message).withStyle(style -> 
            style.withColor(color).withBold(true));
        
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(component);
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
    
    /**
     * 使用配置颜色显示ActionBar消息
     */
    public static void showActionBarWithConfigColor(MinecraftServer server, String message, String colorType) {
        int color = switch (colorType.toLowerCase()) {
            case "success" -> Config.getSuccessColor();
            case "error" -> Config.getErrorColor();
            case "warning_normal" -> Config.parseColor(Config.WARNING_COLOR_NORMAL.get());
            case "warning_urgent" -> Config.parseColor(Config.WARNING_COLOR_URGENT.get());
            case "warning_critical" -> Config.parseColor(Config.WARNING_COLOR_CRITICAL.get());
            default -> 0xFFFFFF; // 白色
        };
        showActionBar(server, message, color);
    }
    
    /**
     * 发送错误消息
     */
    public static void sendErrorMessage(ServerPlayer player, String translationKey) {
        Component message = Component.translatable(translationKey).withStyle(style ->
            style.withColor(Config.getErrorColor()));
        player.sendSystemMessage(message);
    }

    /**
     * 发送普通消息
     */
    public static void sendMessage(ServerPlayer player, String message) {
        Component component = Component.literal(message);
        player.sendSystemMessage(component);
    }
    
    /**
     * 发送带颜色的消息
     */
    public static void sendColoredMessage(ServerPlayer player, String message, int color) {
        Component component = Component.literal(message).withStyle(style ->
            style.withColor(color));
        player.sendSystemMessage(component);
    }
    
    /**
     * 发送成功消息
     */
    public static void sendSuccessMessage(ServerPlayer player, String message) {
        sendColoredMessage(player, message, Config.getSuccessColor());
    }
    
    /**
     * 发送格式化消息（支持参数替换）
     */
    public static String formatMessage(String template, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(args[i]));
            }
        }
        // 同时支持命名参数
        if (args.length >= 1 && args[0] instanceof String key) {
            switch (key) {
                case "ui_type" -> result = result.replace("{ui_type}", args.length > 1 ? String.valueOf(args[1]) : "");
                case "count" -> result = result.replace("{count}", args.length > 1 ? String.valueOf(args[1]) : "0");
            }
        }
        return result;
    }
}
