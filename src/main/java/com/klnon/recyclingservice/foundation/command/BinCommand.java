package com.klnon.recyclingservice.foundation.command;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
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
import java.util.function.Predicate;

/**
 * 垃圾箱命令处理器
 * <p>
 * 命令列表：
 * - /bin                           显示帮助
 * - /bin open <维度> <编号>         打开指定垃圾箱
 * - /bin cleanup                   手动触发清理（管理员）
 * - /bin reload                    重载配置（管理员）
 */
public class BinCommand {

    // ==================== 常量 ====================

    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private static final Predicate<CommandSourceStack> REQUIRE_ADMIN =
            source -> source.hasPermission(ADMIN_PERMISSION_LEVEL);

    private static final int BOX_NUMBER_MIN = 1;
    private static final int BOX_NUMBER_MAX = 5;

    private static final List<String> DEFAULT_DIMENSIONS = List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

    private static final List<String> DEFAULT_BOX_NUMBERS = List.of("1", "2", "3", "4", "5");

    // ==================== 命令注册 ====================

    /**
     * 注册 /bin 命令及其子命令
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bin")
                .requires(CommandSourceStack::isPlayer)
                .then(Commands.literal("open")
                        .then(Commands.argument("dimension", ResourceLocationArgument.id())
                                .suggests(BinCommand::suggestDimensions)
                                .then(Commands.argument("box_number", IntegerArgumentType.integer(BOX_NUMBER_MIN, BOX_NUMBER_MAX))
                                        .suggests(BinCommand::suggestBoxNumbers)
                                        .executes(BinCommand::executeOpen))))
                .then(Commands.literal("cleanup")
                        .requires(REQUIRE_ADMIN)
                        .executes(BinCommand::executeCleanup))
                .then(Commands.literal("reload")
                        .requires(REQUIRE_ADMIN)
                        .executes(BinCommand::executeReload))
                .executes(BinCommand::executeHelp));
    }

    // ==================== 命令执行 ====================

    /**
     * 显示帮助信息
     */
    private static int executeHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        // 发送配置的帮助消息
        Config.MESSAGE.cmdHelpMessages.get().forEach(message ->
                source.sendSuccess(() -> Component.literal(message), false));
        source.sendSuccess(() -> Component.literal("§e/bin cleanup §7- Manually trigger cleanup"), false);
        source.sendSuccess(() -> Component.literal("§e/bin reload §7- Reload configuration"), false);

        return 1;
    }

    /**
     * 打开垃圾箱
     */
    private static int executeOpen(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) return 0;

        try {
            ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
            int boxNumber = IntegerArgumentType.getInteger(context, "box_number");
            return TrashBoxManager.openTrashBox(player, dimensionId, boxNumber) ? 1 : 0;
        } catch (Exception e) {
            sendError(context.getSource(), "Failed to open trash box: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 手动触发清理
     */
    private static int executeCleanup(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = getPlayer(context);
        if (player == null) return 0;

        context.getSource().sendSuccess(
                () -> Component.literal(Config.MESSAGE.manualCleanupStart.get()), true);
        AutoCleanupEvent.doCleanup();

        return 1;
    }

    /**
     * 重载配置
     */
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        Config.updateCaches();
        context.getSource().sendSuccess(
                () -> Component.literal("§a[Config Reload] Configuration reloaded successfully"), false);
        return 1;
    }

    // ==================== 补全建议 ====================

    /**
     * 建议可用的维度ID
     */
    private static CompletableFuture<Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            List<String> dimensions = context.getSource().getServer().levelKeys().stream()
                    .map(ResourceKey::location)
                    .map(ResourceLocation::toString)
                    .toList();
            return SharedSuggestionProvider.suggest(dimensions, builder);
        } catch (Exception e) {
            return SharedSuggestionProvider.suggest(DEFAULT_DIMENSIONS, builder);
        }
    }

    /**
     * 建议可用的垃圾箱编号
     */
    private static CompletableFuture<Suggestions> suggestBoxNumbers(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            ResourceLocation dimensionId = ResourceLocationArgument.getId(context, "dimension");
            List<TrashBox> existingBoxes = TrashBoxManager.getDimensionTrashBoxes(dimensionId);

            List<String> suggestions = new ArrayList<>();
            int maxNumber = Math.max(1, existingBoxes.size());
            for (int i = 1; i <= maxNumber; i++) {
                suggestions.add(String.valueOf(i));
            }

            return SharedSuggestionProvider.suggest(suggestions, builder);
        } catch (Exception e) {
            return SharedSuggestionProvider.suggest(DEFAULT_BOX_NUMBERS, builder);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从命令上下文获取玩家
     */
    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> context) {
        try {
            return (ServerPlayer) context.getSource().getEntity();
        } catch (Exception e) {
            sendError(context.getSource(), "This command can only be executed by a player");
            return null;
        }
    }

    /**
     * 发送错误消息
     */
    private static void sendError(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("§c" + message));
    }
}