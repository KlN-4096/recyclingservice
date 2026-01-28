package com.klnon.recyclingservice.foundation.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/**
 * 娑堟伅妯℃澘閰嶇疆 - 鎵€鏈夌敤鎴峰彲瑙佺殑娑堟伅鏂囨湰
 */
public class MessageConfig {

    public final ModConfigSpec.ConfigValue<String> itemCountDisplayFormat;
    public final ModConfigSpec.ConfigValue<String> postageCostDisplayFormat;
    public final ModConfigSpec.ConfigValue<String> cleanupResultHeader;
    public final ModConfigSpec.ConfigValue<String> dimensionEntryFormat;
    public final ModConfigSpec.ConfigValue<String> errorCleanupFailed;
    public final ModConfigSpec.ConfigValue<String> manualCleanupStart;
    public final ModConfigSpec.ConfigValue<String> paymentErrorMessage;
    public final ModConfigSpec.ConfigValue<String> paymentSuccessMessage;
    public final ModConfigSpec.ConfigValue<String> messagePrefix;
    public final ModConfigSpec.ConfigValue<String> warningMessage;
    public final ModConfigSpec.ConfigValue<List<? extends String>> cmdHelpMessages;
    public final ModConfigSpec.ConfigValue<String> trashBoxButtonText;
    public final ModConfigSpec.ConfigValue<String> trashBoxButtonHover;

    public MessageConfig(ModConfigSpec.Builder builder) {
        builder.comment("Message Templates").push("messages");

        itemCountDisplayFormat = builder
                .comment("Format for item count display. {current} = current count, {max} = maximum stack size")
                .define("item_count_display_format", "\u00A77Available: \u00A7a{current} / \u00A7b{max}");
        postageCostDisplayFormat = builder
                .comment("Format for postage cost display. {cost} = cost, {item} = payment item name")
                .define("postage_cost_display_format", "cost: {cost} {item}");

        cleanupResultHeader = builder
                .comment("Header text for detailed cleanup results")
                .define("cleanup_result_header", "> \u00A7a\u00A7lCleanup results:");

        dimensionEntryFormat = builder
                .comment("Format for each dimension entry in cleanup message. {name} {items} {entities}")
                .define("dimension_entry_format", "\u00A7f{name}: \u00A7b{items} \u00A7fitems, \u00A7d{entities} \u00A7fentities");

        errorCleanupFailed = builder
                .comment("Message shown when cleanup fails")
                .define("error_cleanup_failed", "\u00A7cCleanup failed");

        manualCleanupStart = builder
                .comment("Message shown when manual cleanup starts")
                .define("manual_cleanup_start", "\u00A76[Manual Cleanup] Starting cleanup...");

        paymentErrorMessage = builder
                .comment("Message shown when player doesn't have enough payment items. {cost} = required amount, {item} = item name")
                .define("payment_error_message", "\u00A7cNeed {cost} {item} as postage!");

        paymentSuccessMessage = builder
                .comment("Message shown when payment is successfully deducted. {cost} = deducted amount, {item} = item name")
                .define("payment_success_message", "\u00A7aDeducted {cost} {item} as postage");
        messagePrefix = builder
                .comment("Prefix for payment messages")
                .define("message_prefix", "[RecyclingService] ");

        warningMessage = builder
                .comment("Warning message template (use {time} for remaining seconds)")
                .define("warning_message", "\u00A7e[Auto Clean] Items will be cleaned up in {time} seconds!");

        cmdHelpMessages = builder
                .comment("Command help messages")
                .defineListAllowEmpty("cmd_help_messages",
                    List.of(
                        "\u00A76=== Trash Box Command Help ===",
                        "\u00A7e/bin open <dimension> <box> \u00A77- Open specific dimension trash box",
                        "\u00A7e/bin cleanup \u00A77- Manually trigger cleanup"
                    ),
                    () -> "",
                    obj -> obj instanceof String);

        trashBoxButtonText = builder
                .comment("Text for trash box button. {name} = dimension name")
                .define("trash_box_button_text", "[Open Trash Box]");

        trashBoxButtonHover = builder
                .comment("Hover text for trash box button. {name} = dimension name")
                .define("trash_box_button_hover", "Click to open trash box #1 in {name}");

        builder.pop();
    }
}
