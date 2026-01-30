package com.klnon.recyclingservice.foundation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 消息模板配置 - 所有用户可见的消息文本
 */
public class MessageConfig {

    // ==================== General ====================
    public final ModConfigSpec.ConfigValue<String> messagePrefix;

    // ==================== UI Display ====================
    public final ModConfigSpec.ConfigValue<String> itemCountDisplayFormat;
    public final ModConfigSpec.ConfigValue<String> postageCostDisplayFormat;
    public final ModConfigSpec.ConfigValue<String> trashBoxButtonText;
    public final ModConfigSpec.ConfigValue<String> trashBoxButtonHover;

    // ==================== Cleanup Messages ====================
    public final ModConfigSpec.ConfigValue<String> warningMessage;
    public final ModConfigSpec.ConfigValue<String> manualCleanupStart;
    public final ModConfigSpec.ConfigValue<String> cleanupResultHeader;
    public final ModConfigSpec.ConfigValue<String> dimensionEntryFormat;
    public final ModConfigSpec.ConfigValue<String> errorCleanupFailed;

    // ==================== Payment Messages ====================
    public final ModConfigSpec.ConfigValue<String> paymentSuccessMessage;
    public final ModConfigSpec.ConfigValue<String> paymentErrorMessage;

    // ==================== Command Help ====================
    public final ModConfigSpec.ConfigValue<List<? extends String>> cmdHelpMessages;

    public MessageConfig(ModConfigSpec.Builder builder) {
        builder.comment("Message Templates").push("messages");

        // ==================== General ====================
        builder.comment("General settings").push("general");

        messagePrefix = builder
                .comment("Prefix for all mod messages")
                .define("prefix", "[RecyclingService] ");

        builder.pop();

        // ==================== UI Display ====================
        builder.comment("UI display formats").push("ui");

        itemCountDisplayFormat = builder
                .comment("Format for item count tooltip. Variables: {current}, {max}")
                .define("item_count_format", "§7Available: §a{current} / §b{max}");

        postageCostDisplayFormat = builder
                .comment("Format for postage cost tooltip. Variables: {cost}, {item}")
                .define("postage_cost_format", "cost: {cost} {item}");

        trashBoxButtonText = builder
                .comment("Text for trash box button. Variables: {name}, {box}")
                .define("button_text", "[#Box{box}]");

        trashBoxButtonHover = builder
                .comment("Hover text for trash box button. Variables: {name}, {box}")
                .define("button_hover", "Click to open trash box #{box} in {name}");

        builder.pop();

        // ==================== Cleanup Messages ====================
        builder.comment("Cleanup related messages").push("cleanup");

        warningMessage = builder
                .comment("Warning before cleanup. Variables: {time}")
                .define("warning", "§e[Auto Clean] Items will be cleaned up in {time} seconds!");

        manualCleanupStart = builder
                .comment("Message when manual cleanup starts")
                .define("manual_start", "§6[Manual Cleanup] Starting cleanup...");

        cleanupResultHeader = builder
                .comment("Header for cleanup results")
                .define("result_header", "> §a§lCleanup results:");

        dimensionEntryFormat = builder
                .comment("Format for each dimension in results. Variables: {name}, {items}, {entities}")
                .define("dimension_entry", "§f{name}: §b{items} §fitems, §d{entities} §fentities");

        errorCleanupFailed = builder
                .comment("Message when cleanup fails")
                .define("error_failed", "§cCleanup failed");

        builder.pop();

        // ==================== Payment Messages ====================
        builder.comment("Payment related messages").push("payment");

        paymentSuccessMessage = builder
                .comment("Message when payment succeeds. Variables: {cost}, {item}")
                .define("success", "§aDeducted {cost} {item} as postage");

        paymentErrorMessage = builder
                .comment("Message when payment fails (insufficient funds). Variables: {cost}, {item}")
                .define("error", "§cNeed {cost} {item} as postage!");

        builder.pop();

        // ==================== Command Help ====================
        builder.comment("Command help messages").push("command");

        cmdHelpMessages = builder
                .comment("Help messages shown by /bin command")
                .defineListAllowEmpty("help_messages",
                        List.of(
                                "§6=== Trash Box Command Help ===",
                                "§e/bin open <dimension> <box> §7- Open specific dimension trash box",
                                "§e/bin cleanup §7- Manually trigger cleanup"
                        ),
                        () -> "",
                        obj -> obj instanceof String);

        builder.pop();

        builder.pop();
    }
}