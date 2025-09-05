package com.klnon.recyclingservice.foundation.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Arrays;
import java.util.List;

/**
 * 游戏玩法配置 - 包含清理、垃圾箱、物品过滤相关设置
 */
public class GameplayConfig {
    
    // === 自动清理设置 ===
    public final ModConfigSpec.IntValue autoCleanTime;
    public final ModConfigSpec.BooleanValue showCleanupWarnings;
    public final ModConfigSpec.IntValue warningCountdownStart;
    
    // === 垃圾箱设置 ===
    public final ModConfigSpec.IntValue trashBoxRows;
    public final ModConfigSpec.IntValue itemStackMultiplier;
    public final ModConfigSpec.IntValue maxBoxesPerDimension;
    public final ModConfigSpec.ConfigValue<List<? extends String>> dimensionTrashAllowPutIn;
    public final ModConfigSpec.BooleanValue dimensionTrashCrossAccess;
    
    // === 物品过滤设置 ===
    public final ModConfigSpec.ConfigValue<String> cleanMode;
    public final ModConfigSpec.ConfigValue<List<? extends String>> whitelist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> blacklist;
    public final ModConfigSpec.BooleanValue cleanProjectiles;
    public final ModConfigSpec.ConfigValue<List<? extends String>> projectileTypesToClean;
    public final ModConfigSpec.BooleanValue protectCreateProcessingItems;
    
    // === 支付系统设置 ===
    public final ModConfigSpec.ConfigValue<String> paymentItemType;
    public final ModConfigSpec.IntValue crossDimensionAccessCost;
    public final ModConfigSpec.ConfigValue<String> insertPaymentMode;
    public final ModConfigSpec.ConfigValue<String> extractPaymentMode;
    public final ModConfigSpec.ConfigValue<List<? extends String>> dimensionMultipliers;
    
    public GameplayConfig(ModConfigSpec.Builder builder) {
        builder.comment("Gameplay Settings").push("gameplay");
        
        // 自动清理
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
        
        // 垃圾箱
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
        
        // 物品过滤
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
                    List.of("minecraft:arrow", "minecraft:spectral_arrow", "minecraft:snowball"),
                    () -> "", this::validateResourceLocation);
        protectCreateProcessingItems = builder
                .comment("Protect items being processed by Create mod")
                .define("protect_create_items", true);
        builder.pop();
        
        // 支付系统
        builder.comment("Payment system settings").push("payment");
        paymentItemType = builder
                .comment("Payment item type")
                .define("item_type", "minecraft:emerald");
        crossDimensionAccessCost = builder
                .comment("Cost for cross-dimension access")
                .defineInRange("cross_dimension_cost", 1, 1, 64);
        insertPaymentMode = builder
                .comment("Insert payment mode")
                .defineInList("insert_mode", "current_dimension_free", 
                    Arrays.asList("all_dimensions_pay", "current_dimension_free", "all_free"));
        extractPaymentMode = builder
                .comment("Extract payment mode")
                .defineInList("extract_mode", "current_dimension_free", 
                    Arrays.asList("all_dimensions_pay", "current_dimension_free", "all_free"));
        dimensionMultipliers = builder
                .comment("Cost multipliers per dimension")
                .defineListAllowEmpty("dimension_multipliers", 
                    List.of("minecraft:overworld:1.0", "minecraft:the_nether:1.0", "minecraft:the_end:2.0"),
                    () -> "minecraft:overworld:1.0",
                    obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_]+:[a-z0-9_]+:[0-9]+(\\.[0-9]+)?$"));
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