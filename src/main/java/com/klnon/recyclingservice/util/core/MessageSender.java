package com.klnon.recyclingservice.util.core;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class MessageSender {
    
    /**
     * 消息类型枚举 - 简化版
     */
    public enum MessageType {
        SUCCESS(0x55FF55),      // 绿色
        ERROR(0xFF5555),        // 红色
        WARNING(0xFFAA00),      // 黄色 - 统一的警告样式
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
     * 发送聊天消息到所有在线玩家（支持完整的Component功能，包括tooltip）
     * @param server 服务器实例
     * @param component 完整的Component消息（可包含悬停事件等）
     */
    public static void sendChatMessage(MinecraftServer server, Component component) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }
    
    
    /**
     * 统一消息发送方法
     * @param player 玩家
     * @param message 消息内容
     * @param messageType 消息类型
     */
    public static void sendMessage(ServerPlayer player, String message, MessageType messageType) {
        Component component = Component.literal(message).withStyle(style ->
            style.withColor(messageType.getColor()));
        player.sendSystemMessage(component);
    }
    
    /**
     * 发送翻译消息
     * @param player 玩家  
     * @param translationKey 翻译键
     * @param messageType 消息类型
     */
    public static void sendTranslatableMessage(ServerPlayer player, String translationKey, MessageType messageType) {
        Component message = Component.translatable(translationKey).withStyle(style ->
            style.withColor(messageType.getColor()));
        player.sendSystemMessage(message);
    }


}
