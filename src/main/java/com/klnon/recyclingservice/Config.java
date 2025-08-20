package com.klnon.recyclingservice;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;
import com.klnon.recyclingservice.util.ErrorHandler;

import java.util.ArrayList;
import java.util.List;

public class Config {
    // 配置构建器
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    // 配置规范
    public static final ModConfigSpec SPEC;
    
    // === 基础清理设置 ===
    public static final ModConfigSpec.IntValue AUTO_CLEAN_TIME;
    public static final ModConfigSpec.BooleanValue SHOW_CLEANUP_WARNINGS;
    
    // === 警告消息设置 ===
    public static final ModConfigSpec.ConfigValue<String> WARNING_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> CLEANUP_COMPLETE_MESSAGE;
    
    // === 垃圾箱设置 ===
    public static final ModConfigSpec.IntValue TRASH_BOX_SIZE;
    public static final ModConfigSpec.BooleanValue DIMENSION_TRASH_ALLOW_PUT_IN;
    
    // === 维度管理 ===
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SUPPORTED_DIMENSIONS;
    public static final ModConfigSpec.IntValue MAX_BOXES_PER_DIMENSION;
    public static final ModConfigSpec.BooleanValue AUTO_CREATE_DIMENSION_TRASH;
    
    // === 付费系统 ===
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ITEM_TYPE;
    public static final ModConfigSpec.IntValue CROSS_DIMENSION_ACCESS_COST;
    
    // === 物品过滤 ===
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALWAYS_CLEAN_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NEVER_CLEAN_ITEMS;
    public static final ModConfigSpec.BooleanValue ONLY_CLEAN_LISTED_ITEMS;
    
    // === 弹射物过滤 ===
    public static final ModConfigSpec.BooleanValue CLEAN_PROJECTILES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_TYPES_TO_CLEAN;
    
    // === 区域管理 ===
    public static final ModConfigSpec.IntValue TOO_MANY_ITEMS_WARNING;
    public static final ModConfigSpec.BooleanValue AUTO_STOP_CHUNK_LOADING;
    public static final ModConfigSpec.ConfigValue<String> TOO_MANY_ITEMS_WARNING_MESSAGE;
    
