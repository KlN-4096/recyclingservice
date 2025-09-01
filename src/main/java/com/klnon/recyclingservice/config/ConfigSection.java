package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 配置节基类 - 提供通用配置构建方法
 */
public abstract class ConfigSection {
    
    /**
     * 注册所有配置项到构建器
     */
    public abstract void build(ModConfigSpec.Builder builder);
    
    /**
     * 获取配置节名称
     */
    public abstract String getName();
    
    /**
     * 获取配置节描述
     */
    public abstract String getDescription();
}