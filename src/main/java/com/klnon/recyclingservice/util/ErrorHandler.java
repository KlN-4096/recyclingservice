package com.klnon.recyclingservice.util;

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

  }
