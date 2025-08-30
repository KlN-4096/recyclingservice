package com.klnon.recyclingservice.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;

import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.ui.TrashBoxUI;
import com.klnon.recyclingservice.util.core.ErrorHandler;
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
 * /bin open <dimension> <box_number> - 打开指定维度的垃圾箱（支持Tab补全）
 * /bin cleanup - 手动触发清理
 * /bin tickets <x> <z> - 查看指定区块坐标的所有tickets
 */
public class BinCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bin")
                .requires(CommandSourceStack::isPlayer) // 只要求是玩家即可
                .then(Commands.literal("open")
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests(BinCommand::suggestDimensions)
                                .then(Commands.argument("box_number", IntegerArgumentType.integer(1, 5))
                                        .suggests(BinCommand::suggestBoxNumbers)
                                        .executes(BinCommand::openSpecificTrashBox))))
                .then(Commands.literal("cleanup")
                        .executes(BinCommand::manualCleanup))
                .then(Commands.literal("tickets")
                        .requires(source -> source.hasPermission(2)) // 仅管理员可用
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(BinCommand::showChunkTickets))))
                .then(Commands.literal("test")
                        .requires(source -> source.hasPermission(2)) // 仅管理员可用
                        .executes(BinCommand::openTestTrashBox))
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
                ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
                int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
                // 获取垃圾箱管理器
                var trashManager = CleanupService.getTrashManager();
                // 打开垃圾箱
                return TrashBoxUI.openTrashBox(player, dimensionId, boxNumber, trashManager);
            });
    }
    
    /**
     * 智能补全维度ID
     * 只显示服务器实际已加载的维度
     * @param context 命令上下文
     * @param builder 补全建议构建器
     * @return CompletableFuture包装的补全建议
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> context, 
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            return ErrorHandler.handleOperation(null, "suggestDimensions", () -> {
                try {
                    // 获取服务器所有已加载的维度
                    MinecraftServer server = context.getSource().getServer();
                    List<String> dimensionIds = server.levelKeys().stream()
                        .map(ResourceKey::location)
                        .map(ResourceLocation::toString)
                        .toList();
                    
                    return SharedSuggestionProvider.suggest(dimensionIds, builder).join();
                    
                } catch (Exception e) {
                    // 如果无法获取服务器信息，fallback到常用维度
                    List<String> fallbackDimensions = List.of(
                        "minecraft:overworld", 
                        "minecraft:the_nether", 
                        "minecraft:the_end"
                    );
                    return SharedSuggestionProvider.suggest(fallbackDimensions, builder).join();
                }
            }, SharedSuggestionProvider.suggest(List.of("minecraft:overworld"), builder).join());
        });
    }
    
    /**
     * 智能补全垃圾箱编号
     * 根据指定维度现有的垃圾箱情况，动态提供编号建议
     * @param context 命令上下文
     * @param builder 补全建议构建器
     * @return CompletableFuture包装的补全建议
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBoxNumbers(
            CommandContext<CommandSourceStack> context, 
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            return ErrorHandler.handleOperation(null, "suggestBoxNumbers", () -> {
                try {
                    // 尝试获取维度ID
                    ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
                    var trashManager = CleanupService.getTrashManager();
                    List<com.klnon.recyclingservice.core.TrashBox> existingBoxes = trashManager.getDimensionTrashBoxes(dimensionId);
                    
                    List<String> suggestions = new ArrayList<>();
                    
                    // 只添加已存在的垃圾箱编号
                    for (int i = 1; i <= existingBoxes.size(); i++) {
                        suggestions.add(String.valueOf(i));
                    }
                    
                    // 如果没有任何垃圾箱，显示1号（防止空白补全）
                    if (suggestions.isEmpty()) {
                        suggestions.add("1");
                    }
                    
                    return SharedSuggestionProvider.suggest(suggestions, builder).join();
                    
                } catch (Exception e) {
                    // 如果无法获取维度信息，fallback到静态补全
                    List<String> fallbackSuggestions = List.of("1", "2", "3", "4", "5");
                    return SharedSuggestionProvider.suggest(fallbackSuggestions, builder).join();
                }
            }, SharedSuggestionProvider.suggest(List.of("1"), builder).join());
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
                if (player != null) {
                    AutoCleanupEvent.manualClean(player.getServer());
                }

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
                index++, ticket.getType(), ticket.getTicketLevel());
            source.sendSuccess(() -> Component.literal(ticketInfo), false);
        }
        
        return 1;
    }
}