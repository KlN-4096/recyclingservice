package com.klnon.recyclingservice.foundation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Gameplay configuration for cleanup, trash box, item filtering, and payment system.
 */
public class GameplayConfig {

    // ==================== Auto Cleanup ====================
    public final ModConfigSpec.IntValue autoCleanTime;
    public final ModConfigSpec.BooleanValue showCleanupWarnings;
    public final ModConfigSpec.IntValue warningCountdownStart;

    // ==================== Trash Box ====================
    public final ModConfigSpec.IntValue trashBoxRows;
    public final ModConfigSpec.IntValue itemStackMultiplier;
    public final ModConfigSpec.IntValue maxBoxesPerDimension;
    public final ModConfigSpec.ConfigValue<List<? extends String>> dimensionTrashAllowPutIn;
    public final ModConfigSpec.BooleanValue dimensionTrashCrossAccess;

    // ==================== Item Filter ====================
    public final ModConfigSpec.ConfigValue<String> cleanMode;
    public final ModConfigSpec.ConfigValue<List<? extends String>> whitelist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> blacklist;
    public final ModConfigSpec.BooleanValue cleanProjectiles;
    public final ModConfigSpec.ConfigValue<List<? extends String>> projectileTypesToClean;
    public final ModConfigSpec.BooleanValue protectCreateProcessingItems;

    // ==================== Payment System ====================
    public final ModConfigSpec.ConfigValue<String> paymentItemType;
    public final ModConfigSpec.ConfigValue<String> extractPaymentMode;
    public final ModConfigSpec.IntValue crossDimensionAccessCost;
    public final ModConfigSpec.BooleanValue autoCleanItemsFree;
    public final ModConfigSpec.BooleanValue extractPenaltyCrossDimensionOnly;
    public final ModConfigSpec.ConfigValue<List<? extends String>> dimensionMultipliers;
    public final ModConfigSpec.ConfigValue<List<? extends String>> extractCostCaps;
    public final ModConfigSpec.ConfigValue<String> extractCostFormula;

