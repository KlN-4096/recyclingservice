package com.klnon.recyclingservice;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.klnon.recyclingservice.util.core.ErrorHandler;
import java.util.HashSet;

public class Config {
    // 配置构建器
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    // 配置规范
    public static final ModConfigSpec SPEC;
    
    // 维度倍数缓存（用于避免重复解析配置）
    private static final Map<String, Double> dimensionMultiplierCache = new ConcurrentHashMap<>();
    
    // === 基础清理设置 ===
    public static final ModConfigSpec.IntValue AUTO_CLEAN_TIME;
    public static final ModConfigSpec.BooleanValue SHOW_CLEANUP_WARNINGS;
    
    // === 警告消息设置 ===
    public static final ModConfigSpec.ConfigValue<String> WARNING_MESSAGE;
    public static final ModConfigSpec.IntValue WARNING_COUNTDOWN_START;
    
    // === 详细清理消息模板 ===
    public static final ModConfigSpec.ConfigValue<String> CLEANUP_RESULT_HEADER;
    public static final ModConfigSpec.ConfigValue<String> DIMENSION_ENTRY_FORMAT;
    
    // === 垃圾箱设置 ===
    public static final ModConfigSpec.IntValue TRASH_BOX_ROWS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_TRASH_ALLOW_PUT_IN;
    public static final ModConfigSpec.BooleanValue DIMENSION_TRASH_CROSS_ACCESS;
    
    // === 维度管理 ===
    public static final ModConfigSpec.IntValue MAX_BOXES_PER_DIMENSION;
    
    // === 付费系统 ===
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ITEM_TYPE;
    public static final ModConfigSpec.IntValue CROSS_DIMENSION_ACCESS_COST;
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_MODE;
    public static final ModConfigSpec.BooleanValue SAME_DIMENSION_PAYMENT_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ERROR_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_SUCCESS_MESSAGE;
    
    // === 物品过滤 ===
    public static final ModConfigSpec.ConfigValue<String> CLEAN_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    
    // === 弹射物过滤 ===
    public static final ModConfigSpec.BooleanValue CLEAN_PROJECTILES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_TYPES_TO_CLEAN;
    
    // === Create模组兼容性 ===
    public static final ModConfigSpec.BooleanValue PROTECT_CREATE_PROCESSING_ITEMS;
    
    // === 区域管理 ===
    public static final ModConfigSpec.BooleanValue ENABLE_CHUNK_ITEM_WARNING;
    public static final ModConfigSpec.BooleanValue ENABLE_CHUNK_FREEZING;
    public static final ModConfigSpec.IntValue TOO_MANY_ITEMS_WARNING;
    public static final ModConfigSpec.ConfigValue<String> TOO_MANY_ITEMS_WARNING_MESSAGE;
    public static final ModConfigSpec.IntValue CHUNK_FREEZING_SEARCH_RADIUS;
    
    // === 扫描优化设置 ===
    public static final ModConfigSpec.ConfigValue<String> SCAN_MODE;
    public static final ModConfigSpec.IntValue PLAYER_SCAN_RADIUS;
    public static final ModConfigSpec.IntValue BATCH_SIZE;
    public static final ModConfigSpec.ConfigValue<String> CHUNK_LOADING_MODE;
    
    // === 主线程调度优化设置 ===
    public static final ModConfigSpec.IntValue MAX_PROCESSING_TIME_MS;
    
    // === UI界面设置 ===
    public static final ModConfigSpec.IntValue ITEM_STACK_MULTIPLIER;
    
    // === 调试设置 ===
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGS;
    
    
    // === 消息模板 ===
    public static final ModConfigSpec.ConfigValue<String> ERROR_CLEANUP_FAILED;
    public static final ModConfigSpec.ConfigValue<String> MANUAL_CLEANUP_START;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CMD_HELP_MESSAGES;

    // === 性能优化缓存 ===
    // HashSet缓存，将O(n)查找优化为O(1)
    private static volatile Set<String> whitelistCache = new HashSet<>();
    private static volatile Set<String> blacklistCache = new HashSet<>();
    private static volatile Set<String> projectileTypesCache = new HashSet<>();
    
    
    // 允许放入物品的维度缓存
    private static volatile Set<String> allowPutInDimensionsCache = new HashSet<>();
    
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

