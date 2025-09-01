package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/**
 * 消息模板配置
 */
public class MessageConfig extends ConfigSection {
    
    public ModConfigSpec.ConfigValue<String> itemCountDisplayFormat;
    public ModConfigSpec.ConfigValue<String> cleanupResultHeader;
    public ModConfigSpec.ConfigValue<String> dimensionEntryFormat;
    public ModConfigSpec.ConfigValue<String> errorCleanupFailed;
    public ModConfigSpec.ConfigValue<String> manualCleanupStart;
    public ModConfigSpec.ConfigValue<String> paymentErrorMessage;
    public ModConfigSpec.ConfigValue<String> paymentSuccessMessage;
    public ModConfigSpec.ConfigValue<String> warningMessage;
    public ModConfigSpec.ConfigValue<String> tooManyItemsWarningMessage;
    public ModConfigSpec.ConfigValue<List<? extends String>> cmdHelpMessages;
    public ModConfigSpec.ConfigValue<String> trashBoxButtonText;
    public ModConfigSpec.ConfigValue<String> trashBoxButtonHover;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        itemCountDisplayFormat = builder
                .comment("Format for item count display. {current} = current count, {max} = maximum stack size")
                .define("item_count_display_format", "§7Available: §a{current} / §b{max}");
        
        cleanupResultHeader = builder
                .comment("Header text for detailed cleanup results")
                .define("cleanup_result_header", "> §a§lCleanup results:");
        
        dimensionEntryFormat = builder
                .comment("Format for each dimension entry in cleanup message. {name} {items} {entities}")
                .define("dimension_entry_format", "§f{name}: §b{items} §fitems, §d{entities} §fentities");
        
        errorCleanupFailed = builder
                .comment("Message shown when cleanup fails")
                .define("error_cleanup_failed", "§cCleanup failed");
        
        manualCleanupStart = builder
                .comment("Message shown when manual cleanup starts")
                .define("manual_cleanup_start", "§6[Manual Cleanup] Starting cleanup...");
        
        paymentErrorMessage = builder
                .comment("Message shown when player doesn't have enough payment items. {cost} = required amount, {item} = item name")
                .define("payment_error_message", "§cNeed {cost} {item} as postage!");
        
        paymentSuccessMessage = builder
                .comment("Message shown when payment is successfully deducted. {cost} = deducted amount, {item} = item name")
                .define("payment_success_message", "§aDeducted {cost} {item} as postage");

        warningMessage = builder
                .comment("Warning message template (use {time} for remaining seconds)")
                .define("warning_message", "§e[Auto Clean] Items will be cleaned up in {time} seconds!");

        tooManyItemsWarningMessage = builder
                .comment("Warning message for too many items. {count} = item count, {x} {z} = world coordinates, {ticket} = ticket level")
                .define("too_many_items_warning_message", "§e[Items Warning] Found {count} items at ({x}, {z}) ticketLevel:{ticket}");
        
        cmdHelpMessages = builder
                .comment("Command help messages")
                .defineListAllowEmpty("cmd_help_messages",
                    List.of(
                        "§6=== Trash Box Command Help ===",
                        "§e/bin open <dimension> <box> §7- Open specific dimension trash box",
                        "§e/bin cleanup §7- Manually trigger cleanup",
                        "§e/bin tickets <x> <z> §7- Show chunk tickets info"
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
    
    @Override
    public String getName() {
        return "messages";
    }
    
    @Override
    public String getDescription() {
        return "Message templates";
    }
}