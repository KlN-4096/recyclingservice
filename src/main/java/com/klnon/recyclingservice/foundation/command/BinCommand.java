package com.klnon.recyclingservice.foundation.command;

import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import com.klnon.recyclingservice.content.chunk.ChunkManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.content.trashbox.data.TrashBox;
import com.klnon.recyclingservice.content.trashbox.TrashBoxMenu;
import com.klnon.recyclingservice.foundation.events.AutoCleanupEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import com.klnon.recyclingservice.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 垃圾箱命令处理器 - /bin
 */
public class BinCommand {

    private static final java.util.function.Predicate<CommandSourceStack> ADMIN_PERMISSION =
            source -> source.hasPermission(2);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bin")
                .requires(CommandSourceStack::isPlayer)
                .then(Commands.literal("open")
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests(BinCommand::suggestDimensions)
                                .then(Commands.argument("box_number", IntegerArgumentType.integer(1, 5))
                                        .suggests(BinCommand::suggestBoxNumbers)
                                        .executes(BinCommand::openTrashBox))))
                .then(Commands.literal("cleanup")
                        .requires(ADMIN_PERMISSION)
                        .executes(BinCommand::manualCleanup))
                .then(Commands.literal("tickets")
                        .requires(ADMIN_PERMISSION)
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

    // === 命令执行方法 ===

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        Config.MESSAGE.cmdHelpMessages.get().forEach(message ->
                source.sendSuccess(() -> Component.literal(message), false));

        if (source.hasPermission(2)) {
            sendAdminHelp(source);
        }

        return 1;
    }

    private static void sendAdminHelp(CommandSourceStack source) {
        String[] adminCommands = {
                "§e/bin tickets <x> <z> §7- Show chunk tickets info",
                "§e/bin chunks [state] [page] §7- List managed chunks by state",
                "§e/bin reload §7- Reload configuration"
        };

        for (String cmd : adminCommands) {
            source.sendSuccess(() -> Component.literal(cmd), false);
        }
    }

    private static int openTrashBox(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayerFromContext(context);
        if (player == null) return 0;

        try {
            ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
            int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
            return TrashBoxMenu.openTrashBox(player, dimensionId, boxNumber) ? 1 : 0;
        } catch (Exception e) {
            sendError(context.getSource(), "Failed to open trash box: " + e.getMessage());
            return 0;
        }
    }

    private static int manualCleanup(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayerFromContext(context);
        if (player == null) return 0;

        context.getSource().sendSuccess(() -> Component.literal(Config.MESSAGE.manualCleanupStart.get()), true);
        AutoCleanupEvent.doCleanup(player.getServer());
        return 1;
    }

    private static int showChunkTickets(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayerFromContext(context);
        if (player == null) return 0;

        try {
            int x = IntegerArgumentType.getInteger(context, "x") / 16;
            int z = IntegerArgumentType.getInteger(context, "z") / 16;

            displayTicketInfo(context.getSource(), player.serverLevel(), x, z);
            return 1;
        } catch (Exception e) {
            sendError(context.getSource(), "Failed to show tickets: " + e.getMessage());
            return 0;
        }
    }

    private static void displayTicketInfo(CommandSourceStack source, ServerLevel level, int x, int z) {
        ChunkPos chunkPos = new ChunkPos(x, z);
        List<Ticket<?>> chunkTickets = ChunkManager.getChunkTickets(chunkPos, level);

        source.sendSuccess(() -> Component.literal("§6=== Chunk (" + x + ", " + z + ") Tickets ==="), false);

        if (chunkTickets.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No tickets found for this chunk"), false);
            return;
        }

        source.sendSuccess(() -> Component.literal("§aTotal tickets: " + chunkTickets.size()), false);

        int index = 1;
        for (Ticket<?> ticket : chunkTickets) {
            final int currentIndex = index++;
            String ticketInfo = String.format("§e[%d] §f%s §7(Level: %d)",
                    currentIndex, ticket.getType(), ticket.getTicketLevel());
            source.sendSuccess(() -> Component.literal(ticketInfo), false);
        }
    }

    private static int listChunks(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayerFromContext(context);
        if (player == null) return 0;

        try {
            String stateFilter = getStringArgument(context);
            int page = getIntArgument(context);

            List<Component> allChunks = collectChunkInfo(context.getSource().getServer(), stateFilter);
            displayChunkPage(context.getSource(), allChunks, stateFilter, page);
            return 1;
        } catch (Exception e) {
            sendError(context.getSource(), "Failed to list chunks: " + e.getMessage());
            return 0;
        }
    }

    private static List<Component> collectChunkInfo(MinecraftServer server, String stateFilter) {
        List<Component> allChunks = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimension = level.dimension().location();

            if ("ALL".equals(stateFilter)) {
                for (byte state : ChunkDataCache.getAllStates()) {
                    addChunksForState(allChunks, dimension, state);
                }
            } else {
                byte filterState = ChunkManager.getStateByName(stateFilter);
                addChunksForState(allChunks, dimension, filterState);
            }
        }

        return allChunks;
    }

    private static void addChunksForState(List<Component> allChunks, ResourceLocation dimension, byte state) {
        List<ChunkDataCache.ChunkInfo> chunks = ChunkDataCache.getChunksByState(dimension, state);
        chunks.stream()
                .map(BinCommand::formatChunkInfo)
                .forEach(allChunks::add);
    }

    private static void displayChunkPage(CommandSourceStack source, List<Component> allChunks,
                                         String stateFilter, int page) {
        int pageSize = 10;
        int totalPages = Math.max(1, (allChunks.size() + pageSize - 1) / pageSize);
        page = Math.max(1, Math.min(page, totalPages));

        int finalPage = page;
        source.sendSuccess(() -> Component.literal(
                String.format("§6=== Chunks (%s) - Page %d/%d (%d total) ===",
                        stateFilter, finalPage, totalPages, allChunks.size())), false);

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allChunks.size());

        for (int i = startIndex; i < endIndex; i++) {
            final Component chunkInfo = allChunks.get(i);
            source.sendSuccess(() -> chunkInfo, false);
        }
    }

    private static Component formatChunkInfo(ChunkDataCache.ChunkInfo chunkInfo) {
        String stateName = ChunkManager.getStateName(chunkInfo);
        String dimName = chunkInfo.dimension().getPath();
        int worldX = chunkInfo.pos().x * 16 + 8;
        int worldZ = chunkInfo.pos().z * 16 + 8;

        MutableComponent baseInfo = Component.literal(
                String.format("§f%s §7(%d,%d) §e%s §6BlockEntities:%d ",
                        dimName, chunkInfo.pos().x, chunkInfo.pos().z, stateName, chunkInfo.blockEntityCount()));

        MutableComponent teleportButton = Component.literal("§a[TP]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/tp @s " + worldX + " ~ " + worldZ))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("§7Click to teleport to chunk center\n" +
                                        "§7World coordinate: " + worldX + ", " + worldZ + "\n" +
                                        "§7Chunk coordinate: " + chunkInfo.pos().x + ", " + chunkInfo.pos().z + "\n" +
                                        "§7Block Entities: " + chunkInfo.blockEntityCount()))));

        return baseInfo.append(teleportButton);
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        Config.updateCaches();
        context.getSource().sendSuccess(() ->
                Component.literal("§a[Config Reload] Configuration reloaded successfully"), false);
        return 1;
    }

    // === 补全建议方法 ===

    private static CompletableFuture<Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            List<String> dimensionIds = context.getSource().getServer().levelKeys().stream()
                    .map(ResourceKey::location)
                    .map(ResourceLocation::toString)
                    .toList();
            return SharedSuggestionProvider.suggest(dimensionIds, builder);
        } catch (Exception e) {
            List<String> fallback = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
            return SharedSuggestionProvider.suggest(fallback, builder);
        }
    }

    private static CompletableFuture<Suggestions> suggestBoxNumbers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
            List<TrashBox> existingBoxes = TrashBoxManager.getDimensionTrashBoxes(dimensionId);

            List<String> suggestions = new ArrayList<>();
            for (int i = 1; i <= Math.max(1, existingBoxes.size()); i++) {
                suggestions.add(String.valueOf(i));
            }

            return SharedSuggestionProvider.suggest(suggestions, builder);
        } catch (Exception e) {
            return SharedSuggestionProvider.suggest(List.of("1", "2", "3", "4", "5"), builder);
        }
    }

    private static CompletableFuture<Suggestions> suggestChunkStates(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<String> states = List.of("ALL", "UNIMPORTANT", "MANAGED", "ITEM_FROZEN", "PERFORMANCE_FROZEN");
        return SharedSuggestionProvider.suggest(states, builder);
    }

    // === 工具方法 ===

    private static ServerPlayer getPlayerFromContext(CommandContext<CommandSourceStack> context) {
        try {
            return (ServerPlayer) context.getSource().getEntity();
        } catch (Exception e) {
            sendError(context.getSource(), "This command can only be executed by a player");
            return null;
        }
    }

    private static String getStringArgument(CommandContext<CommandSourceStack> context) {
        try {
            return StringArgumentType.getString(context, "state");
        } catch (Exception e) {
            return "ALL";
        }
    }

    private static int getIntArgument(CommandContext<CommandSourceStack> context) {
        try {
            return IntegerArgumentType.getInteger(context, "page");
        } catch (Exception e) {
            return 1;
        }
    }

    private static void sendError(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("§c" + message));
    }
}