        WARNING_COUNTDOWN_START = BUILDER
                .comment("Start countdown warnings when remaining time reaches this many seconds / 剩余时间达到多少秒时开始倒计时警告",
                        "Set to 0 to completely disable countdown warnings / 设为0完全禁用倒计时警告",
                        "Default: 30, Min: 0, Max: 300")
                .translation("recycle.config.warning_countdown_start")
                .defineInRange("warning_countdown_start", 30, 0, 300);

        // 详细清理消息模板配置
        CLEANUP_RESULT_HEADER = BUILDER
                .comment("Header text for detailed cleanup results / 详细清理结果的标题文本",
                        "Default: > §a§lCleanup results:")
                .translation("recycle.config.cleanup_result_header")
                .define("cleanup_result_header", "> §a§lCleanup results:");
        
        DIMENSION_ENTRY_FORMAT = BUILDER
                .comment("Format for each dimension entry in cleanup message / 清理消息中每个维度条目的格式",
                        "Available placeholders: {name} {items} {entities}",
                        "可用占位符: {name} {items} {entities}",
                        "Note: A clickable button '[打开垃圾箱]' will be automatically added after each entry",
                        "注意：每个条目后会自动添加可点击的'[打开垃圾箱]'按钮",
                        "Default: §f{name}: §b{items} §fitems, §d{entities} §fentities")
                .translation("recycle.config.dimension_entry_format")
                .define("dimension_entry_format", "§f{name}: §b{items} §fitems, §d{entities} §fentities");
        
        BUILDER.pop();
        
        // 垃圾箱设置
        BUILDER.comment("Trash box settings / 垃圾箱系统设置").push("trash_box");

        TRASH_BOX_ROWS = BUILDER
                .comment("Number of rows in each trash box / 每个垃圾箱的行数",
                        "Default: 6, Min: 1, Max: 6")
                .translation("recycle.config.trash_box_rows")
                .defineInRange("trash_box_rows", 6, 1, 6);
        
        DIMENSION_TRASH_ALLOW_PUT_IN = BUILDER
                .comment("Dimensions that allow players to put items into trash boxes / 允许玩家主动将物品放入垃圾箱的维度",
                        "Default: Main 3 dimensions / 默认：主要3个维度")
                .translation("recycle.config.dimension_trash_allow_put_in")
                .defineListAllowEmpty("dimension_trash_allow_put_in",
                    List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    () -> "",
                    Config::validateResourceLocation);

        //是否允许玩家访问所处维度垃圾箱
        DIMENSION_TRASH_CROSS_ACCESS = BUILDER
                .comment("Allow players to access trash boxes from other dimensions / 允许玩家跨维度访问垃圾箱",
                        "When false, players can only access trash boxes in their current dimension, ignoring DIMENSION_TRASH_ALLOW_PUT_IN / 为false时，玩家只能访问当前维度的垃圾箱，忽略DIMENSION_TRASH_ALLOW_PUT_IN配置",
                        "Default: true / 默认：true")
                .translation("recycle.config.dimension_trash_cross_access")
                .define("dimension_trash_cross_access", true);
        
        BUILDER.pop();
        
        // 维度管理设置
        BUILDER.comment("Dimension management settings / 维度管理设置").push("dimension_management");
        
        MAX_BOXES_PER_DIMENSION = BUILDER
                .comment("Maximum number of trash boxes per dimension / 每个维度最大垃圾箱数量",
                        "Default: 3, Min: 1, Max: 5")
                .translation("recycle.config.max_boxes_per_dimension")
                .defineInRange("max_boxes_per_dimension", 3, 1, 5);

        
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
        
        PAYMENT_MODE = BUILDER
                .comment("When to charge payment / 何时收取邮费:",
                        "  - 'enabled': Pay when putting items into trash box / 放入垃圾箱时付费",
                        "  - 'none': Disable this function / 禁用邮费系统",
                        "Default: enabled")
                .translation("recycle.config.payment_mode")
                .defineInList("payment_mode", "enabled", Arrays.asList("enabled", "none"));
        
