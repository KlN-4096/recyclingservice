package com.klnon.recyclingservice;

import com.klnon.recyclingservice.config.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一配置管理器 - 整合各功能配置模块
 */
public class Config {
    
    // 配置构建器和规范
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    
    // 各功能配置实例
    public static final CleanupConfig CLEANUP = new CleanupConfig();
    public static final TrashBoxConfig TRASH_BOX = new TrashBoxConfig();
    public static final PaymentConfig PAYMENT = new PaymentConfig();
    public static final FilterConfig FILTER = new FilterConfig();
    public static final PerformanceConfig PERFORMANCE = new PerformanceConfig();
    public static final ChunkConfig CHUNK = new ChunkConfig();
    public static final MessageConfig MESSAGE = new MessageConfig();
    
    // 性能优化缓存
    public static volatile Set<String> whitelistCache = new HashSet<>();
    public static volatile Set<String> blacklistCache = new HashSet<>();
    public static volatile Set<String> projectileTypesCache = new HashSet<>();
    private static volatile Set<String> allowPutInDimensionsCache = new HashSet<>();
    private static final Map<String, Double> dimensionMultiplierCache = new ConcurrentHashMap<>();
    
    // 调试设置
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGS;
    
    static {
        // 构建所有配置节
        CLEANUP.build(BUILDER);
        TRASH_BOX.build(BUILDER);
        PAYMENT.build(BUILDER);
        FILTER.build(BUILDER);
        PERFORMANCE.build(BUILDER);
        CHUNK.build(BUILDER);
        MESSAGE.build(BUILDER);
        
        // 调试设置
        BUILDER.comment("Debug settings").push("debug");
        ENABLE_DEBUG_LOGS = BUILDER
                .comment("Enable debug logging for ErrorHandler operations")
                .define("enable_debug_logs", false);
        BUILDER.pop();
        
        // 构建配置规范
        SPEC = BUILDER.build();
    }
    
    
    // 基础清理设置
    public static final ModConfigSpec.IntValue AUTO_CLEAN_TIME = CLEANUP.autoCleanTime;
    public static final ModConfigSpec.BooleanValue SHOW_CLEANUP_WARNINGS = CLEANUP.showCleanupWarnings;
    public static final ModConfigSpec.IntValue WARNING_COUNTDOWN_START = CLEANUP.warningCountdownStart;
    
    // 垃圾箱设置
    public static final ModConfigSpec.IntValue TRASH_BOX_ROWS = TRASH_BOX.trashBoxRows;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_TRASH_ALLOW_PUT_IN = TRASH_BOX.dimensionTrashAllowPutIn;
    public static final ModConfigSpec.BooleanValue DIMENSION_TRASH_CROSS_ACCESS = TRASH_BOX.dimensionTrashCrossAccess;
    
    // 维度管理
    public static final ModConfigSpec.IntValue MAX_BOXES_PER_DIMENSION = TRASH_BOX.maxBoxesPerDimension;
    
    // 付费系统
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ITEM_TYPE = PAYMENT.paymentItemType;
    public static final ModConfigSpec.IntValue CROSS_DIMENSION_ACCESS_COST = PAYMENT.crossDimensionAccessCost;
    public static final ModConfigSpec.ConfigValue<String> INSERT_PAYMENT_MODE = PAYMENT.insertPaymentMode;
    public static final ModConfigSpec.ConfigValue<String> EXTRACT_PAYMENT_MODE = PAYMENT.extractPaymentMode;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_MULTIPLIERS = PAYMENT.dimensionMultipliers;
    
    // 物品过滤
    public static final ModConfigSpec.ConfigValue<String> CLEAN_MODE = FILTER.cleanMode;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST = FILTER.whitelist;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST = FILTER.blacklist;
    
    // 弹射物过滤
    public static final ModConfigSpec.BooleanValue CLEAN_PROJECTILES = FILTER.cleanProjectiles;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROJECTILE_TYPES_TO_CLEAN = FILTER.projectileTypesToClean;
    
    // Create模组兼容性
    public static final ModConfigSpec.BooleanValue PROTECT_CREATE_PROCESSING_ITEMS = FILTER.protectCreateProcessingItems;
    
    // 区域管理
    public static final ModConfigSpec.BooleanValue ENABLE_CHUNK_ITEM_WARNING = CHUNK.enableChunkItemWarning;
    public static final ModConfigSpec.BooleanValue ENABLE_CHUNK_FREEZING = CHUNK.enableChunkFreezing;
    public static final ModConfigSpec.IntValue TOO_MANY_ITEMS_WARNING = CHUNK.tooManyItemsWarning;
    public static final ModConfigSpec.IntValue CHUNK_FREEZING_SEARCH_RADIUS = CHUNK.chunkFreezingSearchRadius;

    // 主线程调度优化设置
    public static final ModConfigSpec.IntValue MAX_PROCESSING_TIME_MS = PERFORMANCE.maxProcessingTimeMs;
    public static final ModConfigSpec.IntValue BATCH_SIZE = PERFORMANCE.batchSize;
    