    static {
        // 基础清理设置
        BUILDER.comment("Auto cleanup settings / 自动清理设置").push("auto_cleanup");
        
        AUTO_CLEAN_TIME = BUILDER
                .comment("How often to automatically clean up items (in seconds) / 自动清理掉落物品的间隔时间（秒）",
                        "Default: 600, Min: 30, Max: 7200")
                .translation("recycle.config.auto_clean_time")
                .defineInRange("auto_clean_time_seconds", 600, 30, 7200);
        
        SHOW_CLEANUP_WARNINGS = BUILDER
                .comment("Show warning messages before cleaning up / 是否在清理前显示警告消息",
                        "Default: true")
                .translation("recycle.config.show_cleanup_warnings")
                .define("show_cleanup_warnings", true);
        
        BUILDER.pop();
        
        // 警告消息设置
        BUILDER.comment("Warning message settings / 警告消息设置").push("warning_messages");
        
        WARNING_MESSAGE = BUILDER
                .comment("Warning message template (use {time} for remaining seconds) / 警告消息模板（使用{time}显示剩余秒数）",
                        "Default: §e[Auto Clean] Items will be cleaned up in {time} seconds!")
                .translation("recycle.config.warning_message")
                .define("warning_message", "§e[Auto Clean] Items will be cleaned up in {time} seconds!");
        
        CLEANUP_COMPLETE_MESSAGE = BUILDER
                .comment("Message shown when cleanup is complete (use {count} for number of items cleaned) / 清理完成后显示的消息（使用{count}显示清理的物品数量）",
                        "Default: §a[Auto Clean] Cleaned up {count} items!")
                .translation("recycle.config.cleanup_complete_message")
                .define("cleanup_complete_message", "§a[Auto Clean] Cleaned up {count} items!");
        
        BUILDER.pop();
        
        // 垃圾箱设置
        BUILDER.comment("Trash box settings / 垃圾箱系统设置").push("trash_box");
        
        TRASH_BOX_SIZE = BUILDER
                .comment("How many slots each trash box can hold / 每个垃圾箱的格子数量",
                        "Default: 54, Min: 9, Max: 108")
                .translation("recycle.config.trash_box_size")
                .defineInRange("trash_box_size", 54, 9, 108);
        
        DIMENSION_TRASH_ALLOW_PUT_IN = BUILDER
                .comment("Allow players to put items into dimension trash box / 是否允许玩家主动将物品放入维度垃圾箱",
                        "Default: true")
                .translation("recycle.config.dimension_trash_allow_put_in")
                .define("dimension_trash_allow_put_in", true);
        
        BUILDER.pop();
        
        // 维度管理设置
        BUILDER.comment("Dimension management settings / 维度管理设置").push("dimension_management");
        
        SUPPORTED_DIMENSIONS = BUILDER
                .comment("List of dimensions to create trash boxes for / 支持创建垃圾箱的维度列表",
                        "Default: [minecraft:overworld, minecraft:the_nether, minecraft:the_end]")
                .translation("recycle.config.supported_dimensions")
                .defineListAllowEmpty("supported_dimensions",
                    List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    () -> "",
                    Config::validateResourceLocation);
        
        MAX_BOXES_PER_DIMENSION = BUILDER
                .comment("Maximum number of trash boxes per dimension / 每个维度最大垃圾箱数量",
                        "Default: 5, Min: 1, Max: 5")
                .translation("recycle.config.max_boxes_per_dimension")
                .defineInRange("max_boxes_per_dimension", 5, 1, 5);
        
        AUTO_CREATE_DIMENSION_TRASH = BUILDER
                .comment("Automatically create trash boxes for new dimensions / 自动为新维度创建垃圾箱",
                        "Default: true")
                .translation("recycle.config.auto_create_dimension_trash")
                .define("auto_create_dimension_trash", true);
        
        BUILDER.pop();
        
        // 邮费系统
        BUILDER.comment("Payment system for cross-dimension access / 跨维度访问邮费系统").push("payment");
        
        PAYMENT_ITEM_TYPE = BUILDER
                .comment("What item to use as payment (example: minecraft:emerald) / 用作邮费的物品类型（例如：minecraft:emerald）",
                        "Default: minecraft:emerald")
                .translation("recycle.config.payment_item_type")
                .define("payment_item_type", "minecraft:emerald");
        
        CROSS_DIMENSION_ACCESS_COST = BUILDER
                .comment("Payment required to access other dimension trash boxes / 访问其他维度垃圾箱需要的邮费数量",
                        "Default: 1, Min: 1, Max: 64")
                .translation("recycle.config.cross_dimension_access_cost")
                .defineInRange("cross_dimension_access_cost", 1, 1, 64);
        
        BUILDER.pop();
        
        // 过滤系统
        BUILDER.comment("Item filtering settings / 物品过滤设置").push("item_filter");
        
        ONLY_CLEAN_LISTED_ITEMS = BUILDER
                .comment("If true, only clean items in the 'always clean' list. If false, clean everything except 'never clean' list / 如果为true，只清理'总是清理'列表中的物品。如果为false，清理除'永不清理'外的所有物品",
                        "Default: false")
                .translation("recycle.config.only_clean_listed_items")
                .define("only_clean_listed_items", false);
        
        ALWAYS_CLEAN_ITEMS = BUILDER
                .comment("Items that will always be cleaned up (when 'only clean listed items' is true) / 黑名单（当'仅清理指定物品'启用时）",
                        "Default: [minecraft:cobblestone, minecraft:dirt, minecraft:gravel]")
                .translation("recycle.config.always_clean_items")
                .defineListAllowEmpty("always_clean_items", 
                    List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel"),
                    () -> "",
                    Config::validateResourceLocation);
        
        NEVER_CLEAN_ITEMS = BUILDER
                .comment("Items that will never be cleaned up / 白名单",
                        "Default: [minecraft:diamond, minecraft:netherite_ingot, minecraft:elytra]")
                .translation("recycle.config.never_clean_items")
                .defineListAllowEmpty("never_clean_items",
                    List.of("minecraft:diamond", "minecraft:netherite_ingot", "minecraft:elytra"),
                    () -> "",
                    Config::validateResourceLocation);
        
        BUILDER.pop();

        // === 弹射物清理设置 === (添加到合适的位置)
        BUILDER.comment("Projectile cleanup settings / 弹射物清理设置").push("projectile_cleanup");
        CLEAN_PROJECTILES = BUILDER
            .comment("Enable cleaning up projectiles that can cause lag / 是否清理可能造成卡顿的弹射物",
                    "Default: true")
            .translation("recycle.config.clean_projectiles")
            .define("clean_projectiles", true);

        PROJECTILE_TYPES_TO_CLEAN = BUILDER
            .comment("Types of projectiles to clean up (all projectiles that can cause lag) / 要清理的弹射物类型（所有可能造成卡顿的弹射物）",
                    "Default: arrows, fireballs, potions, etc. that can accumulate and cause performance issues",
                    "默认：箭矢、火球等可能大量堆积造成性能问题的弹射物")
            .translation("recycle.config.projectile_types_to_clean")
            .defineListAllowEmpty("projectile_types_to_clean",
                List.of(
                    // 箭矢类
                    "minecraft:arrow", 
                    "minecraft:spectral_arrow",
                    // 火球类  
                    "minecraft:dragon_fireball", 
                    "minecraft:wither_skull", 
                    "minecraft:fireball", 
                    "minecraft:small_fireball",
                    // 投掷物类
                    "minecraft:snowball",
                    // 其他弹射物
                    "minecraft:shulker_bullet",
                    "minecraft:llama_spit"
                ),
                () -> "",
                Config::validateResourceLocation);

        BUILDER.pop();
        
        
        // 区块管理
        BUILDER.comment("Chunk management settings / 区块管理设置").push("chunk_management");
        
        TOO_MANY_ITEMS_WARNING = BUILDER
                .comment("Warn when a chunk has more than this many items / 当区块中物品超过此数量时发出警告",
                        "Default: 200, Min: 50, Max: 1000")
                .translation("recycle.config.too_many_items_warning")
                .defineInRange("too_many_items_warning_limit", 200, 50, 1000);
        
        AUTO_STOP_CHUNK_LOADING = BUILDER
                .comment("Automatically stop chunk loading when chunk has too many items / 当区块物品过多时自动停止区块加载",
                        "Default: true")
                .translation("recycle.config.auto_stop_chunk_loading")
                .define("auto_stop_chunk_loading", true);
        
        TOO_MANY_ITEMS_WARNING_MESSAGE = BUILDER
                .comment("Warning message for too many items (use {count} for item count, {threshold} for threshold) / 物品过多警告消息（使用{count}显示物品数量，{threshold}显示阈值）",
                        "Default: recycle.warning.too_many_items")
                .translation("recycle.config.too_many_items_warning_message")
                .define("too_many_items_warning_message", "recycle.warning.too_many_items");
        
        BUILDER.pop();
        
        // 构建配置规范
        SPEC = BUILDER.build();
    }
    
