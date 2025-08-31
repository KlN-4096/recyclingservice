package com.klnon.recyclingservice;

import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

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
    public static final ModConfigSpec.IntValue WARNING_COUNTDOWN_START;
    
    // === 垃圾箱设置 ===
    public static final ModConfigSpec.IntValue TRASH_BOX_ROWS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_TRASH_ALLOW_PUT_IN;
    public static final ModConfigSpec.BooleanValue DIMENSION_TRASH_CROSS_ACCESS;
    
    // === 维度管理 ===
    public static final ModConfigSpec.IntValue MAX_BOXES_PER_DIMENSION;
    
    // === 付费系统 ===
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ITEM_TYPE;
    public static final ModConfigSpec.IntValue CROSS_DIMENSION_ACCESS_COST;
    public static final ModConfigSpec.ConfigValue<String> INSERT_PAYMENT_MODE;
    public static final ModConfigSpec.ConfigValue<String> EXTRACT_PAYMENT_MODE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_MULTIPLIERS;
    
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
    public static final ModConfigSpec.IntValue CHUNK_FREEZING_SEARCH_RADIUS;


    // === 主线程调度优化设置 ===
    public static final ModConfigSpec.IntValue MAX_PROCESSING_TIME_MS;
    public static final ModConfigSpec.IntValue BATCH_SIZE;
    
    // === UI界面设置 ===
    public static final ModConfigSpec.IntValue ITEM_STACK_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> ITEM_COUNT_DISPLAY_FORMAT;
    
    // === 消息模板 ===
    
    // === 清理结果消息 ===
    public static final ModConfigSpec.ConfigValue<String> CLEANUP_RESULT_HEADER;
    public static final ModConfigSpec.ConfigValue<String> DIMENSION_ENTRY_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> ERROR_CLEANUP_FAILED;
    public static final ModConfigSpec.ConfigValue<String> MANUAL_CLEANUP_START;
    
    // === 邮费系统消息 ===
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ERROR_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_SUCCESS_MESSAGE;
    
    // === 警告消息 ===
    public static final ModConfigSpec.ConfigValue<String> WARNING_MESSAGE;
    public static final ModConfigSpec.ConfigValue<String> TOO_MANY_ITEMS_WARNING_MESSAGE;
    
    // === 命令系统消息 ===
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CMD_HELP_MESSAGES;
    
    // === UI界面消息 ===
    public static final ModConfigSpec.ConfigValue<String> TRASH_BOX_BUTTON_TEXT;
    public static final ModConfigSpec.ConfigValue<String> TRASH_BOX_BUTTON_HOVER;
    
    // === 调试设置 ===
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGS;

    // === 性能优化缓存 ===
    // HashSet缓存，将O(n)查找优化为O(1)
    public static volatile Set<String> whitelistCache = new HashSet<>();
    public static volatile Set<String> blacklistCache = new HashSet<>();
    public static volatile Set<String> projectileTypesCache = new HashSet<>();
    
    
    // 允许放入物品的维度缓存
    private static volatile Set<String> allowPutInDimensionsCache = new HashSet<>();
    
    static {
        // 基础清理设置
        BUILDER.comment("Auto cleanup settings / 自动清理设置").push("auto_cleanup");
        
        AUTO_CLEAN_TIME = BUILDER
                .comment("Auto cleanup interval in seconds (Default: 600, Min: 30, Max: 7200)")
                .translation("recycle.config.auto_clean_time")
                .defineInRange("auto_clean_time_seconds", 600, 30, 7200);
        
        SHOW_CLEANUP_WARNINGS = BUILDER
                .comment("Show warning messages before cleanup (Default: true)")
                .translation("recycle.config.show_cleanup_warnings")
                .define("show_cleanup_warnings", true);

        WARNING_COUNTDOWN_START = BUILDER
                .comment("Start countdown warnings at remaining seconds (Default: 15, 0=disabled)")
                .translation("recycle.config.warning_countdown_start")
                .defineInRange("warning_countdown_start", 15, 0, 300);
        
        BUILDER.pop();
    
        
        // 垃圾箱设置
        BUILDER.comment("Trash box settings / 垃圾箱系统设置").push("trash_box");

        TRASH_BOX_ROWS = BUILDER
                .comment("Number of rows in each trash box (Default: 6, Min: 1, Max: 6)")
                .translation("recycle.config.trash_box_rows")
                .defineInRange("trash_box_rows", 6, 1, 6);

        ITEM_STACK_MULTIPLIER = BUILDER
                .comment("Stack size multiplier (Default: 100 = 64*100=6400, Min: 1, Max: 1000)")
                .translation("recycle.config.item_stack_multiplier")
                .defineInRange("item_stack_multiplier", 100, 1, 1000);
        
        DIMENSION_TRASH_ALLOW_PUT_IN = BUILDER
                .comment("Dimensions that allow players to put items into trash boxes")
                .translation("recycle.config.dimension_trash_allow_put_in")
                .defineListAllowEmpty("dimension_trash_allow_put_in",
                    List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    () -> "",
                    Config::validateResourceLocation);
        
        DIMENSION_TRASH_CROSS_ACCESS = BUILDER
                .comment("Allow cross-dimension trash box access. When false, ignores DIMENSION_TRASH_ALLOW_PUT_IN (Default: true)")
                .translation("recycle.config.dimension_trash_cross_access")
                .define("dimension_trash_cross_access", true);

        MAX_BOXES_PER_DIMENSION = BUILDER
                .comment("Maximum trash boxes per dimension (Default: 3, Min: 1, Max: 5)")
                .translation("recycle.config.max_boxes_per_dimension")
                .defineInRange("max_boxes_per_dimension", 3, 1, 5);

        
        BUILDER.pop();
        
        // 邮费系统
        BUILDER.comment("Payment system for cross-dimension access / 跨维度访问邮费系统").push("payment");
        
        INSERT_PAYMENT_MODE = BUILDER
                .comment("Insert payment mode: all_dimensions_pay (always pay), current_dimension_free (free for same dim), all_free (never pay)")
                .translation("recycle.config.insert_payment_mode")
                .defineInList("insert_payment_mode", "current_dimension_free", 
                        Arrays.asList("all_dimensions_pay", "current_dimension_free", "all_free"));

        PAYMENT_ITEM_TYPE = BUILDER
                .comment("Payment item type (Default: minecraft:emerald)")
                .translation("recycle.config.payment_item_type")
                .define("payment_item_type", "minecraft:emerald");
        
        CROSS_DIMENSION_ACCESS_COST = BUILDER
                .comment("Payment cost for dimension access (Default: 1, Min: 1, Max: 64)")
                .translation("recycle.config.cross_dimension_access_cost")
                .defineInRange("cross_dimension_access_cost", 1, 1, 64);
        
        EXTRACT_PAYMENT_MODE = BUILDER
                .comment("Extract payment mode: all_dimensions_pay (always pay), current_dimension_free (free for same dim), all_free (never pay)")
                .translation("recycle.config.extract_payment_mode")
                .defineInList("extract_payment_mode", "current_dimension_free", 
                        Arrays.asList("all_dimensions_pay", "current_dimension_free", "all_free"));
        
        DIMENSION_MULTIPLIERS = BUILDER
                .comment("Cost multipliers per dimension. Format: dimension_id:multiplier. Only applies when player in different dimension")
                .translation("recycle.config.dimension_multipliers")
                .defineListAllowEmpty("dimension_multipliers", 
                        List.of("minecraft:overworld:1.0", "minecraft:the_nether:1.0", "minecraft:the_end:2.0"),
                        () -> "minecraft:overworld:1.0",
                        obj -> obj instanceof String && ((String) obj).matches("^[a-z0-9_]+:[a-z0-9_]+:[0-9]+(\\.[0-9]+)?$"));
        
        BUILDER.pop();
        
        // 物品过滤系统
        BUILDER.comment("Item filtering settings / 物品过滤设置").push("item_filter");
        
        CLEAN_MODE = BUILDER
                .comment("Item cleaning mode: whitelist (keep only listed items), blacklist (clean only listed items)")
                .translation("recycle.config.clean_mode")
                .defineInList("clean_mode", "whitelist", Arrays.asList("whitelist", "blacklist"));
        
        WHITELIST = BUILDER
                .comment("Items that will be kept (protected from cleaning)")
                .translation("recycle.config.whitelist")
                .defineListAllowEmpty("whitelist",
                    List.of("minecraft:netherite_ingot", "minecraft:elytra"),
                    () -> "",
                    Config::validateResourceLocation);
        
        BLACKLIST = BUILDER
                .comment("Items that will be cleaned up")
                .translation("recycle.config.blacklist")
                .defineListAllowEmpty("blacklist", 
                    List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel"),
                    () -> "",
                    Config::validateResourceLocation);
        
        BUILDER.pop();

        // 弹射物清理设置
        BUILDER.comment("Projectile cleanup settings / 弹射物清理设置").push("projectile_cleanup");
        
        CLEAN_PROJECTILES = BUILDER
                .comment("Enable cleaning up projectiles (Default: true)")
                .translation("recycle.config.clean_projectiles")
                .define("clean_projectiles", true);

        PROJECTILE_TYPES_TO_CLEAN = BUILDER
                .comment("Types of projectiles to clean up")
                .translation("recycle.config.projectile_types_to_clean")
                .defineListAllowEmpty("projectile_types_to_clean",
                    List.of(
                        "minecraft:arrow", 
                        "minecraft:spectral_arrow",
                        "minecraft:dragon_fireball", 
                        "minecraft:wither_skull", 
                        "minecraft:fireball", 
                        "minecraft:small_fireball",
                        "minecraft:snowball",
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
        
        CHUNK_FREEZING_SEARCH_RADIUS = BUILDER
                .comment("Search radius for chunk loader freezing / 区块加载器冻结搜索半径",
                        "Determines the search area when looking for chunk loaders that affect chunks with excessive items",
                        "决定寻找影响物品过多区块的区块加载器的搜索范围",
                        "Default: 8, Min: 2, Max: 16")
                .translation("recycle.config.chunk_freezing_search_radius")
                .defineInRange("chunk_freezing_search_radius", 8, 2, 16);

        BUILDER.pop();

        // 主线程调度优化设置
        BUILDER.comment("Main thread scheduling optimization / 主线程调度优化设置").push("main_thread_scheduling");
        
        MAX_PROCESSING_TIME_MS = BUILDER
                .comment("Maximum processing time per tick in milliseconds / 每tick最大主线程删除物品处理时间（毫秒）",
                        "Default: 2, Min: 1, Max: 10")
                .translation("recycle.config.max_processing_time_ms")
                .defineInRange("max_processing_time_ms", 2, 1, 10);

        BATCH_SIZE = BUILDER
                .comment("Batch size for processing (entity deletion, etc.) / 批处理大小（实体删除等）",
                        "Default: 100, Min: 50, Max: 500")
                .translation("recycle.config.batch_size")
                .defineInRange("batch_size", 100, 50, 500);

        BUILDER.pop();
        
        // 消息模板
        BUILDER.comment("Message templates / 消息模板").push("messages");

        ITEM_COUNT_DISPLAY_FORMAT = BUILDER
                .comment("Format for item count display / 物品数量显示格式",
                        "Available placeholders: {current} = current count, {max} = maximum stack size / 可用占位符：{current} = 当前数量，{max} = 最大堆叠数",
                        "Default: §7Available: §a{current} / §b{max}")
                .translation("recycle.config.item_count_display_format")
                .define("item_count_display_format", "§7Available: §a{current} / §b{max}");
        
        // === 清理结果消息 ===
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
        
        // === 邮费系统消息 ===
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

        // === 警告消息 ===
        WARNING_MESSAGE = BUILDER
                .comment("Warning message template (use {time} for remaining seconds) / 警告消息模板（使用{time}显示剩余秒数）",
                        "Default: §e[Auto Clean] Items will be cleaned up in {time} seconds!")
                .translation("recycle.config.warning_message")
                .define("warning_message", "§e[Auto Clean] Items will be cleaned up in {time} seconds!");

        TOO_MANY_ITEMS_WARNING_MESSAGE = BUILDER
                .comment("Warning message for too many items (use {count} for item count, {x} {z} for world coordinates, {ticket} for ticket level) / 物品过多警告消息（使用{count}显示物品数量，{x} {z}显示世界坐标，{ticket}显示票据级别）",
                        "Default: §e[Items Warning] Found {count} items at ({x}, {z}) ticketLevel:{ticket}")
                .translation("recycle.config.too_many_items_warning_message")
                .define("too_many_items_warning_message", "§e[Items Warning] Found {count} items at ({x}, {z}) ticketLevel:{ticket}");
        
        // === 命令系统消息 ===
        CMD_HELP_MESSAGES = BUILDER
                .comment("Command help messages")
                .translation("recycle.config.cmd_help_messages")
                .defineListAllowEmpty("cmd_help_messages",
                    List.of(
                        "§6=== Trash Box Command Help ===",
                        "§e/bin open <dimension> <box> §7- Open specific dimension trash box",
                        "§e/bin current <box> §7- Open current dimension trash box",
                        "§e/bin cleanup §7- Manually trigger cleanup",
                        "§e/bin tickets <x> <z> §7- Show chunk tickets info",
                        "§7Example: §f/bin open minecraft:overworld 1"
                    ),
                    () -> "",
                    obj -> obj instanceof String);
        
        // === UI界面消息 ===
        TRASH_BOX_BUTTON_TEXT = BUILDER
                .comment("Text for trash box button / 垃圾箱按钮文本",
                        "Available placeholders: {name} for dimension name / 可用占位符：{name} 表示维度名称",
                        "Default: [Open Trash Box]")
                .translation("recycle.config.trash_box_button_text")
                .define("trash_box_button_text", "[Open Trash Box]");
        
        TRASH_BOX_BUTTON_HOVER = BUILDER
                .comment("Hover text for trash box button / 垃圾箱按钮悬停文本",
                        "Available placeholders: {name} for dimension name / 可用占位符：{name} 表示维度名称",
                        "Default: Click to open trash box #1 in {name}")
                .translation("recycle.config.trash_box_button_hover")
                .define("trash_box_button_hover", "Click to open trash box #1 in {name}");
        
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

    public static MenuType<ChestMenu> getMenuTypeForRows() {
        return switch(TRASH_BOX_ROWS.get()) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
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
        
        DIMENSION_MULTIPLIERS.get().forEach(entry -> {
            try {
                String[] parts = entry.split(":");
                if (parts.length == 3) {
                    String dimensionId = parts[0] + ":" + parts[1];
                    double multiplier = Double.parseDouble(parts[2]);
                    dimensionMultiplierCache.put(dimensionId, multiplier);
                }
            } catch (NumberFormatException e) {
                ErrorHandler.handleVoidOperation("parseDimensionMultiplier", () -> {});
            }
        });
    }
    
    /**
     * 计算邮费数量（支持insert和extract操作）
     * @param playerDim 玩家所在维度
     * @param trashDim 垃圾箱所在维度
     * @param operation 操作类型："insert" 或 "extract"
     * @return 需要支付的邮费数量，0表示免费
     */
    public static int calculatePaymentCost(ResourceLocation playerDim, ResourceLocation trashDim, String operation) {
        boolean isSameDimension = playerDim.equals(trashDim);
        String paymentMode = "insert".equals(operation) ? INSERT_PAYMENT_MODE.get() : EXTRACT_PAYMENT_MODE.get();
        
        return switch (paymentMode) {
            case "current_dimension_free" -> isSameDimension ? 0 : calculateCrossDimensionCost(trashDim);
            case "all_dimensions_pay" -> isSameDimension ? CROSS_DIMENSION_ACCESS_COST.get() : calculateCrossDimensionCost(trashDim);
            default -> 0;
        };
    }
    
    /**
     * 计算跨维度邮费
     * @param trashDim 垃圾箱所在维度
     * @return 计算后的邮费数量
     */
    private static int calculateCrossDimensionCost(ResourceLocation trashDim) {
        int baseCost = CROSS_DIMENSION_ACCESS_COST.get();
        double multiplier = getDimensionMultiplier(trashDim.toString());
        return (int) Math.ceil(baseCost * multiplier);
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
        return formatTemplate(WARNING_MESSAGE.get(), Map.of("time", String.valueOf(remainingSeconds)));
    }

    // === 物品过滤便捷方法 ===
    
    /**
     * 检查是否为白名单模式,反之则是黑名单
     */
    public static boolean isWhitelistMode() {
        return "whitelist".equals(CLEAN_MODE.get());
    }
    
    /**
     * 获取格式化的物品过多警告消息（支持点击传送）
     */
    public static Component getItemWarningMessage(int itemCount, int worldX, int worldZ, int ticketLevel) {
        String message = formatTemplate(TOO_MANY_ITEMS_WARNING_MESSAGE.get(), Map.of(
            "count", String.valueOf(itemCount),
            "x", String.valueOf(worldX),
            "z", String.valueOf(worldZ),
            "ticket", String.valueOf(ticketLevel)
        ));
        
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
    
    // === 通用字符串模板工具 ===
    
    /**
     * 统一的字符串模板处理工具
     * @param template 模板字符串，使用{key}格式的占位符
     * @param params 替换参数映射
     * @return 替换后的字符串
     */
    public static String formatTemplate(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    
    // === 消息格式化方法 - 已简化直接访问 ===
    
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
     * 构建详细清理完成消息（简化版）
     * @param dimensionStats 各维度清理统计信息
     * @return 清理结果的聊天组件
     */
    public static Component getDetailedCleanupMessage(Map<ResourceLocation, ?> dimensionStats) {
        MutableComponent mainComponent = Component.literal(CLEANUP_RESULT_HEADER.get());
        
        // 简化：所有维度统一处理，按字典序排序
        dimensionStats.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(ResourceLocation::compareTo))
            .forEach(entry -> {
                Component dimensionEntry = formatDimensionEntry(entry.getKey(), entry.getValue());
                if (dimensionEntry != null) {
                    mainComponent.append(Component.literal("\n")).append(dimensionEntry);
                }
            });
        
        return mainComponent;
    }
    
    /**
     * 格式化单个维度的清理条目
     * @param dimensionId 维度ID
     * @param stats 统计数据对象
     * @return 格式化后的条目Component，如果获取失败返回null
     */
    private static Component formatDimensionEntry(ResourceLocation dimensionId, Object stats) {
        if (!(stats instanceof com.klnon.recyclingservice.service.CleanupService.DimensionCleanupStats dimensionStats)) {
            return null;
        }
        
        // 创建基础文本
        String baseText = formatTemplate(DIMENSION_ENTRY_FORMAT.get(), Map.of(
            "name", getDimensionDisplayName(dimensionId),
            "items", String.valueOf(dimensionStats.itemsCleaned()),
            "entities", String.valueOf(dimensionStats.projectilesCleaned())
        ));
        
        // 创建可点击的按钮
        String buttonText = formatTemplate(TRASH_BOX_BUTTON_TEXT.get(), 
            Map.of("name", getDimensionDisplayName(dimensionId)));
        String hoverText = formatTemplate(TRASH_BOX_BUTTON_HOVER.get(), 
            Map.of("name", getDimensionDisplayName(dimensionId)));
            
        MutableComponent button = Component.literal(buttonText)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                                "/bin open " + dimensionId + " 1"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hoverText).withStyle(ChatFormatting.YELLOW))));
        
        // 组合文本和按钮
        return Component.literal(baseText).append(button);
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