package com.klnon.recyclingservice.content.chunk;

/**
 * 区块状态枚举
 */
public enum ChunkState {
    UNMANAGED,          // 未管理
    MANAGED,            // 正常管理(有ticket)
    ITEM_FROZEN,        // 物品超标冻结(定时1小时)
    PERFORMANCE_FROZEN  // 性能冻结(动态解除)
}