    /**
     * 验证资源ID格式是否正确
     */
    private static boolean validateResourceLocation(Object obj) {
        if (!(obj instanceof String)) {
            return false;
        }
        String Id = (String) obj;
        return ErrorHandler.handleStaticOperation("validateResourceLocation", () -> {
            ResourceLocation.parse(Id);
            return true;
        }, false);
    }
    // === 便捷访问方法 ===
    
    /**
     * 获取清理间隔（tick）
     */
    public static int getCleanIntervalTicks() {
        return AUTO_CLEAN_TIME.get() * 20; // 秒转tick
    }
    
    /**
     * 获取付费物品的ResourceLocation
     */
    public static ResourceLocation getPaymentItem() {
        return ResourceLocation.parse(PAYMENT_ITEM_TYPE.get());
    }
    
    /**
     * 获取跨维度访问邮费数量
     */
    public static int getCrossDimensionCost() {
        return CROSS_DIMENSION_ACCESS_COST.get();
    }
    
    /**
     * 检查维度是否在支持列表中
     */
    public static boolean isDimensionSupported(String dimensionId) {
        if (Config.AUTO_CREATE_DIMENSION_TRASH.get()) {
            return true;
        }
        return SUPPORTED_DIMENSIONS.get().contains(dimensionId);
    }
    
    /**
     * 获取支持的维度列表
     */
    public static List<String> getSupportedDimensions() {
        return new ArrayList<>(SUPPORTED_DIMENSIONS.get());
    }
    
    /**
     * 获取格式化的警告消息
     */
    public static String getWarningMessage(int remainingSeconds) {
        return WARNING_MESSAGE.get().replace("{time}", String.valueOf(remainingSeconds));
    }
    
    /**
     * 获取格式化的清理完成消息
     */
    public static String getCleanupCompleteMessage(int itemCount) {
        return CLEANUP_COMPLETE_MESSAGE.get().replace("{count}", String.valueOf(itemCount));
    }

    // === 添加便捷访问方法 ===
    /**
     * 检查是否应该清理弹射物
     */
    public static boolean shouldCleanProjectiles() {
        return CLEAN_PROJECTILES.get();
    }

    /**
     * 检查特定实体类型是否应该被清理
     */
    public static boolean shouldCleanEntityType(String entityTypeId) {
        if (!shouldCleanProjectiles()) {
            return false;
        }
        return PROJECTILE_TYPES_TO_CLEAN.get().contains(entityTypeId);
    }
}