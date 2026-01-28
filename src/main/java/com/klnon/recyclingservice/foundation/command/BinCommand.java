package com.klnon.recyclingservice.foundation.command;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxMenu;
import com.klnon.recyclingservice.content.trashbox.data.TrashBox;
import com.klnon.recyclingservice.foundation.events.AutoCleanupEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

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
                "§e/bin cleanup §7- Manually trigger cleanup",
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

    // === 工具方法 ===

    private static ServerPlayer getPlayerFromContext(CommandContext<CommandSourceStack> context) {
        try {
            return (ServerPlayer) context.getSource().getEntity();
        } catch (Exception e) {
            sendError(context.getSource(), "This command can only be executed by a player");
            return null;
        }
    }

    private static void sendError(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("§c" + message));
    }
}
