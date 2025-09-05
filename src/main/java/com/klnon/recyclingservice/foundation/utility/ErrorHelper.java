package com.klnon.recyclingservice.foundation.utility;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import org.slf4j.Logger;
import java.util.function.Supplier;

/**
 * 错误处理工具类 - 提供统一的错误处理和日志记录
 * 职责单一：只负责错误捕获、日志记录和默认值返回
 * 简化版本：移除对其他Helper的依赖，避免循环引用
 */
public class ErrorHelper {
    
    private static final Logger LOGGER = Recyclingservice.LOGGER;
    private static final int ERROR_MESSAGE_COLOR = 0xFF5555; // 红色
    
    /**
     * 通用错误处理方法
     * @param player 玩家（可为null）
     * @param operationName 操作名称
     * @param operation 要执行的操作
     * @param defaultValue 出错时的默认返回值
     * @return 操作结果或默认值
     */
    public static <T> T handleOperation(ServerPlayer player, String operationName, 
                                       Supplier<T> operation, T defaultValue) {
        String playerName = player != null ? player.getName().getString() : "System";
        
        try {
            T result = operation.get();
            if (Config.TECHNICAL.enableDebugLogs.get()) {
                LOGGER.debug("Operation {} succeeded for {}", operationName, playerName);
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for {}: {}", operationName, playerName, e.getMessage());
            if (player != null && Config.TECHNICAL.enableDebugLogs.get()) {
                // 直接发送错误消息，避免对MessageHelper的依赖
                sendSimpleErrorMessage(player, "Operation failed: " + operationName);
            }
            return defaultValue;
        }
    }

    /**
     * 命令操作错误处理 - 专门用于命令执行
     * @param player 执行命令的玩家
     * @param operationName 操作名称
     * @param operation 要执行的操作
     * @return 命令执行结果（1=成功，0=失败）
     */
    public static int handleCommandOperation(ServerPlayer player, String operationName, 
                                            Supplier<Boolean> operation) {
        Boolean result = handleOperation(player, operationName, operation, false);
        return result ? 1 : 0;
    }

    /**
     * 发送简单错误消息
     * @param player 玩家
     * @param message 错误消息
     */
    private static void sendSimpleErrorMessage(ServerPlayer player, String message) {
        try {
            Component component = Component.literal(message)
                .withStyle(style -> style.withColor(ERROR_MESSAGE_COLOR));
            player.sendSystemMessage(component);
        } catch (Exception e) {
            // 发送消息失败也不要抛出异常，只记录日志
            LOGGER.debug("Failed to send error message to player {}: {}", 
                player.getName().getString(), e.getMessage());
        }
    }
}