        SAME_DIMENSION_PAYMENT_ENABLED = BUILDER
                .comment("Whether same dimension access requires payment / 是否同维度访问需要邮费",
                        "true: Same dimension access costs base_cost / true: 同维度访问需要基础邮费",
                        "false: Same dimension access is free / false: 同维度访问免费",
                        "Default: true")
                .translation("recycle.config.same_dimension_payment_enabled")
                .define("same_dimension_payment_enabled", true);
        
DIMENSION_MULTIPLIERS = BUILDER
                .comment("Cost multipliers for each dimension / 各维度邮费倍数",
                        "Format: \"dimension_id:multiplier\" / 格式：\"维度ID:倍数\"",
                        "Example: minecraft:overworld:1.0,minecraft:the_nether:1.5,minecraft:the_end:2.0",
                        "If dimension not configured, uses default 1.0 / 未配置的维度使用默认值1.0",
                        "NOTE: Multiplier only applies when player is in DIFFERENT dimension / 注意：倍数仅在玩家位于不同维度时生效")
                .translation("recycle.config.dimension_multipliers")
                .defineListAllowEmpty("dimension_multipliers", 
                        List.of("minecraft:overworld:1.5", "minecraft:the_nether:1.5", "minecraft:the_end:2.0"), 
                        () -> "minecraft:overworld:1.5",
                        obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_]+:[a-z0-9_]+:[0-9]+(\\.[0-9]+)?$"));
        
        // 邮费消息模板
        PAYMENT_ERROR_MESSAGE = BUILDER
                .comment("Message shown when player doesn't have enough payment items / 玩家邮费物品不足时显示的消息",
                        "Placeholders: {cost} = required amount, {item} = item name / 占位符：{cost} = 需要数量，{item} = 物品名称",
                        "Default: §cNeed {cost} {item} as postage!")
                .translation("recycle.config.payment_error_message")
                .define("payment_error_message", "§cNeed {cost} {item} as postage!");
        
        PAYMENT_SUCCESS_MESSAGE = BUILDER
                .comment("Message shown when payment is successfully deducted / 成功扣除邮费时显示的消息",
                        "Placeholders: {cost} = deducted amount, {item} = item name / 占位符：{cost} = 扣除数量，{item} = 物品名称",
                        "Default: §aDeducted {cost} {item} as postage")
                .translation("recycle.config.payment_success_message")
                .define("payment_success_message", "§aDeducted {cost} {item} as postage");
        
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
        
        // Create模组兼容性设置
        BUILDER.comment("Create mod compatibility settings / Create模组兼容性设置").push("create_mod_compatibility");
        
        PROTECT_CREATE_PROCESSING_ITEMS = BUILDER
                .comment("Protect items being processed by Create mod fans / 保护正在被Create模组鼓风机处理的物品",
                        "When enabled, items with Create processing NBT data will not be cleaned up",
                        "启用后，带有Create处理NBT数据的物品不会被清理",
                        "Default: true")
                .translation("recycle.config.protect_create_processing_items")
                .define("protect_create_processing_items", true);
        
        BUILDER.pop();
        
        // 区块管理
        BUILDER.comment("Chunk management settings / 区块管理设置").push("chunk_management");
        
        ENABLE_CHUNK_ITEM_WARNING = BUILDER
                .comment("Enable warnings when chunks have too many items / 启用区块物品过多警告功能",
                        "Default: true")
                .translation("recycle.config.enable_chunk_item_warning")
                .define("enable_chunk_item_warning", true);
        
        ENABLE_CHUNK_FREEZING = BUILDER
                .comment("Enable automatic chunk freezing when too many items are detected / 启用检测到大量物品时自动冻结区块功能",
                        "When enabled, chunks with excessive items will be automatically frozen to improve server performance",
                        "启用后，物品过多的区块将自动冻结以提升服务器性能",
                        "Default: true")
                .translation("recycle.config.enable_chunk_freezing")
                .define("enable_chunk_freezing", true);
        
        TOO_MANY_ITEMS_WARNING = BUILDER
                .comment("Warn when a chunk has more than this many items / 当区块中物品超过此数量时发出警告",
                        "Default: 50, Min: 5, Max: 10000")
                .translation("recycle.config.too_many_items_warning")
                .defineInRange("too_many_items_warning_limit", 50, 5, 10000);
        
        TOO_MANY_ITEMS_WARNING_MESSAGE = BUILDER
                .comment("Warning message for too many items (use {count} for item count, {x} {z} for world coordinates, {ticket} for ticket level) / 物品过多警告消息（使用{count}显示物品数量，{x} {z}显示世界坐标，{ticket}显示票据级别）",
                        "Default: §e[Items Warning] Found {count} items at ({x}, {z}) ticketLevel:{ticket}")
                .translation("recycle.config.too_many_items_warning_message")
                .define("too_many_items_warning_message", "§e[Items Warning] Found {count} items at ({x}, {z}) ticketLevel:{ticket}");
        
