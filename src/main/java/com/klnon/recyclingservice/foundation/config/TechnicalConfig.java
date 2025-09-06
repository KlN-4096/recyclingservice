package com.klnon.recyclingservice.foundation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 技术配置 - 包含区块管理、调试设置
 */
public class TechnicalConfig {
    
    // === 区块管理设置 ===
    public final ModConfigSpec.BooleanValue enableChunkItemWarning;
    public final ModConfigSpec.IntValue tooManyItemsWarning;
    public final ModConfigSpec.IntValue chunkFreezingSearchRadius;
    public final ModConfigSpec.BooleanValue enableStartupChunkCleanup;
    public final ModConfigSpec.IntValue startupChunkEntityThreshold;
    // 动态区块管理
    public final ModConfigSpec.BooleanValue enableDynamicChunkManagement;
    public final ModConfigSpec.DoubleValue tpsThreshold;
    public final ModConfigSpec.DoubleValue msptThresholdSuspend;
    public final ModConfigSpec.DoubleValue msptThresholdRestore;
    public final ModConfigSpec.IntValue chunkOperationCount;
    
    // 激进接管配置
    public final ModConfigSpec.BooleanValue enableAggressiveTakeover;
    public final ModConfigSpec.IntValue takeoverBlockEntityThreshold;
    
    // 物品监控配置  
    public final ModConfigSpec.BooleanValue enableItemBasedFreezing;
    public final ModConfigSpec.IntValue itemFreezeHours;
    
    // === 调试设置 ===
    public final ModConfigSpec.BooleanValue enableDebugLogs;
    
    public TechnicalConfig(ModConfigSpec.Builder builder) {
        builder.comment("Technical Settings").push("technical");
        
        // 区块管理
        builder.comment("Chunk management").push("chunk");
        enableChunkItemWarning = builder
                .comment("Enable warnings for chunks with too many items")
                .define("enable_warning", true);
        tooManyItemsWarning = builder
                .comment("Item count threshold for warnings")
                .defineInRange("warning_threshold", 50, 5, 10000);
        chunkFreezingSearchRadius = builder
                .comment("Search radius for chunk loader freezing")
                .defineInRange("freeze_radius", 8, 2, 16);
        enableStartupChunkCleanup = builder
                .comment("Enable startup chunk cleanup based on block entity count")
                .define("enable_startup_cleanup", false);
        startupChunkEntityThreshold = builder
                .comment("Minimum block entity count to keep chunk loaded at startup")
                .defineInRange("startup_entity_threshold", 100, 10, 10000);
        enableDynamicChunkManagement = builder
                .comment("Enable dynamic chunk management based on server performance")
                .define("enable_dynamic_management", false);
        tpsThreshold = builder
                .comment("TPS threshold for suspending chunks (suspend when TPS < threshold)")
                .defineInRange("tps_threshold", 18.0, 10.0, 20.0);
        msptThresholdSuspend = builder
                .comment("MSPT threshold for suspending chunks (suspend when MSPT > threshold)")
                .defineInRange("mspt_suspend_threshold", 45.0, 30.0, 100.0);
        msptThresholdRestore = builder
                .comment("MSPT threshold for restoring chunks (restore when MSPT < threshold)")
                .defineInRange("mspt_restore_threshold", 30.0, 20.0, 40.0);
        chunkOperationCount = builder
                .comment("Number of chunks to suspend/restore per operation")
                .defineInRange("chunk_operation_count", 10, 1, 100);
        builder.pop();
        
        // 激进接管
        builder.comment("Aggressive takeover settings").push("takeover");
        enableAggressiveTakeover = builder
                .comment("Enable aggressive chunk takeover on startup")
                .define("enable_aggressive_takeover", false);
        takeoverBlockEntityThreshold = builder
                .comment("Block entity count threshold for takeover")
                .defineInRange("takeover_threshold", 100, 10, 10000);
        builder.pop();
        
        // 物品监控
        builder.comment("Item-based freezing settings").push("item_freezing");
        enableItemBasedFreezing = builder
                .comment("Enable item-based chunk freezing")
                .define("enable_item_freezing", true);
        itemFreezeHours = builder
                .comment("Hours to freeze chunks with too many items")
                .defineInRange("freeze_hours", 1, 1, 24);
        builder.pop();
        
        // 调试
        builder.comment("Debug settings").push("debug");
        enableDebugLogs = builder
                .comment("Enable debug logging")
                .define("enable_logs", false);
        builder.pop();
        
        builder.pop();
    }
}