    // UI界面设置
    public static final ModConfigSpec.IntValue ITEM_STACK_MULTIPLIER = TRASH_BOX.itemStackMultiplier;
    public static final ModConfigSpec.ConfigValue<String> ITEM_COUNT_DISPLAY_FORMAT = MESSAGE.itemCountDisplayFormat;
    
    // 清理结果消息
    public static final ModConfigSpec.ConfigValue<String> CLEANUP_RESULT_HEADER = MESSAGE.cleanupResultHeader;
    public static final ModConfigSpec.ConfigValue<String> DIMENSION_ENTRY_FORMAT = MESSAGE.dimensionEntryFormat;
    public static final ModConfigSpec.ConfigValue<String> ERROR_CLEANUP_FAILED = MESSAGE.errorCleanupFailed;
    public static final ModConfigSpec.ConfigValue<String> MANUAL_CLEANUP_START = MESSAGE.manualCleanupStart;
    
    // 邮费系统消息
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_ERROR_MESSAGE = MESSAGE.paymentErrorMessage;
    public static final ModConfigSpec.ConfigValue<String> PAYMENT_SUCCESS_MESSAGE = MESSAGE.paymentSuccessMessage;
    
    // 警告消息
    public static final ModConfigSpec.ConfigValue<String> WARNING_MESSAGE = MESSAGE.warningMessage;
    public static final ModConfigSpec.ConfigValue<String> TOO_MANY_ITEMS_WARNING_MESSAGE = MESSAGE.tooManyItemsWarningMessage;
    
    // 命令系统消息
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CMD_HELP_MESSAGES = MESSAGE.cmdHelpMessages;
    
    // UI界面消息
    public static final ModConfigSpec.ConfigValue<String> TRASH_BOX_BUTTON_TEXT = MESSAGE.trashBoxButtonText;
    public static final ModConfigSpec.ConfigValue<String> TRASH_BOX_BUTTON_HOVER = MESSAGE.trashBoxButtonHover;

    
    // === 便捷访问方法 ===
    
    /**
     * 获取清理间隔（tick）
     */
    public static int getCleanIntervalTicks() {
        return AUTO_CLEAN_TIME.get() * 20;
    }
    
    /**
     * 获取付费物品的ResourceLocation
     */
    public static ResourceLocation getPaymentItem() {
        return ResourceLocation.parse(PAYMENT_ITEM_TYPE.get());
    }
    
    /**
     * 获取指定维度的邮费倍数
     */
    public static double getDimensionMultiplier(String dimensionId) {
        return dimensionMultiplierCache.getOrDefault(dimensionId, 1.0);
    }
    
    /**
     * 检查维度是否允许玩家主动放入物品到垃圾箱
     */
    public static boolean isDimensionAllowPutIn(String dimensionId) {
        return allowPutInDimensionsCache.contains(dimensionId);
    }
    
    /**
     * 检查是否为白名单模式
     */
    public static boolean isWhitelistMode() {
        return "whitelist".equals(CLEAN_MODE.get());
    }
    
    /**
     * 获取物品堆叠合并限制
     */
    public static int getItemStackMultiplier(ItemStack itemStack) {
        return ITEM_STACK_MULTIPLIER.get() * itemStack.getMaxStackSize();
    }
    
    /**
     * 更新HashSet缓存（配置重载时调用）
     */
    public static void updateCaches() {
        try {
            whitelistCache = new HashSet<>(WHITELIST.get());
            blacklistCache = new HashSet<>(BLACKLIST.get());
            projectileTypesCache = new HashSet<>(PROJECTILE_TYPES_TO_CLEAN.get());
            allowPutInDimensionsCache = new HashSet<>(DIMENSION_TRASH_ALLOW_PUT_IN.get());
            parseDimensionMultipliers();
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to update config caches", e);
            
            if (whitelistCache == null) whitelistCache = new HashSet<>();
            if (blacklistCache == null) blacklistCache = new HashSet<>();
            if (projectileTypesCache == null) projectileTypesCache = new HashSet<>();
            if (allowPutInDimensionsCache == null) allowPutInDimensionsCache = new HashSet<>();
        }
    }
    
    /**
     * 解析维度倍数配置并更新缓存
     */
    private static void parseDimensionMultipliers() {
        dimensionMultiplierCache.clear();
        
        try {
            DIMENSION_MULTIPLIERS.get().forEach(entry -> {
                try {
                    String[] parts = entry.split(":");
                    if (parts.length == 3) {
                        String dimensionId = parts[0] + ":" + parts[1];
                        double multiplier = Double.parseDouble(parts[2]);
                        dimensionMultiplierCache.put(dimensionId, multiplier);
                    }
                } catch (NumberFormatException e) {
                    Recyclingservice.LOGGER.warn("Invalid dimension multiplier format: '{}', skipping", entry);
                }
            });
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to parse dimension multipliers, using defaults", e);
            dimensionMultiplierCache.put("minecraft:overworld", 1.0);
            dimensionMultiplierCache.put("minecraft:the_nether", 1.0);
            dimensionMultiplierCache.put("minecraft:the_end", 2.0);
        }
    }
}