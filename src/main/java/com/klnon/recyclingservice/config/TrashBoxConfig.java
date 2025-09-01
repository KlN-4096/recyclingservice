package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/**
 * 垃圾箱系统配置
 */
public class TrashBoxConfig extends ConfigSection {
    
    public ModConfigSpec.IntValue trashBoxRows;
    public ModConfigSpec.IntValue itemStackMultiplier;
    public ModConfigSpec.ConfigValue<List<? extends String>> dimensionTrashAllowPutIn;
    public ModConfigSpec.BooleanValue dimensionTrashCrossAccess;
    public ModConfigSpec.IntValue maxBoxesPerDimension;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        trashBoxRows = builder
                .comment("Number of rows in each trash box")
                .defineInRange("trash_box_rows", 6, 1, 6);

        itemStackMultiplier = builder
                .comment("Stack size multiplier (default: 100 = 64*100=6400)")
                .defineInRange("item_stack_multiplier", 100, 1, 1000);
        
        dimensionTrashAllowPutIn = builder
                .comment("Dimensions that allow players to put items into trash boxes")
                .defineListAllowEmpty("dimension_trash_allow_put_in",
                    List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                    () -> "",
                    this::validateResourceLocation);
        
        dimensionTrashCrossAccess = builder
                .comment("Allow cross-dimension trash box access")
                .define("dimension_trash_cross_access", true);

        maxBoxesPerDimension = builder
                .comment("Maximum trash boxes per dimension")
                .defineInRange("max_boxes_per_dimension", 3, 1, 5);
        
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
        return "trash_box";
    }
    
    @Override
    public String getDescription() {
        return "Trash box system settings";
    }
}