package com.klnon.recyclingservice;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// 一个示例配置类。这不是必需的，但拥有一个来保持配置有序是个好主意。
// 演示如何使用 Neo 的配置 API
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("是否在公共设置中记录泥土块")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("一个魔数")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("您希望魔数的介绍消息是什么")
            .define("magicNumberIntroduction", "The magic number is... ");

    // 一个字符串列表，被视为物品的资源位置
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("在公共设置中记录的物品列表。")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
