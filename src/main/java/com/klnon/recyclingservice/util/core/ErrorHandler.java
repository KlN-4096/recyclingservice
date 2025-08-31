package com.klnon.recyclingservice.util.core;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

import net.minecraft.server.level.ServerPlayer;

public class ErrorHandler {
    private static final Logger LOGGER = Recyclingservice.LOGGER;

    /**
     * 简化的通用错误处理方法
     */
    public static <T> T handleOperation(ServerPlayer player, String operationName, Supplier<T> operation, T defaultValue) {
        String playerName = player != null ? player.getName().getString() : "System";
        
        try {
            T result = operation.get();
            if (Config.ENABLE_DEBUG_LOGS.get()) {
                LOGGER.debug("Operation {} succeeded for {}", operationName, playerName);
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for {}: {}", operationName, playerName, e.getMessage());
            if (player != null) {
                MessageSender.sendTranslatableMessage(player, "operation.error.general", MessageSender.MessageType.ERROR);
            }
            return defaultValue;
        }
    }

    /**
     * 命令操作错误处理 - 返回int用于命令结果
     */
    public static int handleCommandOperation(ServerPlayer player, String operationName, Supplier<Boolean> operation) {
        Boolean result = handleOperation(player, operationName, operation, false);
        return result ? 1 : 0;
    }

    /**
     * 处理void操作（不需要返回值）
     */
    public static void handleVoidOperation(String operationName, Runnable operation) {
        handleOperation(null, operationName, () -> {
            operation.run();
            return null;
        }, null);
    }
}
