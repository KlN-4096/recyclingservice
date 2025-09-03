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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;

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
     * 发送目标枚举
     */
    public enum Target {
        ACTION_BAR,    // 发送到ActionBar
        CHAT          // 发送到聊天框
    }
    
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
     * 获取格式化的物品过多警告消息（支持点击传送）
     */
    public static Component getItemWarningMessage(int itemCount, int worldX, int worldZ, int ticketLevel) {
        String message = formatTemplate(Config.MESSAGE.tooManyItemsWarningMessage.get(), Map.of(
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
                        Component.literal("§7Click to teleport (OP required)\n" +
                                        "§7Coordinate: " + worldX + ", " + worldZ + "\n" +
                                        "§7Ticket Level: " + ticketLevel)))
                );
    }

    /**
     * 构建详细清理完成消息
     */
    public static Component getDetailedCleanupMessage(Map<ResourceLocation, ?> dimensionStats) {
        MutableComponent mainComponent = Component.literal(Config.MESSAGE.cleanupResultHeader.get());
        
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
     */
    private static Component formatDimensionEntry(ResourceLocation dimensionId, Object stats) {
        // 使用instanceof模式匹配检查类型
        if (!(stats instanceof CleanupManager.DimensionCleanupStats dimensionStats)) {
            return null;
        }
        
        // 创建基础文本
        String baseText = formatTemplate(Config.MESSAGE.dimensionEntryFormat.get(), Map.of(
            "name", getDimensionDisplayName(dimensionId),
            "items", String.valueOf(dimensionStats.itemsCleaned()),
            "entities", String.valueOf(dimensionStats.projectilesCleaned())
        ));
        
        // 创建可点击的按钮
        String buttonText = formatTemplate(Config.MESSAGE.trashBoxButtonText.get(), 
            Map.of("name", getDimensionDisplayName(dimensionId)));
        String hoverText = formatTemplate(Config.MESSAGE.trashBoxButtonHover.get(), 
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
     * 获取维度的显示名称
     */
    private static String getDimensionDisplayName(ResourceLocation dimensionId) {
        String dimString = dimensionId.toString();
        return dimString.contains(":") ? 
            dimString.substring(dimString.indexOf(':') + 1) : dimString;
    }
    
    // === 消息发送功能 ===
    
    /**
     * 统一消息发送方法 - 发送给所有玩家
     */
    public static void sendToAll(MinecraftServer server, String message, 
                                MessageType messageType, Target target) {
        Component component = Component.literal(message).withStyle(style -> 
            style.withColor(messageType.getColor())
                 .withBold(target == Target.ACTION_BAR));
        
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
     * 发送Component消息给所有玩家
     */
    public static void sendChatMessage(MinecraftServer server, Component component) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(component);
        }
    }

    /**
     * 发送错误消息给单个玩家
     */
    public static void sendErrorMessage(Player player, String translationKey) {
        Component message = Component.translatable(translationKey)
            .withStyle(style -> style.withColor(MessageType.ERROR.getColor()));
        player.sendSystemMessage(message);
    }
    
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