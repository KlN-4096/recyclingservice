package com.klnon.recyclingservice;

import com.klnon.recyclingservice.foundation.config.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一配置管理器 - 整合各功能配置模块
 * 采用新的架构：减少文件数量，保持合理分离
 */
public class Config {
    
    // 配置构建器和规范
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    
    // 各功能配置实例
    public static final GameplayConfig GAMEPLAY = new GameplayConfig(BUILDER);
    public static final TechnicalConfig TECHNICAL = new TechnicalConfig(BUILDER);
    public static final MessageConfig MESSAGE = new MessageConfig(BUILDER);
    
    // 性能优化缓存
    public static volatile Set<String> whitelistCache = new HashSet<>();
    public static volatile Set<String> blacklistCache = new HashSet<>();
    public static volatile Set<String> projectileTypesCache = new HashSet<>();
    private static volatile Set<String> allowPutInDimensionsCache = new HashSet<>();
    private static final Map<String, Double> dimensionMultiplierCache = new ConcurrentHashMap<>();
    
    static {
        // 构建配置规范
        SPEC = BUILDER.build();
    }
    

    
    // === 便捷访问方法 ===
    
    /**
     * 获取清理间隔（tick）
     */
    public static int getCleanIntervalTicks() {
        return GAMEPLAY.autoCleanTime.get() * 20;
    }
    
    /**
     * 获取付费物品的ResourceLocation
     */
    public static ResourceLocation getPaymentItem() {
        return ResourceLocation.parse(GAMEPLAY.paymentItemType.get());
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
        return "whitelist".equals(GAMEPLAY.cleanMode.get());
    }
    
    /**
     * 获取物品堆叠合并限制
     */
    public static int getItemStackMultiplier(ItemStack itemStack) {
        return GAMEPLAY.itemStackMultiplier.get() * itemStack.getMaxStackSize();
    }
    
    /**
     * 更新HashSet缓存（配置重载时调用）
     */
    public static void updateCaches() {
        try {
            whitelistCache = new HashSet<>(GAMEPLAY.whitelist.get());
            blacklistCache = new HashSet<>(GAMEPLAY.blacklist.get());
            projectileTypesCache = new HashSet<>(GAMEPLAY.projectileTypesToClean.get());
            allowPutInDimensionsCache = new HashSet<>(GAMEPLAY.dimensionTrashAllowPutIn.get());
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
            GAMEPLAY.dimensionMultipliers.get().forEach(entry -> {
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