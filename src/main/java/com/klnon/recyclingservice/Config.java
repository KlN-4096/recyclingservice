package com.klnon.recyclingservice;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.klnon.recyclingservice.util.other.ErrorHandler;

import java.util.HashSet;

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
    public static final ModConfigSpec.ConfigValue<String> CLEAN_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    
    // === 弹射物过滤 ===
    public static final ModConfigSpec.BooleanValue CLEAN_PROJECTILES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_TYPES_TO_CLEAN;
    
    // === 区域管理 ===
    public static final ModConfigSpec.IntValue TOO_MANY_ITEMS_WARNING;
    public static final ModConfigSpec.BooleanValue AUTO_STOP_CHUNK_LOADING;
    public static final ModConfigSpec.ConfigValue<String> TOO_MANY_ITEMS_WARNING_MESSAGE;
    
    // === 扫描优化设置 ===
    public static final ModConfigSpec.ConfigValue<String> SCAN_MODE;
    public static final ModConfigSpec.IntValue PLAYER_SCAN_RADIUS;
    public static final ModConfigSpec.IntValue BATCH_SIZE;
    
    // === 主线程调度优化设置 ===
    public static final ModConfigSpec.IntValue MAX_PROCESSING_TIME_MS;
    
    // === UI界面设置 ===
    public static final ModConfigSpec.IntValue ITEM_STACK_MERGE_LIMIT;
    
    // === 颜色配置 ===
    public static final ModConfigSpec.ConfigValue<String> WARNING_COLOR_NORMAL;
    public static final ModConfigSpec.ConfigValue<String> WARNING_COLOR_URGENT;
    public static final ModConfigSpec.ConfigValue<String> WARNING_COLOR_CRITICAL;
    public static final ModConfigSpec.ConfigValue<String> SUCCESS_COLOR;
    public static final ModConfigSpec.ConfigValue<String> ERROR_COLOR;
    
    // === 消息模板 ===
    public static final ModConfigSpec.ConfigValue<String> ERROR_CLEANUP_FAILED;
    public static final ModConfigSpec.ConfigValue<String> CMD_HELP_HEADER;
    public static final ModConfigSpec.ConfigValue<String> CMD_HELP_TEST;
    public static final ModConfigSpec.ConfigValue<String> CMD_HELP_OPEN;
    public static final ModConfigSpec.ConfigValue<String> CMD_HELP_CURRENT;
    public static final ModConfigSpec.ConfigValue<String> CMD_HELP_EXAMPLE;
    public static final ModConfigSpec.ConfigValue<String> TEST_BOX_TITLE;
    public static final ModConfigSpec.ConfigValue<String> TEST_BOX_OPENED;
    public static final ModConfigSpec.ConfigValue<String> ITEM_COUNT_DISPLAY;

    // === 性能优化缓存 ===
    // HashSet缓存，将O(n)查找优化为O(1)
    private static volatile Set<String> whitelistCache = new HashSet<>();
    private static volatile Set<String> blacklistCache = new HashSet<>();
    private static volatile Set<String> projectileTypesCache = new HashSet<>();
    
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
        
        // 物品过滤系统
        BUILDER.comment("Item filtering settings / 物品过滤设置").push("item_filter");
        
        CLEAN_MODE = BUILDER
                .comment("Item cleaning mode / 物品清理模式:",
                        "  - 'whitelist': Keep only items in whitelist, clean everything else / 白名单模式：仅保留白名单中的物品，清理其它所有物品",
                        "  - 'blacklist': Clean only items in blacklist, keep everything else / 黑名单模式：仅清理黑名单中的物品，保留其它所有物品",
                        "Default: whitelist")
                .translation("recycle.config.clean_mode")
                .defineInList("clean_mode", "whitelist", Arrays.asList("whitelist", "blacklist"));
        
        WHITELIST = BUILDER
                .comment("Items that will be kept (protected from cleaning) / 白名单：永远保留的物品",
                        "Default: [minecraft:diamond, minecraft:netherite_ingot, minecraft:elytra]")
                .translation("recycle.config.whitelist")
                .defineListAllowEmpty("whitelist",
                    List.of("minecraft:diamond", "minecraft:netherite_ingot", "minecraft:elytra"),
                    () -> "",
                    Config::validateResourceLocation);
        
        BLACKLIST = BUILDER
                .comment("Items that will be cleaned up / 黑名单：永远清理的物品",
                        "Default: [minecraft:cobblestone, minecraft:dirt, minecraft:gravel]")
                .translation("recycle.config.blacklist")
                .defineListAllowEmpty("blacklist", 
                    List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel"),
                    () -> "",
                    Config::validateResourceLocation);
        
        BUILDER.pop();

        // 弹射物清理设置
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
        
        // 扫描优化设置
        BUILDER.comment("Scanning optimization settings / 扫描优化设置").push("scanning_optimization");
        
        SCAN_MODE = BUILDER
                .comment("Scan Mode / 扫描模式:",
                        "  - 'chunk': Force-loaded Chunk Scan / 强加载区块扫描",
                        "  - 'player': Player Surrounding Scan / 玩家周围扫描",
                        "Default: player")
                .translation("recycle.config.scan_mode")
                .defineInList("scan_mode", "player", Arrays.asList("chunk", "player"));
        
        PLAYER_SCAN_RADIUS = BUILDER
                .comment("Chunk radius around players for optimized scanning / 玩家周围的区块扫描半径",
                        "Default: 8, Min: 2, Max: 32")
                .translation("recycle.config.player_scan_radius")
                .defineInRange("player_scan_radius", 8, 2, 32);
        
        BATCH_SIZE = BUILDER
                .comment("Batch size for processing (scanning, entity deletion, etc.) / 批处理大小（扫描、实体删除等通用）",
                        "Default: 100, Min: 50, Max: 500")
                .translation("recycle.config.batch_size")
                .defineInRange("batch_size", 100, 50, 500);
        
        BUILDER.pop();
        
        // 主线程调度优化设置
        BUILDER.comment("Main thread scheduling optimization / 主线程调度优化设置").push("main_thread_scheduling");
        
        MAX_PROCESSING_TIME_MS = BUILDER
                .comment("Maximum processing time per tick in milliseconds / 每tick最大主线程删除物品处理时间（毫秒）",
                        "Default: 2, Min: 1, Max: 10")
                .translation("recycle.config.max_processing_time_ms")
                .defineInRange("max_processing_time_ms", 2, 1, 10);
        
        BUILDER.pop();
        
        // UI界面设置
        BUILDER.comment("UI interface settings / UI界面设置").push("ui_settings");
        
        ITEM_STACK_MERGE_LIMIT = BUILDER
                .comment("Maximum stack size when merging items / 合并物品时的最大堆叠数量",
                        "Default: 6400, Min: 64, Max: 9999")
                .translation("recycle.config.item_stack_merge_limit")
                .defineInRange("item_stack_merge_limit", 6400, 64, 9999);
        
        BUILDER.pop();
        
        // 颜色配置
        BUILDER.comment("Color configuration for UI messages / UI消息颜色配置").push("colors");
        
        WARNING_COLOR_NORMAL = BUILDER
                .comment("Color for normal warnings (hex format #RRGGBB) / 普通警告颜色（十六进制格式#RRGGBB）",
                        "Default: #FFCC00")
                .translation("recycle.config.warning_color_normal")
                .define("warning_color_normal", "#FFCC00");
        
        WARNING_COLOR_URGENT = BUILDER
                .comment("Color for urgent warnings / 紧急警告颜色",
                        "Default: #FF6600")
                .translation("recycle.config.warning_color_urgent")
                .define("warning_color_urgent", "#FF6600");
        
        WARNING_COLOR_CRITICAL = BUILDER
                .comment("Color for critical warnings / 关键警告颜色",
                        "Default: #FF3300")
                .translation("recycle.config.warning_color_critical")
                .define("warning_color_critical", "#FF3300");
        
        SUCCESS_COLOR = BUILDER
                .comment("Color for success messages / 成功消息颜色",
                        "Default: #00FF00")
                .translation("recycle.config.success_color")
                .define("success_color", "#00FF00");
        
        ERROR_COLOR = BUILDER
                .comment("Color for error messages / 错误消息颜色",
                        "Default: #FF0000")
                .translation("recycle.config.error_color")
                .define("error_color", "#FF0000");
        
        BUILDER.pop();
        
        // 消息模板
        BUILDER.comment("Message templates for UI text / UI文本消息模板").push("messages");
        
        ERROR_CLEANUP_FAILED = BUILDER
                .comment("Message shown when cleanup fails / 清理失败时显示的消息",
                        "Default: §cCleanup failed")
                .translation("recycle.config.error_cleanup_failed")
                .define("error_cleanup_failed", "§cCleanup failed");
        
        CMD_HELP_HEADER = BUILDER
                .comment("Command help header / 命令帮助标题",
                        "Default: §6=== Trash Box Command Help ===")
                .translation("recycle.config.cmd_help_header")
                .define("cmd_help_header", "§6=== Trash Box Command Help ===");
        
        CMD_HELP_TEST = BUILDER
                .comment("Command help for test command / 测试命令帮助",
                        "Default: §e/bin test §7- Open test trash box")
                .translation("recycle.config.cmd_help_test")
                .define("cmd_help_test", "§e/bin test §7- Open test trash box");
        
        CMD_HELP_OPEN = BUILDER
                .comment("Command help for open command / 打开命令帮助",
                        "Default: §e/bin open <dimension> <box> §7- Open specific dimension trash box")
                .translation("recycle.config.cmd_help_open")
                .define("cmd_help_open", "§e/bin open <dimension> <box> §7- Open specific dimension trash box");
        
        CMD_HELP_CURRENT = BUILDER
                .comment("Command help for current command / 当前维度命令帮助",
                        "Default: §e/bin current <box> §7- Open current dimension trash box")
                .translation("recycle.config.cmd_help_current")
                .define("cmd_help_current", "§e/bin current <box> §7- Open current dimension trash box");
        
        CMD_HELP_EXAMPLE = BUILDER
                .comment("Command help example / 命令示例",
                        "Default: §7Example: §f/bin open minecraft:overworld 1")
                .translation("recycle.config.cmd_help_example")
                .define("cmd_help_example", "§7Example: §f/bin open minecraft:overworld 1");
        
        TEST_BOX_TITLE = BUILDER
                .comment("Test box title template (use {ui_type} for UI type) / 测试垃圾箱标题模板（使用{ui_type}显示UI类型）",
                        "Default: §6Test Trash Box §7(UI Type: {ui_type})")
                .translation("recycle.config.test_box_title")
                .define("test_box_title", "§6Test Trash Box §7(UI Type: {ui_type})");
        
        TEST_BOX_OPENED = BUILDER
                .comment("Message when test box is opened (use {ui_type} for UI type) / 打开测试垃圾箱时的消息",
                        "Default: §aTest trash box opened §7| UI Type: §b{ui_type}")
                .translation("recycle.config.test_box_opened")
                .define("test_box_opened", "§aTest trash box opened §7| UI Type: §b{ui_type}");
        
        ITEM_COUNT_DISPLAY = BUILDER
                .comment("Item count display template (use {count} for count) / 物品数量显示模板",
                        "Default: §7Available: §a{count} / §b{stack_limit}")
                .translation("recycle.config.item_count_display")
                .define("item_count_display", "§7Available: §a{count} / §b{stack_limit}");
        
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
        String id = (String) obj;
        return ErrorHandler.handleStaticOperation("validateResourceLocation", () -> {
            ResourceLocation.parse(id);
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
     * 获取跨维度访问付费数量
     */
    public static int getCrossDimensionCost() {
        return CROSS_DIMENSION_ACCESS_COST.get();
    }
    
    /**
     * 检查维度是否在支持列表中
     */
    public static boolean isDimensionSupported(String dimensionId) {
        return AUTO_CREATE_DIMENSION_TRASH.get() || SUPPORTED_DIMENSIONS.get().contains(dimensionId);
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

    // === 物品过滤便捷方法 ===
    
    /**
     * 获取清理模式
     */
    public static String getCleanMode() {
        return CLEAN_MODE.get();
    }
    
    /**
     * 检查是否为白名单模式
     */
    public static boolean isWhitelistMode() {
        return "whitelist".equals(getCleanMode());
    }
    
    /**
     * 检查是否为黑名单模式
     */
    public static boolean isBlacklistMode() {
        return "blacklist".equals(getCleanMode());
    }
    
    /**
     * 检查物品是否在白名单中（用于保留）
     */
    public static boolean isInWhitelist(String itemId) {
        return whitelistCache.contains(itemId);
    }
    
    /**
     * 检查物品是否在黑名单中（用于清理）
     */
    public static boolean isInBlacklist(String itemId) {
        return blacklistCache.contains(itemId);
    }

    // === 弹射物清理便捷方法 ===
    
    /**
     * 检查是否应该清理弹射物
     */
    public static boolean shouldCleanProjectiles() {
        return CLEAN_PROJECTILES.get();
    }
    
    /**
     * 检查弹射物类型是否应该清理
     */
    public static boolean isProjectileTypeToClean(String entityTypeId) {
        return projectileTypesCache.contains(entityTypeId);
    }

    // === 扫描优化便捷方法 ===
    
    /**
     * 获取扫描模式
     */
    public static String getScanMode() {
        return SCAN_MODE.get();
    }
    
    /**
     * 检查是否为区块扫描模式
     */
    public static boolean isChunkScanMode() {
        return "chunk".equals(getScanMode());
    }
    
    /**
     * 检查是否为玩家周围扫描模式
     */
    public static boolean isPlayerScanMode() {
        return "player".equals(getScanMode());
    }
    
    /**
     * 获取玩家扫描半径
     */
    public static int getPlayerScanRadius() {
        return PLAYER_SCAN_RADIUS.get();
    }
    
    /**
     * 获取批处理大小（适用于扫描、实体删除等所有批处理操作）
     */
    public static int getBatchSize() {
        return BATCH_SIZE.get();
    }
    
    /**
     * 获取主线程最大处理时间（纳秒）
     */
    public static long getMaxProcessingTimeNs() {
        return MAX_PROCESSING_TIME_MS.get() * 1_000_000L;
    }
    
    // === UI和颜色相关便捷方法 ===

    /**
     * 获取物品堆叠合并限制
     */
    public static int getItemStackMergeLimit() {
        return ITEM_STACK_MERGE_LIMIT.get();
    }
    
    /**
     * 解析十六进制颜色字符串为整数
     */
    public static int parseColor(String colorStr) {
        return ErrorHandler.handleStaticOperation(
            "parseColor_" + colorStr,
            () -> {
                if (colorStr.startsWith("#")) {
                    return Integer.parseInt(colorStr.substring(1), 16);
                }
                return Integer.parseInt(colorStr, 16);
            },
            0xFFFFFF // 默认白色
        );
    }
    
    /**
     * 获取不同类型警告的颜色
     */
    public static int getWarningColor(int remainingSeconds) {
        if (remainingSeconds > 10) {
            return parseColor(WARNING_COLOR_NORMAL.get());
        } else if (remainingSeconds > 5) {
            return parseColor(WARNING_COLOR_URGENT.get());
        } else {
            return parseColor(WARNING_COLOR_CRITICAL.get());
        }
    }
    
    /**
     * 获取成功消息颜色
     */
    public static int getSuccessColor() {
        return parseColor(SUCCESS_COLOR.get());
    }
    
    /**
     * 获取错误消息颜色
     */
    public static int getErrorColor() {
        return parseColor(ERROR_COLOR.get());
    }
    
    // === 消息格式化方法 ===
    
    /**
     * 获取清理失败消息
     */
    public static String getCleanupFailedMessage() {
        return ERROR_CLEANUP_FAILED.get();
    }
    
    /**
     * 获取格式化的测试垃圾箱标题
     */
    public static String getTestBoxTitle(String uiType) {
        return TEST_BOX_TITLE.get().replace("{ui_type}", uiType);
    }
    
    /**
     * 获取格式化的测试垃圾箱打开消息
     */
    public static String getTestBoxOpenedMessage(String uiType) {
        return TEST_BOX_OPENED.get().replace("{ui_type}", uiType);
    }
    
    /**
     * 获取格式化的物品数量显示
     */
    public static String getItemCountDisplay(int count) {
        return ITEM_COUNT_DISPLAY.get().replace("{count}", String.valueOf(count)).replace("{stack_limit}", String.valueOf(getItemStackMergeLimit()));
    }
    
    /**
     * 获取命令帮助消息组
     */
    public static String[] getCommandHelpMessages() {
        return new String[] {
            CMD_HELP_HEADER.get(),
            CMD_HELP_TEST.get(),
            CMD_HELP_OPEN.get(),
            CMD_HELP_CURRENT.get(),
            CMD_HELP_EXAMPLE.get()
        };
    }
    
    /**
     * 获取弹射物实体类型集合（用于优化扫描）
     */
    public static Set<EntityType<?>> getProjectileTypes() {
        Set<EntityType<?>> entityTypes = new HashSet<>();
        
        for (String entityTypeId : projectileTypesCache) {
            ErrorHandler.handleVoidOperation(
                "parseProjectileType_" + entityTypeId,
                () -> {
                    ResourceLocation resourceLocation = ResourceLocation.parse(entityTypeId);
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(resourceLocation);
                    entityTypes.add(entityType);
                }
            );
        }
        
        return entityTypes;
    }
    
    // === 性能优化方法 ===
    
    /**
     * 更新HashSet缓存（配置重载时调用）
     */
    public static void updateCaches() {
        // 更新过滤物品缓存
        whitelistCache = new HashSet<>(WHITELIST.get());
        blacklistCache = new HashSet<>(BLACKLIST.get());
        projectileTypesCache = new HashSet<>(PROJECTILE_TYPES_TO_CLEAN.get());
    }
}