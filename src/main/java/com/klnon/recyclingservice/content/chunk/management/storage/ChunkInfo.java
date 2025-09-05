package com.klnon.recyclingservice.content.chunk.management.storage;

import net.minecraft.world.level.ChunkPos;

/**
 * 区块信息记录
 */
public record ChunkInfo(
    ChunkPos chunkPos,
    ChunkState state,
    int blockEntityCount,
    int itemCount,
    long lastItemCheck,
    long unfreezeTime,
    long addedTime
) {
    
    public ChunkInfo(ChunkPos chunkPos, ChunkState state, int blockEntityCount) {
        this(chunkPos, state, blockEntityCount, 0, System.currentTimeMillis(), 0, System.currentTimeMillis());
    }
    
    public ChunkInfo withState(ChunkState newState) {
        return new ChunkInfo(chunkPos, newState, blockEntityCount, itemCount, lastItemCheck, unfreezeTime, addedTime);
    }

    public ChunkInfo withUnfreezeTime(long newUnfreezeTime) {
        return new ChunkInfo(chunkPos, state, blockEntityCount, itemCount, lastItemCheck, newUnfreezeTime, addedTime);
    }
    
    public boolean shouldUnfreeze() {
        return state == ChunkState.ITEM_FROZEN && unfreezeTime > 0 && System.currentTimeMillis() >= unfreezeTime;
    }
}