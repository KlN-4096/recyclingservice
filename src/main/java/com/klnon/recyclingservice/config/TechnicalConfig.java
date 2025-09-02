package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 技术配置 - 包含性能优化、区块管理、调试设置
 */
public class TechnicalConfig {
    
    // === 性能优化设置 ===
    public final ModConfigSpec.IntValue maxProcessingTimeMs;
    public final ModConfigSpec.IntValue batchSize;
    
    // === 区块管理设置 ===
    public final ModConfigSpec.BooleanValue enableChunkItemWarning;
    public final ModConfigSpec.BooleanValue enableChunkFreezing;
    public final ModConfigSpec.IntValue tooManyItemsWarning;
    public final ModConfigSpec.IntValue chunkFreezingSearchRadius;
    
    // === 调试设置 ===
    public final ModConfigSpec.BooleanValue enableDebugLogs;
    
    public TechnicalConfig(ModConfigSpec.Builder builder) {
        builder.comment("Technical Settings").push("technical");
        
        // 性能优化
        builder.comment("Performance optimization").push("performance");
        maxProcessingTimeMs = builder
                .comment("Maximum processing time per tick in milliseconds")
                .defineInRange("max_time_ms", 2, 1, 10);
        batchSize = builder
                .comment("Batch size for entity processing")
                .defineInRange("batch_size", 100, 50, 500);
        builder.pop();
        
        // 区块管理
        builder.comment("Chunk management").push("chunk");
        enableChunkItemWarning = builder
                .comment("Enable warnings for chunks with too many items")
                .define("enable_warning", true);
        enableChunkFreezing = builder
                .comment("Enable automatic chunk freezing")
                .define("enable_freezing", true);
        tooManyItemsWarning = builder
                .comment("Item count threshold for warnings")
                .defineInRange("warning_threshold", 50, 5, 10000);
        chunkFreezingSearchRadius = builder
                .comment("Search radius for chunk loader freezing")
                .defineInRange("freeze_radius", 8, 2, 16);
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