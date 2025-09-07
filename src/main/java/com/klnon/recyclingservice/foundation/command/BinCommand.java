package com.klnon.recyclingservice.foundation.command;

import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.content.trashbox.core.TrashBox;
import com.klnon.recyclingservice.content.trashbox.TrashBoxMenu;
import com.klnon.recyclingservice.content.chunk.ChunkCache;
import com.klnon.recyclingservice.foundation.events.AutoCleanupEvent;
import com.klnon.recyclingservice.foundation.utility.ErrorHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;

import com.klnon.recyclingservice.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.SortedArraySet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * 垃圾箱命令处理器 - /bin
 */
public class BinCommand {
    
    // 管理员权限检查谓词
    private static final java.util.function.Predicate<CommandSourceStack> ADMIN_PERMISSION = 
        source -> source.hasPermission(2);

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
                        .requires(ADMIN_PERMISSION) // 使用常量
                        .executes(BinCommand::manualCleanup))
                .then(Commands.literal("tickets")
                        .requires(ADMIN_PERMISSION) // 使用常量
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(BinCommand::showChunkTickets))))
                .then(Commands.literal("chunks")
                        .requires(ADMIN_PERMISSION)
                        .executes(BinCommand::listChunks)
                        .then(Commands.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests(BinCommand::suggestChunkStates)
                                .executes(BinCommand::listChunks)
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(BinCommand::listChunks))))
                .then(Commands.literal("reload")
                        .requires(ADMIN_PERMISSION)
                        .executes(BinCommand::reloadConfig))
                .executes(BinCommand::showHelp));
    }
    
    /**
     * 显示命令帮助
     */
    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        String[] helpMessages = Config.MESSAGE.cmdHelpMessages.get().toArray(new String[0]);
        for (String message : helpMessages) {
            source.sendSuccess(() -> Component.literal(message), false);
        }
        
        // 添加新命令帮助（管理员权限检查）
        if (source.hasPermission(2)) {
            source.sendSuccess(() -> Component.literal("§e/bin tickets <x> <z> §7- Show chunk tickets info"), false);
            source.sendSuccess(() -> Component.literal("§e/bin chunks [state] [page] §7- List managed chunks by state"), false);
            source.sendSuccess(() -> Component.literal("§e/bin takeover §7- Manually takeover unmanaged chunks"), false);
            source.sendSuccess(() -> Component.literal("§e/bin reload §7- Reload configuration"), false);
        }
        
        return 1;
    }
    
    /**
     * 打开指定维度的垃圾箱
     */
    private static int openSpecificTrashBox(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        return ErrorHelper.handleCommandOperation(player, "打开指定维度垃圾箱",
            () -> {
                ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
                int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
                // 打开垃圾箱
                return TrashBoxMenu.openTrashBox(player, dimensionId, boxNumber);
            });
    }
    
    /**
     * 手动触发清理命令
     */
    private static int manualCleanup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        return ErrorHelper.handleCommandOperation(player, "手动清理",
            () -> {
                source.sendSuccess(() -> Component.literal(Config.MESSAGE.manualCleanupStart.get()), true);
                
                // 触发手动清理
                if (player != null) {
                    AutoCleanupEvent.manualClean(player.getServer());
                }

                return true;
            });
    }
    
    /**
     * 显示区块tickets信息 - 硬编码
     */
    private static int showChunkTickets(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            // 检查实体是否存在且为玩家
            Entity entity = source.getEntity();
            if (!(entity instanceof ServerPlayer player)) {
                source.sendFailure(Component.literal("§cThis command can only be executed by a player"));
                return 0; // 返回0表示命令执行失败
            }

            ServerLevel level = player.serverLevel();

            // 检查level是否为null

            int x = IntegerArgumentType.getInteger(context, "x") / 16;
            int z = IntegerArgumentType.getInteger(context, "z") / 16;

            // 直接访问distanceManager
            DistanceManager distanceManager = level.getChunkSource().distanceManager;
            Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = distanceManager.tickets;
            long chunkKey = ChunkPos.asLong(x, z);

            source.sendSuccess(() -> Component.literal("§6=== Chunk (" + x + ", " + z + ") Tickets ==="), false);

            SortedArraySet<Ticket<?>> chunkTickets = tickets.get(chunkKey);
            if (chunkTickets == null || chunkTickets.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§7No tickets found for this chunk"), false);
                return 1; // 成功执行但没有找到tickets
            }

            source.sendSuccess(() -> Component.literal("§aTotal tickets: " + chunkTickets.size()), false);

            int index = 1;
            for (Ticket<?> ticket : chunkTickets) {
                final int currentIndex = index++; // 为lambda表达式创建final变量
                String ticketInfo = String.format("§e[%d] §f%s §7(Level: %d)",
                        currentIndex, ticket.getType(), ticket.getTicketLevel());
                source.sendSuccess(() -> Component.literal(ticketInfo), false);
            }

            return 1; // 成功执行并找到了tickets

        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("§cInvalid arguments: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cAn error occurred while retrieving chunk tickets: " + e.getMessage()));
            return 0;
        }
    }
    
    // === 新增命令实现 ===
    
    /**
     * 列出区块状态
     */
    private static int listChunks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        return ErrorHelper.handleCommandOperation(player, "列出区块状态", () -> {
            // 获取参数，带默认值
            String stateFilter = "ALL";
            int page = 1;
            
            try {
                stateFilter = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "state");
            } catch (Exception ignored) {}
            
            try {
                page = IntegerArgumentType.getInteger(context, "page");
            } catch (Exception ignored) {}
            
            MinecraftServer server = source.getServer();
            List<Component> allChunks = new ArrayList<>();
            
            // 收集所有区块信息
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();
                
                // 如果指定了状态过滤，获取指定状态的区块
                if (!"ALL".equals(stateFilter)) {
                    try {
                        int filterState = CleanupManager.getStateByName(stateFilter);
                        List<ChunkCache.ChunkInfo> chunks = ChunkCache.getChunksByState(dimension, filterState);
                        for (ChunkCache.ChunkInfo chunkInfo : chunks) {
                            Component chunkInfoComponent = formatChunkInfo(chunkInfo);
                            allChunks.add(chunkInfoComponent);
                        }
                    } catch (IllegalArgumentException e) {
                        source.sendFailure(Component.literal("§cInvalid state: " + stateFilter));
                        return false;
                    }
                } else {
                    // 获取所有状态的区块
                    int[] states = {ChunkCache.ChunkInfo.UNIMPORTANT,ChunkCache.ChunkInfo.MANAGED,
                                    ChunkCache.ChunkInfo.ITEM_FROZEN, ChunkCache.ChunkInfo.PERFORMANCE_FROZEN};
                    for (int state : states) {
                        List<ChunkCache.ChunkInfo> chunks = ChunkCache.getChunksByState(dimension, state);
                        for (ChunkCache.ChunkInfo chunkInfo : chunks) {
                            Component chunkInfoComponent = formatChunkInfo(chunkInfo);
                            allChunks.add(chunkInfoComponent);
                        }
                    }
                }
            }
            
            // 分页显示
            int pageSize = 10;
            int totalPages = (allChunks.size() + pageSize - 1) / pageSize;
            if (page > totalPages) page = totalPages;
            if (page < 1) page = 1;
            
            // 显示头部信息
            String finalStateFilter = stateFilter;
            int finalPage = page;
            source.sendSuccess(() -> Component.literal(
                String.format("§6=== Chunks (%s) - Page %d/%d (%d total) ===",
                        finalStateFilter, finalPage, totalPages, allChunks.size())), false);
            
            // 显示当前页的区块
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, allChunks.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                final Component chunkInfo = allChunks.get(i);
                source.sendSuccess(() -> chunkInfo, false);
            }
            
            return true;
        });
    }
    
    /**
     * 格式化区块信息（带点击传送功能）
     */
    private static Component formatChunkInfo(ChunkCache.ChunkInfo chunkInfo) {
        try {
            // 获取区块状态名称
            String stateName = CleanupManager.getStateName(chunkInfo);
            
            // 简化维度名显示
            String dimName = chunkInfo.dimension().getPath();
            
            // 计算世界坐标（区块中心）
            int worldX = chunkInfo.pos().x * 16 + 8;
            int worldZ = chunkInfo.pos().z * 16 + 8;
            
            // 创建基础信息文本（包含方块实体数量）
            MutableComponent baseInfo = Component.literal(
                String.format("§f%s §7(%d,%d) §e%s §6BlockEntities:%d ",
                    dimName, chunkInfo.pos().x, chunkInfo.pos().z, stateName, chunkInfo.blockEntityCount()));
            
            // 创建可点击的传送按钮
            MutableComponent teleportButton = Component.literal("§a[TP]")
                .withStyle(style -> style
                    .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        "/tp @s " + worldX + " ~ " + worldZ))
                    .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§7Click to teleport to chunk center\n" +
                                        "§7World coordinate: " + worldX + ", " + worldZ + "\n" +
                                        "§7Chunk coordinate: " + chunkInfo.pos().x + ", " + chunkInfo.pos().z + "\n" +
                                        "§7Block Entities: " + chunkInfo.blockEntityCount())))
                );
            
            // 组合返回
            return baseInfo.append(teleportButton);
            
        } catch (Exception e) {
            return Component.literal(String.format("§f%s §7(%d,%d) §cERROR", 
                chunkInfo.dimension().getPath(), chunkInfo.pos().x, chunkInfo.pos().z));
        }
    }
    

    
    /**
     * 重载配置命令
     */
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = (ServerPlayer) source.getEntity();
        
        return ErrorHelper.handleCommandOperation(player, "重载配置", () -> {
            // 重新加载配置缓存
            Config.updateCaches();
            
            source.sendSuccess(() -> Component.literal("§a[Config Reload] Configuration reloaded successfully"), false);
            return true;
        });
    }
    
    /**
     * 智能补全维度ID
     * 只显示服务器实际已加载的维度
     * @param context 命令上下文
     * @param builder 补全建议构建器
     * @return CompletableFuture包装的补全建议
     */
    private static java.util.concurrent.CompletableFuture<Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> context, 
            SuggestionsBuilder builder) {
        
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> ErrorHelper.handleOperation(null, "suggestDimensions", () -> {
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
        }, SharedSuggestionProvider.suggest(List.of("minecraft:overworld"), builder).join()));
    }
    
    /**
     * 智能补全垃圾箱编号
     * 根据指定维度现有的垃圾箱情况，动态提供编号建议
     * @param context 命令上下文
     * @param builder 补全建议构建器
     * @return CompletableFuture包装的补全建议
     */
    private static java.util.concurrent.CompletableFuture<Suggestions> suggestBoxNumbers(
            CommandContext<CommandSourceStack> context, 
            SuggestionsBuilder builder) {
        
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> ErrorHelper.handleOperation(null, "suggestBoxNumbers", () -> {
            try {
                // 尝试获取维度ID
                ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
                List<TrashBox> existingBoxes = TrashBoxManager.getDimensionTrashBoxes(dimensionId);

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
        }, SharedSuggestionProvider.suggest(List.of("1"), builder).join()));
    }
    

    /**
     * 区块状态补全
     */
    private static java.util.concurrent.CompletableFuture<Suggestions> suggestChunkStates(
            CommandContext<CommandSourceStack> context, 
            SuggestionsBuilder builder) {
        
        List<String> states = List.of("ALL", "UNIMPORTANT", "MANAGED", "ITEM_FROZEN", "PERFORMANCE_FROZEN");
        return SharedSuggestionProvider.suggest(states, builder);
    }
}