    public GameplayConfig(ModConfigSpec.Builder builder) {
        builder.comment("Gameplay Settings").push("gameplay");

        // ==================== Auto Cleanup ====================
        builder.comment("Auto cleanup settings").push("cleanup");

        autoCleanTime = builder
                .comment("Auto cleanup interval in seconds")
                .defineInRange("interval_seconds", 600, 30, 7200);

        showCleanupWarnings = builder
                .comment("Show warning messages before cleanup")
                .define("show_warnings", true);

        warningCountdownStart = builder
                .comment("Start countdown warnings at remaining seconds")
                .defineInRange("countdown_start", 15, 0, 300);

        builder.pop();

        // ==================== Trash Box ====================
        builder.comment("Trash box settings").push("trash_box");

        trashBoxRows = builder
                .comment("Number of rows in each trash box")
                .defineInRange("rows", 6, 1, 6);

        itemStackMultiplier = builder
                .comment("Stack size multiplier")
                .defineInRange("stack_multiplier", 100, 1, 1000);

        maxBoxesPerDimension = builder
                .comment("Maximum trash boxes per dimension")
                .defineInRange("max_boxes", 3, 1, 5);

        dimensionTrashAllowPutIn = builder
                .comment("Dimensions that allow players to put items")
                .defineListAllowEmpty("allow_put_dimensions",
                        List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                        () -> "", this::validateResourceLocation);

        dimensionTrashCrossAccess = builder
                .comment("Allow cross-dimension trash box access")
                .define("cross_access", true);

        builder.pop();

        // ==================== Item Filter ====================
        builder.comment("Item filter settings").push("filter");

        cleanMode = builder
                .comment("Item cleaning mode: whitelist or blacklist")
                .defineInList("mode", "whitelist", Arrays.asList("whitelist", "blacklist"));

        whitelist = builder
                .comment("Items protected from cleaning")
                .defineListAllowEmpty("whitelist",
                        List.of("minecraft:netherite_ingot", "minecraft:elytra"),
                        () -> "", this::validateResourceLocation);

        blacklist = builder
                .comment("Items to be cleaned")
                .defineListAllowEmpty("blacklist",
                        List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel"),
                        () -> "", this::validateResourceLocation);

        cleanProjectiles = builder
                .comment("Enable projectile cleanup")
                .define("clean_projectiles", true);

        projectileTypesToClean = builder
                .comment("Projectile types to clean")
                .defineListAllowEmpty("projectile_types",
                        List.of("minecraft:arrow", "minecraft:spectral_arrow", "minecraft:dragon_fireball",
                                "minecraft:wither_skull", "minecraft:fireball", "minecraft:small_fireball",
                                "minecraft:snowball", "minecraft:shulker_bullet", "minecraft:llama_spit"),
                        () -> "", this::validateResourceLocation);

        protectCreateProcessingItems = builder
                .comment("Protect items being processed by Create mod")
                .define("protect_create_items", true);

        builder.pop();

        // ==================== Payment System ====================
        builder.comment("Payment system settings").push("payment");

        // --- Basic Settings ---
        paymentItemType = builder
                .comment("Payment item type")
                .define("item_type", "minecraft:emerald");

        extractPaymentMode = builder
                .comment("""
                        Extract operation payment mode:
                        - all_free: All dimensions are free
                        - current_dimension_free: Current dimension is free, cross-dimension requires payment
                        - all_dimensions_pay: All dimensions require payment""")
                .defineInList("extract_mode", "current_dimension_free",
                        Arrays.asList("all_free", "current_dimension_free", "all_dimensions_pay"));

        // --- Cost Settings ---
        crossDimensionAccessCost = builder
                .comment("Base cost for cross-dimension access (used as 'base' variable in formula)")
                .defineInRange("cross_dimension_cost", 1, 1, 64);

        autoCleanItemsFree = builder
                .comment("Allow free extraction of auto-cleaned items (baseFlag=0)")
                .define("auto_clean_items_free", false);

        // --- Penalty Settings ---
        extractPenaltyCrossDimensionOnly = builder
                .comment("Only count 'recent' extracts for cross-dimension access")
                .define("extract_penalty_cross_dimension_only", true);

        // --- Dimension Settings ---
        dimensionMultipliers = builder
                .comment("Cost multipliers per dimension (format: dimension:multiplier)")
                .defineListAllowEmpty("dimension_multipliers",
                        List.of("minecraft:overworld:1.0", "minecraft:the_nether:1.0", "minecraft:the_end:2.0"),
                        () -> "minecraft:overworld:1.0",
                        obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_]+:[a-z0-9_]+:[0-9]+(\\.[0-9]+)?$"));

        extractCostCaps = builder
                .comment("Maximum cost per dimension (0 = unlimited, format: dimension:cap)")
                .defineListAllowEmpty("extract_cost_caps",
                        List.of("minecraft:overworld:2", "minecraft:the_nether:2", "minecraft:the_end:2"),
                        () -> "minecraft:overworld:0",
                        obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_]+:[a-z0-9_]+:[0-9]+$"));

        // --- Cost Formula (uses all above settings) ---
        extractCostFormula = builder
                .comment("""
                        Formula for extract cost calculation.
                        
                        Variables:
                          base       - Base cost (from cross_dimension_cost)
                          count      - Item count being extracted
                          recent     - Recent extract count in time window
                          same_dim   - 1 if same dimension, 0 otherwise
                          cross_dim  - 1 if cross dimension, 0 otherwise
                          multiplier - Dimension cost multiplier
                        
                        Functions: min, max, floor, ceil, abs, round, step
                        
                        Examples:
                          base                                           - Fixed cost
                          base * multiplier                              - With dimension multiplier
                          (base + count * 0.1) * multiplier              - Cost by item count
                          base + cross_dim * 20                          - Extra fee for cross dimension
                          (base + max(0, recent - 2) * 0.2) * multiplier - Penalty for frequent extracts
                        """)
                .define("extract_cost_formula", "(base + max(0, recent - 2) * 0.2) * multiplier");

        builder.pop();
        builder.pop();
    }

    private boolean validateResourceLocation(Object obj) {
        if (!(obj instanceof String id)) return false;
        try {
            net.minecraft.resources.ResourceLocation.parse(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}