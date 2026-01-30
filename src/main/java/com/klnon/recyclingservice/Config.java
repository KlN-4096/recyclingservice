package com.klnon.recyclingservice;

import com.klnon.recyclingservice.foundation.config.GameplayConfig;
import com.klnon.recyclingservice.foundation.config.MessageConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一配置管理器
 * 职责：
 * - 整合各功能配置模块
 * - 提供便捷访问方法
 * - 管理配置缓存
 */
public class Config {

    // ==================== 配置规范 ====================

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final GameplayConfig GAMEPLAY = new GameplayConfig(BUILDER);
    public static final MessageConfig MESSAGE = new MessageConfig(BUILDER);
    private static final Map<String, Double> dimensionMultiplierCache = new ConcurrentHashMap<>();

    // ==================== 缓存字段 ====================
    private static final Map<String, Integer> dimensionCostCapCache = new ConcurrentHashMap<>();
    // 物品过滤缓存
    private static volatile Set<String> whitelistCache = new HashSet<>();
    private static volatile Set<String> blacklistCache = new HashSet<>();
    private static volatile Set<String> projectileTypesCache = new HashSet<>();
    // 维度相关缓存
    private static volatile Set<String> allowPutInDimensionsCache = new HashSet<>();

    static {
        SPEC = BUILDER.build();
    }


    // ==================== 清理相关 ====================

    /**
     * 获取清理间隔（tick）
     */
    public static int getCleanIntervalTicks() {
        return GAMEPLAY.autoCleanTime.get() * 20;
    }

    /**
     * 检查是否为白名单模式
     */
    public static boolean isWhitelistMode() {
        return "whitelist".equals(GAMEPLAY.cleanMode.get());
    }

    /**
     * 获取白名单缓存
     */
    public static Set<String> getWhitelist() {
        return whitelistCache;
    }

    /**
     * 获取黑名单缓存
     */
    public static Set<String> getBlacklist() {
        return blacklistCache;
    }

    /**
     * 获取弹射物类型缓存
     */
    public static Set<String> getProjectileTypes() {
        return projectileTypesCache;
    }

    // ==================== 垃圾箱相关 ====================

    /**
     * 获取物品堆叠上限
     */
    public static int getItemStackMultiplier(ItemStack itemStack) {
        return GAMEPLAY.itemStackMultiplier.get() * itemStack.getMaxStackSize();
    }

    /**
     * 检查维度是否允许玩家放入物品
     */
    public static boolean isDimensionAllowPutIn(String trashDimension, String playerDimension) {
        if (!GAMEPLAY.dimensionTrashCrossAccess.get()) {
            // 不允许跨维度：玩家必须在垃圾箱所在维度
            return playerDimension.equals(trashDimension)
                    && allowPutInDimensionsCache.contains(trashDimension);
        }
        // 允许跨维度：按配置列表判断
        return allowPutInDimensionsCache.contains(trashDimension);
    }

    // ==================== 支付相关 ====================

    /**
     * 获取支付物品
     */
    public static ResourceLocation getPaymentItem() {
        return ResourceLocation.parse(GAMEPLAY.paymentItemType.get());
    }

    /**
     * 获取指定维度的邮费倍率
     */
    public static double getDimensionMultiplier(String dimensionId) {
        return dimensionMultiplierCache.getOrDefault(dimensionId, 1.0);
    }

    /**
     * 获取指定维度的邮费上限（0表示无限制）
     */
    public static int getDimensionCostCap(String dimensionId) {
        return dimensionCostCapCache.getOrDefault(dimensionId, 0);
    }

    // ==================== 缓存管理 ====================

    /**
     * 更新所有缓存（配置重载时调用）
     */
    public static void updateCaches() {
        try {
            // 物品过滤缓存
            whitelistCache = new HashSet<>(GAMEPLAY.whitelist.get());
            blacklistCache = new HashSet<>(GAMEPLAY.blacklist.get());
            projectileTypesCache = new HashSet<>(GAMEPLAY.projectileTypesToClean.get());

            // 维度相关缓存
            allowPutInDimensionsCache = new HashSet<>(GAMEPLAY.dimensionTrashAllowPutIn.get());
            parseDimensionMultipliers();
            parseDimensionCostCaps();

        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to update config caches", e);
            initEmptyCaches();
        }
    }

    /**
     * 初始化空缓存（异常时的兜底）
     */
    private static void initEmptyCaches() {
        if (whitelistCache == null) whitelistCache = new HashSet<>();
        if (blacklistCache == null) blacklistCache = new HashSet<>();
        if (projectileTypesCache == null) projectileTypesCache = new HashSet<>();
        if (allowPutInDimensionsCache == null) allowPutInDimensionsCache = new HashSet<>();
    }

    /**
     * 解析维度配置条目（格式：namespace:path:value）
     */
    private static void parseDimensionEntry(String entry, DimensionEntryConsumer consumer) {
        try {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                String dimensionId = parts[0] + ":" + parts[1];
                consumer.accept(dimensionId, parts[2]);
            } else {
                Recyclingservice.LOGGER.warn("Invalid dimension config format: '{}'", entry);
            }
        } catch (NumberFormatException e) {
            Recyclingservice.LOGGER.warn("Invalid number in dimension config: '{}'", entry);
        }
    }

    /**
     * 解析维度倍率配置
     */
    private static void parseDimensionMultipliers() {
        dimensionMultiplierCache.clear();

        try {
            for (String entry : GAMEPLAY.dimensionMultipliers.get()) {
                parseDimensionEntry(entry, (dimId, value) ->
                        dimensionMultiplierCache.put(dimId, Double.parseDouble(value))
                );
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to parse dimension multipliers", e);
        }
    }

    /**
     * 解析维度邮费上限配置
     */
    private static void parseDimensionCostCaps() {
        dimensionCostCapCache.clear();

        try {
            for (String entry : GAMEPLAY.extractCostCaps.get()) {
                parseDimensionEntry(entry, (dimId, value) -> {
                    int cap = Math.max(0, Integer.parseInt(value));
                    dimensionCostCapCache.put(dimId, cap);
                });
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Failed to parse dimension cost caps", e);
        }
    }


    @FunctionalInterface
    private interface DimensionEntryConsumer {
        void accept(String dimensionId, String value) throws NumberFormatException;
    }


}