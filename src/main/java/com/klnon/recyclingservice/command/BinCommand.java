package com.klnon.recyclingservice.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.ui.TrashBoxUI;
import com.klnon.recyclingservice.util.other.ErrorHandler;
import com.klnon.recyclingservice.Config;

/**
 * 垃圾箱测试命令 - /bin
 * 用法：
 * /bin test - 打开测试垃圾箱
 * /bin open <dimension> <box_number> - 打开指定维度的垃圾箱
 * /bin current <box_number> - 打开当前维度的垃圾箱
 * 简化版命令，专注于UI测试
 */
public class BinCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bin")
            .requires(CommandSourceStack::isPlayer)
            .requires(source -> source.hasPermission(2)) // 需要管理员权限
            .then(Commands.literal("test")
                .executes(BinCommand::openTestTrashBox))
            .then(Commands.literal("open")
                .then(Commands.argument("dimension", StringArgumentType.string())
                    .then(Commands.argument("box_number", IntegerArgumentType.integer(1, 5))
                        .executes(BinCommand::openSpecificTrashBox))))
            .then(Commands.literal("current")
                .then(Commands.argument("box_number", IntegerArgumentType.integer(1, 5))
                    .executes(BinCommand::openCurrentDimensionTrashBox)))
            .executes(BinCommand::showHelp));
    }
    
    /**
     * 显示命令帮助
     */
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String[] helpMessages = Config.getCommandHelpMessages();
        for (String message : helpMessages) {
            source.sendSuccess(() -> Component.literal(message), false);
        }
        
        return 1;
    }
    
    /**
     * 打开测试垃圾箱
     */
    private static int openTestTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        // require中isPlayer()已经检测过,确保是玩家
        ServerPlayer player = (ServerPlayer) source.getEntity();
        return ErrorHandler.handleCommandOperation(source, player, "打开测试垃圾箱",
          () -> TrashBoxUI.openTestTrashBox(player));
    }
    
    /**
     * 打开指定维度的垃圾箱
     */
    private static int openSpecificTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        return ErrorHandler.handleCommandOperation(source, player, "打开指定维度垃圾箱",
            () -> {
                String dimensionString = StringArgumentType.getString(context, "dimension");
                int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
                // 解析维度ID
                ResourceLocation dimensionId = ResourceLocation.parse(dimensionString);
                // 获取垃圾箱管理器
                var trashManager = CleanupService.getTrashManager();
                // 打开垃圾箱
                return TrashBoxUI.openTrashBox(player, dimensionId, boxNumber, trashManager);
            });
    }
    /**
     * 打开当前维度的垃圾箱
     */
    private static int openCurrentDimensionTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (player==null)
            return 0;
        
        return ErrorHandler.handleCommandOperation(source, player, "打开当前维度垃圾箱",
            () -> {
                int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
                // 获取垃圾箱管理器
                var trashManager = CleanupService.getTrashManager();
                // 打开当前维度的垃圾箱
                ResourceLocation dimensionId = player.level().dimension().location();
                return TrashBoxUI.openTrashBox(player, dimensionId, boxNumber, trashManager);
            });
    }
}