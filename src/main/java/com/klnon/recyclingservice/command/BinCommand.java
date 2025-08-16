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

/**
 * 垃圾箱测试命令 - /bin
 * 
 * 用法：
 * /bin test - 打开测试垃圾箱
 * /bin open <dimension> <box_number> - 打开指定维度的垃圾箱
 * /bin current <box_number> - 打开当前维度的垃圾箱
 * 
 * 简化版命令，专注于UI测试
 */
public class BinCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bin")
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
        
        source.sendSuccess(() -> Component.literal("§6=== 垃圾箱命令帮助 ==="), false);
        source.sendSuccess(() -> Component.literal("§e/bin test §7- 打开测试垃圾箱"), false);
        source.sendSuccess(() -> Component.literal("§e/bin open <维度> <箱号> §7- 打开指定维度的垃圾箱"), false);
        source.sendSuccess(() -> Component.literal("§e/bin current <箱号> §7- 打开当前维度的垃圾箱"), false);
        source.sendSuccess(() -> Component.literal("§7示例: §f/bin open minecraft:overworld 1"), false);
        
        return 1;
    }
    
    /**
     * 打开测试垃圾箱
     */
    private static int openTestTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
            return 0;
        }
        
        try {
            boolean success = TrashBoxUI.openTestTrashBox(player);
            
            if (success) {
                String uiType = TrashBoxUI.getUIType(player);
                source.sendSuccess(() -> Component.literal(
                    "§a已打开测试垃圾箱 §7| UI类型: §b" + uiType), false);
                return 1;
            } else {
                source.sendFailure(Component.literal("§c无法打开测试垃圾箱"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 打开指定维度的垃圾箱
     */
    private static int openSpecificTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
            return 0;
        }
        
        try {
            String dimensionString = StringArgumentType.getString(context, "dimension");
            int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
            
            // 解析维度ID
            ResourceLocation dimensionId;
            try {
                dimensionId = ResourceLocation.parse(dimensionString);
            } catch (Exception e) {
                source.sendFailure(Component.literal("§c无效的维度ID: " + dimensionString));
                return 0;
            }
            
            // 获取垃圾箱管理器
            var trashManager = CleanupService.getTrashManager();
            
            // 打开垃圾箱
            boolean success = TrashBoxUI.openTrashBox(player, dimensionId, boxNumber, trashManager);
            
            if (success) {
                String uiType = TrashBoxUI.getUIType(player);
                source.sendSuccess(() -> Component.literal(
                    String.format("§a已打开垃圾箱 §f%s #%d §7| UI类型: §b%s", 
                        dimensionId, boxNumber, uiType)), false);
                return 1;
            } else {
                source.sendFailure(Component.literal("§c无法打开指定的垃圾箱"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 打开当前维度的垃圾箱
     */
    private static int openCurrentDimensionTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
            return 0;
        }
        
        try {
            int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
            
            // 获取垃圾箱管理器
            var trashManager = CleanupService.getTrashManager();
            
            // 打开当前维度的垃圾箱
            boolean success = TrashBoxUI.openCurrentDimensionTrashBox(player, boxNumber, trashManager);
            
            if (success) {
                ResourceLocation currentDimension = player.level().dimension().location();
                String uiType = TrashBoxUI.getUIType(player);
                source.sendSuccess(() -> Component.literal(
                    String.format("§a已打开当前维度垃圾箱 §f%s #%d §7| UI类型: §b%s", 
                        currentDimension, boxNumber, uiType)), false);
                return 1;
            } else {
                source.sendFailure(Component.literal("§c无法打开当前维度的垃圾箱"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("§c命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
}