package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 自动清理系统配置
 */
public class CleanupConfig extends ConfigSection {
    
    public ModConfigSpec.IntValue autoCleanTime;
    public ModConfigSpec.BooleanValue showCleanupWarnings;
    public ModConfigSpec.IntValue warningCountdownStart;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        autoCleanTime = builder
                .comment("Auto cleanup interval in seconds")
                .defineInRange("auto_clean_time_seconds", 600, 30, 7200);
        
        showCleanupWarnings = builder
                .comment("Show warning messages before cleanup")
                .define("show_cleanup_warnings", true);

        warningCountdownStart = builder
                .comment("Start countdown warnings at remaining seconds (0=disabled)")
                .defineInRange("warning_countdown_start", 15, 0, 300);
        
        builder.pop();
    }
    
    @Override
    public String getName() {
        return "auto_cleanup";
    }
    
    @Override
    public String getDescription() {
        return "Auto cleanup settings";
    }
}