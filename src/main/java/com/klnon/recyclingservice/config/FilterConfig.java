package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Arrays;
import java.util.List;

/**
 * 物品和实体过滤配置
 */
public class FilterConfig extends ConfigSection {
    
    public ModConfigSpec.ConfigValue<String> cleanMode;
    public ModConfigSpec.ConfigValue<List<? extends String>> whitelist;
    public ModConfigSpec.ConfigValue<List<? extends String>> blacklist;
    public ModConfigSpec.BooleanValue cleanProjectiles;
    public ModConfigSpec.ConfigValue<List<? extends String>> projectileTypesToClean;
    public ModConfigSpec.BooleanValue protectCreateProcessingItems;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        cleanMode = builder
                .comment("Item cleaning mode: whitelist (keep only listed items), blacklist (clean only listed items)")
                .defineInList("clean_mode", "whitelist", Arrays.asList("whitelist", "blacklist"));
        
        whitelist = builder
                .comment("Items that will be kept (protected from cleaning)")
                .defineListAllowEmpty("whitelist",
                    List.of("minecraft:netherite_ingot", "minecraft:elytra"),
                    () -> "",
                    this::validateResourceLocation);
        
        blacklist = builder
                .comment("Items that will be cleaned up")
                .defineListAllowEmpty("blacklist", 
                    List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:gravel"),
                    () -> "",
                    this::validateResourceLocation);
        
        cleanProjectiles = builder
                .comment("Enable cleaning up projectiles")
                .define("clean_projectiles", true);

        projectileTypesToClean = builder
                .comment("Types of projectiles to clean up")
                .defineListAllowEmpty("projectile_types_to_clean",
                    List.of("minecraft:arrow", "minecraft:spectral_arrow", "minecraft:dragon_fireball", 
                           "minecraft:wither_skull", "minecraft:fireball", "minecraft:small_fireball",
                           "minecraft:snowball", "minecraft:shulker_bullet", "minecraft:llama_spit"),
                    () -> "",
                    this::validateResourceLocation);

        protectCreateProcessingItems = builder
                .comment("Protect items being processed by Create mod fans")
                .define("protect_create_processing_items", true);
        
        builder.pop();
    }
    
    private boolean validateResourceLocation(Object obj) {
        if (!(obj instanceof String id)) return false;
        try {
            net.minecraft.resources.ResourceLocation.parse(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getName() {
        return "item_filter";
    }
    
    @Override
    public String getDescription() {
        return "Item and projectile filtering settings";
    }
}