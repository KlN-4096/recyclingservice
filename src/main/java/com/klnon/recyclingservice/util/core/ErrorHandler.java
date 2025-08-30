package com.klnon.recyclingservice.util.core;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class ErrorHandler {
    private static final Logger LOGGER = Recyclingservice.LOGGER;

    /**
     * 核心错误处理方法 - 统一的异常处理逻辑
     */
    private static <T> T executeWithErrorHandling(String operationName, ServerPlayer player, Supplier<T> operation, 
                                                  T successResult, T failureResult, 
                                                  Runnable onSuccess, Runnable onFailure) {
        String playerName = player != null ? player.getName().getString() : "System";
        
        try {
            T result = operation.get();
            // 使用三元运算符优化DEBUG日志
            Runnable debugLogger = Config.isDebugLogsEnabled() ? 
                () -> LOGGER.debug("Operation {} succeeded for {}", operationName, playerName) : null;
            if (debugLogger != null) debugLogger.run();
            
            if (onSuccess != null) onSuccess.run();
            return result != null ? result : successResult;
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for {}: {}", operationName, playerName, e.getMessage());
            if (player != null) {
                MessageSender.sendTranslatableMessage(player, "operation.error.general", MessageSender.MessageType.ERROR);
            }
            if (onFailure != null) onFailure.run();
            return failureResult;
        }
    }

    /**
     * 通用错误处理方法 - 支持泛型返回值
     */
    public static <T> T handleOperation(ServerPlayer player, String operationName, Supplier<T> operation, T defaultValue) {
        return executeWithErrorHandling(operationName, player, operation, null, defaultValue, null, null);
    }

    /**
     * 命令操作错误处理 - 专门用于命令执行
     */
    public static int handleCommandOperation(CommandSourceStack source, ServerPlayer player, String operationName, Supplier<Boolean> operation) {
        return executeWithErrorHandling(
            operationName, player, 
            () -> operation.get() ? 1 : 0,null, 0,null,null);
    }

    /**
     * 处理void操作（不需要返回值）
     */
    public static void handleVoidOperation(String operationName, Runnable operation) {
        executeWithErrorHandling(
            operationName, null,
            () -> { operation.run(); return null; },
            null, null,
            Config.isDebugLogsEnabled() ? 
                () -> LOGGER.debug("Void operation {} completed successfully", operationName) : null,
            null
        );
    }
  }