        CHUNK_FREEZING_SEARCH_RADIUS = BUILDER
                .comment("Search radius for chunk loader freezing / 区块加载器冻结搜索半径",
                        "Determines the search area when looking for chunk loaders that affect chunks with excessive items",
                        "决定寻找影响物品过多区块的区块加载器的搜索范围",
                        "Default: 8, Min: 2, Max: 16")
                .translation("recycle.config.chunk_freezing_search_radius")
                .defineInRange("chunk_freezing_search_radius", 8, 2, 16);
        
        BUILDER.pop();
        
        // 扫描优化设置
        BUILDER.comment("Scanning optimization settings / 扫描优化设置").push("scanning_optimization");
        
        SCAN_MODE = BUILDER
                .comment("Scan Mode / 扫描模式:",
                        "  - 'chunk': loaded Chunk Scan / 加载区块扫描",
                        "  - 'player': Player Surrounding Scan / 玩家周围扫描",
                        "Default: chunk")
                .translation("recycle.config.scan_mode")
                .defineInList("scan_mode", "chunk", Arrays.asList("chunk", "player"));

        // 区块加载级别模式
        CHUNK_LOADING_MODE = BUILDER
                .comment("Chunk loading level for scanning / 加载区块扫描级别:",
                        "  - 'force': Only force-loaded chunks (ticket level <= 31) / 仅强加载区块（票据级别 <= 31）",
                        "  - 'lazy': Include force-loaded and player chunks (ticket level <= 32) / 包含强加载和弱加载区块（票据级别 <= 32）",
                        "Default: lazy")
                .translation("recycle.config.chunk_loading_mode")
                .defineInList("chunk_loading_mode", "lazy", Arrays.asList("force", "lazy"));
        
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

        ITEM_STACK_MULTIPLIER = BUILDER
                .comment("Stack size multiplier for items / 物品堆叠倍数",
                        "Default: 100 (means 64*100=6400), Min: 1, Max: 1000")
                .translation("recycle.config.item_stack_multiplier")
                .defineInRange("item_stack_multiplier", 100, 1, 1000);
        
        BUILDER.pop();
        
        // 调试设置
        BUILDER.comment("Debug settings / 调试设置").push("debug");
        
        ENABLE_DEBUG_LOGS = BUILDER
                .comment("Enable debug logging for ErrorHandler operations / 启用ErrorHandler操作的调试日志",
                        "When disabled, reduces log spam from routine operations / 禁用时减少常规操作的日志输出",
                        "Default: false")
                .translation("recycle.config.enable_debug_logs")
                .define("enable_debug_logs", false);
        
        BUILDER.pop();
        
        
        // 消息模板
        BUILDER.comment("Message templates for UI text / UI文本消息模板").push("messages");
        
        ERROR_CLEANUP_FAILED = BUILDER
                .comment("Message shown when cleanup fails / 清理失败时显示的消息",
                        "Default: §cCleanup failed")
                .translation("recycle.config.error_cleanup_failed")
                .define("error_cleanup_failed", "§cCleanup failed");
        
        MANUAL_CLEANUP_START = BUILDER
                .comment("Message shown when manual cleanup starts / 手动清理开始时显示的消息",
                        "Default: §6[Manual Cleanup] Starting cleanup...")
                .translation("recycle.config.manual_cleanup_start")
                .define("manual_cleanup_start", "§6[Manual Cleanup] Starting cleanup...");
        
        CMD_HELP_MESSAGES = BUILDER
                .comment("Command help messages / 命令帮助消息",
                        "Format: One message per line / 格式：每行一条消息",
                        "Default help messages / 默认帮助消息")
                .translation("recycle.config.cmd_help_messages")
                .defineListAllowEmpty("cmd_help_messages",
                    List.of(
                        "§6=== Trash Box Command Help ===",
                        "§e/bin test §7- Open test trash box",
                        "§e/bin open <dimension> <box> §7- Open specific dimension trash box",
                        "§e/bin current <box> §7- Open current dimension trash box",
                        "§e/bin cleanup §7- Manually trigger cleanup",
                        "§e/bin tickets <x> <z> §7- Show chunk tickets info",
                        "§7Example: §f/bin open minecraft:overworld 1"
                    ),
                    () -> "",
                    obj -> obj instanceof String);
        
        
        BUILDER.pop();
        
