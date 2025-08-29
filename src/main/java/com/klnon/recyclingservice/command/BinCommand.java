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
import net.minecraft.server.level.Ticket;

import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.ui.TrashBoxUI;
import com.klnon.recyclingservice.util.other.ErrorHandler;
import com.klnon.recyclingservice.event.AutoCleanupEvent;
import com.klnon.recyclingservice.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.SortedArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * 垃圾箱和清理命令 - /bin
 * 用法：
 * /bin test - 打开测试垃圾箱
 * /bin open <dimension> <box_number> - 打开指定维度的垃圾箱
 * /bin current <box_number> - 打开当前维度的垃圾箱
 * /bin cleanup - 手动触发清理
 * /bin tickets <x> <z> - 查看指定区块坐标的所有tickets
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
            .then(Commands.literal("cleanup")
                .executes(BinCommand::manualCleanup))
            .then(Commands.literal("tickets")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(BinCommand::showChunkTickets))))
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
    
    /**
     * 手动触发清理命令
     */
    private static int manualCleanup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        return ErrorHandler.handleCommandOperation(source, player, "手动清理",
            () -> {
                source.sendSuccess(() -> Component.literal("§6[Manual Cleanup] Starting cleanup..."), true);
                
                // 触发手动清理
                AutoCleanupEvent.manualClean(player.getServer());
                
                return true;
            });
    }
    
    /**
     * 显示区块tickets信息 - 硬编码实现
     */
    private static int showChunkTickets(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        int x = IntegerArgumentType.getInteger(context, "x")/16;
        int z = IntegerArgumentType.getInteger(context, "z")/16;
        
        ServerLevel level = player.serverLevel();
        
        // 直接访问distanceManager
        DistanceManager distanceManager = level.getChunkSource().distanceManager;
        Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
        long chunkKey = ChunkPos.asLong(x, z);
        
        source.sendSuccess(() -> Component.literal("§6=== Chunk (" + x + ", " + z + ") Tickets ==="), false);
        
        SortedArraySet<Ticket<?>> chunkTickets = tickets.get(chunkKey);
        if (chunkTickets == null || chunkTickets.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No tickets found for this chunk"), false);
            return 1;
        }
        
        source.sendSuccess(() -> Component.literal("§aTotal tickets: " + chunkTickets.size()), false);
        
        int index = 1;
        for (Ticket<?> ticket : chunkTickets) {
            String ticketInfo = String.format("§e[%d] §f%s §7(Level: %d)", 
                index++, ticket.getType().toString(), ticket.getTicketLevel());
            source.sendSuccess(() -> Component.literal(ticketInfo), false);
        }
        
        return 1;
    }
}