package com.klnon.recyclingservice.util.other;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.klnon.recyclingservice.Recyclingservice;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ErrorHandler {
    private static final Logger LOGGER = Recyclingservice.LOGGER;

    //   通用的错误处理
    public static boolean handleOperation(ServerPlayer player, String operationName, Supplier<Boolean> operation) {
        try {
            boolean result = operation.get();
            if (result) {
                LOGGER.debug("Operation {} succeeded for player {}", operationName, player.getName().getString());
            } else {
                LOGGER.debug("Operation {} failed for player {}", operationName, player.getName().getString());
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for player {}: {}", operationName, player.getName().getString(), e.getMessage());
            MessageSender.sendErrorMessage(player, "operation.error.general");
            return false;
        }
    }

    //   命令错误处理
    public static int handleCommandOperation(CommandSourceStack source, ServerPlayer player, String operationName, Supplier<Boolean> operation) {
        try {
            boolean result = operation.get();
            if (result) {
                LOGGER.debug("Operation {} succeeded for player {}", operationName, player.getName().getString());
                source.sendSuccess(() -> Component.literal("§aOperation " + operationName + " succeeded"), false);
                return 1;  // 成功返回1
            } else {
                LOGGER.debug("Operation {} failed for player {}", operationName, player.getName().getString());
                source.sendFailure(Component.literal("§cOperation " + operationName + " failed"));
                return 0;  // 失败返回0
            }
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for player {}: {}", operationName, player.getName().getString(), e.getMessage());
            source.sendFailure(Component.literal("§cOperation " + operationName + " failed: " + e.getMessage()));
            MessageSender.sendErrorMessage(player, "operation.error.general");
            return 0;  // 异常返回0
        }
    }

    /**
     * 处理void操作（不需要返回值）
     */
    public static void handleVoidOperation(String operationName, Runnable operation) {
        try {
            operation.run();
            LOGGER.debug("Void operation {} completed successfully", operationName);
        } catch (Exception e) {
            LOGGER.error("Void operation {} failed: {}", operationName, e.getMessage());
        }
    }

    /**
     * 处理静态操作（无玩家上下文，支持泛型返回值）
     */
    public static <T> T handleStaticOperation(String operationName, Supplier<T> operation, T defaultValue) {
        try {
            T result = operation.get();
            LOGGER.debug("Static operation {} succeeded", operationName);
            return result;
        } catch (Exception e) {
            LOGGER.error("Static operation {} failed: {}", operationName, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 处理带默认值的泛型操作（有玩家上下文）
     */
    public static <T> T handleOperation(ServerPlayer player, String operationName, Supplier<T> operation, T defaultValue) {
        try {
            T result = operation.get();
            LOGGER.debug("Operation {} succeeded for player {}", operationName, player.getName().getString());
            return result;
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for player {}: {}", operationName, player.getName().getString(), e.getMessage());
            MessageSender.sendErrorMessage(player, "operation.error.general");
            return defaultValue;
        }
    }

  }