        // 构建配置规范
        SPEC = BUILDER.build();
    }
    
    /**
     * 验证资源ID格式是否正确
     */
    private static boolean validateResourceLocation(Object obj) {
        if (!(obj instanceof String id)) {
            return false;
        }
        try {
            ResourceLocation.parse(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    // === 便捷访问方法 ===

    public static int getMaxBoxes(){
        return MAX_BOXES_PER_DIMENSION.get();
    }

    public static MenuType<ChestMenu> getMenuTypeForRows() {
        return switch(getTrashBoxRows()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }


    public static int getTrashBoxRows(){
        return TRASH_BOX_ROWS.get();
    }
    
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
     * 获取邮费模式
     */
    public static String getPaymentMode() {
        return PAYMENT_MODE.get();
    }
    
    /**
     * 检查是否启用同维度邮费
     */
    public static boolean isSameDimensionPaymentEnabled() {
        return SAME_DIMENSION_PAYMENT_ENABLED.get();
    }
    
    /**
     * 获取指定维度的邮费倍数
     * @param dimensionId 维度ID（如 "minecraft:overworld"）
     * @return 该维度的邮费倍数，未配置则返回1.0
     */
    public static double getDimensionMultiplier(String dimensionId) {
        return dimensionMultiplierCache.getOrDefault(dimensionId, 1.0);
    }
    
    /**
     * 解析维度倍数配置并更新缓存
     */
    private static void parseDimensionMultipliers() {
        dimensionMultiplierCache.clear();
        
        List<? extends String> configList = DIMENSION_MULTIPLIERS.get();
        for (String entry : configList) {
            try {
                String[] parts = entry.split(":");
                if (parts.length == 3) {
                    String namespace = parts[0];
                    String path = parts[1];
                    double multiplier = Double.parseDouble(parts[2]);
                    String dimensionId = namespace + ":" + path;
                    dimensionMultiplierCache.put(dimensionId, multiplier);
                }
            } catch (NumberFormatException e) {
                ErrorHandler.handleVoidOperation("parseDimensionMultiplier", 
                    () -> {}); // 处理解析错误
            }
        }
    }
    
    /**
     * 获取邮费不足错误消息模板
     */
    public static String getPaymentErrorMessage() {
        return PAYMENT_ERROR_MESSAGE.get();
    }
    
    /**
     * 获取邮费扣除成功消息模板
     */
    public static String getPaymentSuccessMessage() {
        return PAYMENT_SUCCESS_MESSAGE.get();
    }
    
    /**
     * 计算邮费数量（仅用于insert操作）
     * @param playerDim 玩家所在维度
     * @param trashDim 垃圾箱所在维度
     * @return 需要支付的邮费数量，0表示免费
     */
    public static int calculatePaymentCost(ResourceLocation playerDim, ResourceLocation trashDim) {
        // 只有在enabled模式下才收费
        String mode = getPaymentMode();
        if (!mode.equals("enabled")) {
            return 0;
        }
        
        int baseCost = getCrossDimensionCost();
        
        // 同维度和跨维度分别处理
        if (playerDim.equals(trashDim)) {
            // 同维度访问：根据开关决定是否收费
            return isSameDimensionPaymentEnabled() ? baseCost : 0;
        } else {
            // 跨维度访问：使用目标维度（垃圾箱所在维度）的倍数
            double multiplier = getDimensionMultiplier(trashDim.toString());
            return (int) Math.ceil(baseCost * multiplier);
        }
    }
    
    /**
     * 检查维度是否允许玩家主动放入物品到垃圾箱
     */
    public static boolean isDimensionAllowPutIn(String dimensionId) {
        return allowPutInDimensionsCache.contains(dimensionId);
    }
    
    /**
     * 获取格式化的警告消息
     */
    public static String getWarningMessage(int remainingSeconds) {
        return WARNING_MESSAGE.get().replace("{time}", String.valueOf(remainingSeconds));
    }
    
    /**
     * 获取倒计时开始时间
     */
    public static int getCountdownStartTime() {
        return WARNING_COUNTDOWN_START.get();
    }

    /**
     * 检查是否应该显示倒计时
     * @param remainingSeconds 剩余秒数
     * @return 是否显示倒计时
     */
    public static boolean shouldShowCountdown(int remainingSeconds) {
        int startTime = getCountdownStartTime();
        return remainingSeconds <= startTime && remainingSeconds > 0;
    }

    // === 物品过滤便捷方法 ===
    
    /**
     * 获取清理模式
     */
    public static String getCleanMode() {
        return CLEAN_MODE.get();
    }
    
    /**
     * 检查是否为白名单模式,反之则是黑名单
     */
    public static boolean isWhitelistMode() {
        return "whitelist".equals(getCleanMode());
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

    // === Create模组兼容性便捷方法 ===
    
    /**
     * 检查是否保护正在被Create模组处理的物品
     */
    public static boolean shouldProtectCreateProcessingItems() {
        return PROTECT_CREATE_PROCESSING_ITEMS.get();
    }

    // === 扫描优化便捷方法 ===
    
    /**
     * 获取扫描模式
     */
    public static String getScanMode() {
        return SCAN_MODE.get();
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
     * 根据配置获取票据级别阈值
     * @return 31 (仅强制加载) 或 32 (包含弱加载)
     */
    public static int getTicketLevelThreshold() {
        return "force".equals(CHUNK_LOADING_MODE.get()) ? 31 : 32;
    }
    
    /**
     * 获取主线程最大处理时间（纳秒）
     */
    public static long getMaxProcessingTimeNs() {
        return MAX_PROCESSING_TIME_MS.get() * 1_000_000L;
    }
    
    /**
     * 检查是否启用区块物品警告
     */
    public static boolean isChunkWarningEnabled() {
        return ENABLE_CHUNK_ITEM_WARNING.get();
    }
    
    /**
     * 检查是否启用区块冻结功能
     */
    public static boolean isChunkFreezingEnabled() {
        return ENABLE_CHUNK_FREEZING.get();
    }
    
    /**
     * 获取区块冻结搜索半径
     */
    public static int getChunkFreezingSearchRadius() {
        return CHUNK_FREEZING_SEARCH_RADIUS.get();
    }
    
    /**
     * 获取格式化的物品过多警告消息（支持点击传送）
     */
    public static Component getItemWarningMessage(int itemCount, int worldX, int worldZ, int ticketLevel) {
        String message = TOO_MANY_ITEMS_WARNING_MESSAGE.get()
                .replace("{count}", String.valueOf(itemCount))
                .replace("{x}", String.valueOf(worldX))
                .replace("{z}", String.valueOf(worldZ))
                .replace("{ticket}", String.valueOf(ticketLevel));
        
        return Component.literal(message)
                .withStyle(style -> style
                    .withColor(net.minecraft.ChatFormatting.YELLOW)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/tp @s " + worldX + " ~ " + worldZ))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal("§7Click to teleport (OP required)\n§7Coordinate: " + worldX + ", " + worldZ + "\n§7Ticket Level: " + ticketLevel)))
                );
    }
    
    // === UI和颜色相关便捷方法 ===

    /**
     * 获取物品堆叠合并限制
     */
    public static int getItemStackMultiplier(ItemStack itemStack) {
        return ITEM_STACK_MULTIPLIER.get()*itemStack.getMaxStackSize();
    }
    
    
    
    
    
    
    
    // === 消息格式化方法 ===
    
    /**
     * 获取清理失败消息
     */
    public static String getCleanupFailedMessage() {
        return ERROR_CLEANUP_FAILED.get();
    }
    
    /**
     * 获取手动清理开始消息
     */
    public static String getManualCleanupStartMessage() {
        return MANUAL_CLEANUP_START.get();
    }
    
    /**
     * 获取格式化的测试垃圾箱标题
     */
    public static String getTestBoxTitle(String uiType) {
        return "§6Test Trash Box §7(UI Type: " + uiType + ")";
    }
    
    /**
     * 获取格式化的测试垃圾箱打开消息
     */
    public static String getTestBoxOpenedMessage(String uiType) {
        return "§aTest trash box opened §7| UI Type: §b" + uiType;
    }
    
    /**
     * 获取格式化的物品数量显示
     */
    public static String getItemCountDisplay(int count,ItemStack itemStack) {
        return "§7Available: §a" + count + " / §b" + getItemStackMultiplier(itemStack);
    }
    
    /**
     * 获取命令帮助消息组
     */
    public static String[] getCommandHelpMessages() {
        List<? extends String> messages = CMD_HELP_MESSAGES.get();
        return messages.toArray(new String[0]);
    }
    
    // === 维度相关便捷方法 ===
    
    
    
    
    /**
     * 获取维度的显示名称 - 简化版，直接去前缀
     * @param dimensionId 维度ID
     * @return 去前缀后的维度名称
     */
    public static String getDimensionDisplayName(ResourceLocation dimensionId) {
        String dimString = dimensionId.toString();
        return dimString.contains(":") ? 
            dimString.substring(dimString.indexOf(':') + 1) : dimString;
    }
    
    
    
    /**
     * 构建详细清理完成消息（带tooltip和可点击按钮的Component格式）
     * @param dimensionStats 各维度清理统计信息
     * @return 带悬停详情和可点击按钮的聊天组件
     */
    public static Component getDetailedCleanupMessage(Map<ResourceLocation, ?> dimensionStats) {
        MutableComponent mainComponent = Component.literal(CLEANUP_RESULT_HEADER.get());
        List<Component> tooltipComponents = new ArrayList<>();
        
        // 主要维度集合
        Set<String> mainDimensions = Set.of(
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
        );
        
        // 统一处理所有维度
        for (Map.Entry<ResourceLocation, ?> entry : dimensionStats.entrySet()) {
            Component dimensionEntry = formatDimensionEntry(entry.getKey(), entry.getValue());
            if (dimensionEntry != null) {
                if (mainDimensions.contains(entry.getKey().toString())) {
                    // 主要维度显示在主消息中（添加换行）
                    mainComponent = mainComponent.append(Component.literal("\n")).append(dimensionEntry);
                } else {
                    // 其他维度显示在tooltip中
                    tooltipComponents.add(dimensionEntry);
                }
            }
        }
        
        // 添加tooltip悬停事件
        if (!tooltipComponents.isEmpty()) {
            // 构建tooltip内容，每个组件用换行分隔
            MutableComponent tooltipContent = Component.empty();
            for (int i = 0; i < tooltipComponents.size(); i++) {
                if (i > 0) {
                    tooltipContent = tooltipContent.append(Component.literal("\n"));
                }
                tooltipContent = tooltipContent.append(tooltipComponents.get(i));
            }
            
            // 使用final变量避免lambda问题
            final MutableComponent finalTooltipContent = tooltipContent;
            mainComponent = mainComponent.withStyle(style -> 
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, finalTooltipContent))
            );
        }
        
        return mainComponent;
    }
    
    /**
     * 格式化单个维度的清理条目，包含可点击的打开垃圾箱按钮
     * @param dimensionId 维度ID
     * @param stats 统计数据对象
     * @return 格式化后的条目Component，如果获取失败返回null
     */
    private static Component formatDimensionEntry(ResourceLocation dimensionId, Object stats) {
        try {
            // 直接转换为DimensionCleanupStats记录类
            if (stats instanceof com.klnon.recyclingservice.service.CleanupService.DimensionCleanupStats dimensionStats) {
                // 创建基础文本
                String baseText = DIMENSION_ENTRY_FORMAT.get()
                        .replace("{name}", getDimensionDisplayName(dimensionId))
                        .replace("{items}", String.valueOf(dimensionStats.itemsCleaned()))
                        .replace("{entities}", String.valueOf(dimensionStats.projectilesCleaned()));
                
                // 创建可点击的按钮
                MutableComponent button = Component.literal(" [打开垃圾箱]")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.GREEN)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                        "/bin open " + dimensionId + " 1"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("点击打开 " + getDimensionDisplayName(dimensionId) + " 的1号垃圾箱")
                                                .withStyle(ChatFormatting.YELLOW))));
                
                // 组合文本和按钮
                return Component.literal(baseText).append(button);
            }
            return null;
        } catch (Exception e) {
            // 静默处理转换异常
            return null;
        }
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
        
        // 解析维度倍数配置
        parseDimensionMultipliers();
        
        // 更新允许放入物品的维度缓存
        allowPutInDimensionsCache = new HashSet<>(DIMENSION_TRASH_ALLOW_PUT_IN.get());
    }
    
    /**
     * 检查是否启用调试日志
     */
    public static boolean isDebugLogsEnabled() {
        return ENABLE_DEBUG_LOGS.get();
    }
}