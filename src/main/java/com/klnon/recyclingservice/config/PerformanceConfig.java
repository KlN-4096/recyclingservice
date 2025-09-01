package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 性能优化配置
 */
public class PerformanceConfig extends ConfigSection {
    
    public ModConfigSpec.IntValue maxProcessingTimeMs;
    public ModConfigSpec.IntValue batchSize;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        maxProcessingTimeMs = builder
                .comment("Maximum processing time per tick in milliseconds")
                .defineInRange("max_processing_time_ms", 2, 1, 10);

        batchSize = builder
                .comment("Batch size for processing (entity deletion, etc.)")
                .defineInRange("batch_size", 100, 50, 500);

        builder.pop();
    }
    
    @Override
    public String getName() {
        return "performance";
    }
    
    @Override
    public String getDescription() {
        return "Performance optimization settings";
    }
}