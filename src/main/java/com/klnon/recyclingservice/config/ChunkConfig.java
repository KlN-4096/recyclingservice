package com.klnon.recyclingservice.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 区块管理配置
 */
public class ChunkConfig extends ConfigSection {
    
    public ModConfigSpec.BooleanValue enableChunkItemWarning;
    public ModConfigSpec.BooleanValue enableChunkFreezing;
    public ModConfigSpec.IntValue tooManyItemsWarning;
    public ModConfigSpec.IntValue chunkFreezingSearchRadius;
    
    @Override
    public void build(ModConfigSpec.Builder builder) {
        builder.comment(getDescription()).push(getName());
        
        enableChunkItemWarning = builder
                .comment("Enable warnings when chunks have too many items")
                .define("enable_chunk_item_warning", true);
        
        enableChunkFreezing = builder
                .comment("Enable automatic chunk freezing when too many items are detected")
                .define("enable_chunk_freezing", true);
        
        tooManyItemsWarning = builder
                .comment("Warn when a chunk has more than this many items")
                .defineInRange("too_many_items_warning_limit", 50, 5, 10000);
        
        chunkFreezingSearchRadius = builder
                .comment("Search radius for chunk loader freezing")
                .defineInRange("chunk_freezing_search_radius", 8, 2, 16);

        builder.pop();
    }
    
    @Override
    public String getName() {
        return "chunk_management";
    }
    
    @Override
    public String getDescription() {
        return "Chunk management settings";
    }
}