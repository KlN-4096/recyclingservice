package com.klnon.recyclingservice.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
     * 发送错误消息
     */
    public static void sendErrorMessage(ServerPlayer player, String translationKey) {
        Component message = Component.translatable(translationKey).withStyle(style ->
            style.withColor(0xFF5555));
        player.sendSystemMessage(message);
    }
}
