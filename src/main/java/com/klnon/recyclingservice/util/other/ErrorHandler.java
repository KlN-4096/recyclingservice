package com.klnon.recyclingservice.util.other;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.klnon.recyclingservice.Recyclingservice;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ErrorHandler {
    private static final Logger LOGGER = Recyclingservice.LOGGER;

    /**
     * 通用错误处理方法 - 支持泛型返回值
     * @param player 玩家（可选，为null时不发送玩家消息）
     * @param operationName 操作名称
     * @param operation 要执行的操作
     * @param defaultValue 默认返回值（失败时）
     * @param <T> 返回类型
     * @return 操作结果或默认值
     */
    public static <T> T handleOperation(ServerPlayer player, String operationName, Supplier<T> operation, T defaultValue) {
        try {
            T result = operation.get();
            String playerName = player != null ? player.getName().getString() : "System";
            LOGGER.debug("Operation {} succeeded for {}", operationName, playerName);
            return result;
        } catch (Exception e) {
            String playerName = player != null ? player.getName().getString() : "System";
            LOGGER.error("Operation {} failed for {}: {}", operationName, playerName, e.getMessage());
            if (player != null) {
                MessageSender.sendTranslatableMessage(player, "operation.error.general", MessageSender.MessageType.ERROR);
            }
            return defaultValue;
        }
    }

    /**
     * 命令操作错误处理 - 专门用于命令执行
     */
    public static int handleCommandOperation(CommandSourceStack source, ServerPlayer player, String operationName, Supplier<Boolean> operation) {
        try {
            boolean result = operation.get();
            if (result) {
                LOGGER.debug("Operation {} succeeded for player {}", operationName, player.getName().getString());
                source.sendSuccess(() -> Component.literal("§aOperation " + operationName + " succeeded"), false);
                return 1;
            } else {
                LOGGER.debug("Operation {} failed for player {}", operationName, player.getName().getString());
                source.sendFailure(Component.literal("§cOperation " + operationName + " failed"));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("Operation {} failed for player {}: {}", operationName, player.getName().getString(), e.getMessage());
            source.sendFailure(Component.literal("§cOperation " + operationName + " failed: " + e.getMessage()));
            MessageSender.sendTranslatableMessage(player, "operation.error.general", MessageSender.MessageType.ERROR);
            return 0;
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



  }
