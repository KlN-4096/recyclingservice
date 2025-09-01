package com.klnon.recyclingservice.util.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 统一消息发送工具 - 简化后的消息发送接口
 */
public class MessageSender {
    
    /**
     * 消息类型枚举
     */
    public enum MessageType {
        SUCCESS(0x55FF55),      // 绿色
        ERROR(0xFF5555),        // 红色
        WARNING(0xFFAA00),      // 黄色
        DEFAULT(0xFFFFFF);      // 白色
        
        private final int color;
        
        MessageType(int color) {
            this.color = color;
        }
        
        public int getColor() {
            return color;
        }
    }
    
    /**
     * 发送类型枚举
     */
    public enum Target {
        ACTION_BAR,    // 发送到ActionBar
        CHAT          // 发送到聊天框
    }
    
    /**
     * 统一消息发送方法 - 发送给所有玩家
     * @param server 服务器实例
     * @param message 消息内容
     * @param messageType 消息类型（颜色）
     * @param target 发送目标（ActionBar或聊天）
     */
    public static void sendToAll(MinecraftServer server, String message, MessageType messageType, Target target) {
        Component component = Component.literal(message).withStyle(style -> 
            style.withColor(messageType.getColor())
                 .withBold(target == Target.ACTION_BAR)); // ActionBar消息加粗
        
        if (target == Target.ACTION_BAR) {
            ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(component);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
            }
        } else {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(component);
            }
        }
    }
    
    /**
     * 发送Component消息给所有玩家 - 保留完整Component功能
     */
    public static void sendChatMessage(MinecraftServer server, Component component) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    /**
     * 发送翻译消息给单个玩家
     * @param player 目标玩家
     * @param translationKey 翻译键
     * @param messageType 消息类型
     */
    public static void sendTranslatableMessage(ServerPlayer player, String translationKey, MessageType messageType) {
        Component message = Component.translatable(translationKey).withStyle(style ->
            style.withColor(messageType.getColor()));
        player.sendSystemMessage(message);
    }
    
    // === 便捷方法 - 为了保持向后兼容性 ===
    
    /**
     * 显示ActionBar消息（兼容旧接口）
     */
    public static void showActionBar(MinecraftServer server, String message, int color) {
        MessageType messageType = findMessageTypeByColor(color);
        sendToAll(server, message, messageType, Target.ACTION_BAR);
    }
    
    /**
     * 根据颜色值找到对应的MessageType
     */
    private static MessageType findMessageTypeByColor(int color) {
        for (MessageType type : MessageType.values()) {
            if (type.getColor() == color) {
                return type;
            }
        }
        return MessageType.DEFAULT;
    }
}