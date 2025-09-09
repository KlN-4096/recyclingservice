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
    // 动态区块管理
    public final ModConfigSpec.BooleanValue enableChunkManagement;
    public final ModConfigSpec.IntValue chunkEntityThreshold;
    public final ModConfigSpec.DoubleValue msptThresholdSuspend;
    public final ModConfigSpec.DoubleValue msptThresholdRestore;
    public final ModConfigSpec.IntValue chunkOperationCount;
    
    // 物品监控配置  
    public final ModConfigSpec.BooleanValue enableItemBasedFreezing;
    public final ModConfigSpec.IntValue itemFreezeMinutes;
    
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
                .defineInRange("freeze_radius", 2, 1, 10);
        enableChunkManagement = builder
                .comment("Enable chunk management including startup cleanup and dynamic management based on server performance")
                .define("enable_chunk_management", false);
        chunkEntityThreshold = builder
                .comment("Block entity count threshold for keeping chunk loaded (unload if below)")
                .defineInRange("chunk_entity_threshold", 50, 5, 10000);
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
        
        // 物品监控
        builder.comment("Item-based freezing settings").push("item_freezing");
        enableItemBasedFreezing = builder
                .comment("Enable item-based chunk freezing")
                .define("enable_item_freezing", true);
        itemFreezeMinutes = builder
                .comment("Minutes to freeze chunks with too many items")
                .defineInRange("freeze_minutes", 60, 1, 14400);
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