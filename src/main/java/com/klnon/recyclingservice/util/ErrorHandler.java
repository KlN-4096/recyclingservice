package com.klnon.recyclingservice.util;

import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import org.slf4j.Logger;
import java.util.function.Supplier;

/**
 * 错误处理工具类 - 提供统一的错误处理和日志记录
 * 职责单一：只负责错误捕获、日志记录和默认值返回
 */
public class ErrorHandler {
    
    private static final Logger LOGGER = Recyclingservice.LOGGER;
    
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
            if (Config.ENABLE_DEBUG_LOGS.get()) {
                LOGGER.debug("Operation {} succeeded for {}", operationName, playerName);
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for {}: {}", operationName, playerName, e.getMessage());
            if (player != null && Config.ENABLE_DEBUG_LOGS.get()) {
                // 只在调试模式下向玩家发送错误信息
                MessageUtils.sendErrorMessage(player, "operation.error.general");
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
     * 静默处理操作 - 不向玩家发送任何消息
     * @param operationName 操作名称
     * @param operation 要执行的操作
     * @param defaultValue 默认值
     * @return 操作结果或默认值
     */
    public static <T> T handleSilently(String operationName, Supplier<T> operation, T defaultValue) {
        return handleOperation(null, operationName, operation, defaultValue);
    }
}