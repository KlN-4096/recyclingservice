package com.klnon.recyclingservice.content.chunk.service;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import com.klnon.recyclingservice.content.chunk.function.TicketManager;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;

/**
 * 物品超载冻结功能类
 * 物品监控 ，直接处理EntityCache统计的超载区块
 */
public class ChunkItemMonitoring {
    public static void performItemMonitoring(MinecraftServer server) {
        try {
            int totalFrozenCount = 0;
            int unfrozenCount = 0;

            // 处理所有维度的超载区块
            for (ServerLevel level : server.getAllLevels()) {
                ResourceLocation dimension = level.dimension().location();

                // 获取超载区块
                List<ChunkPos> overloadedChunks = ChunkDataCache.getOverloadedChunks(dimension);

                // 发送警告消息（如果启用）
                if (Config.TECHNICAL.enableChunkItemWarning.get()) {
                    sendItemWarningMessages(server, dimension, overloadedChunks);
                }

                // 冻结超载区块（包括扩散冻结）
                for (ChunkPos chunkPos : overloadedChunks) {
                    totalFrozenCount += TicketManager.freezeChunkWithRadius(dimension, chunkPos, level);
                }

                // 检查已冻结的区块是否应该解冻
                unfrozenCount += unfreezeExpiredChunks(dimension, level);

                //当警告+清扫完毕后清除区块物品数量缓存
                ChunkDataCache.clearEntityCounts(dimension);
            }

            Recyclingservice.LOGGER.info("Item monitoring completed: {} frozen, {} unfrozen",
                    totalFrozenCount, unfrozenCount);

        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to perform item monitoring", e);
        }
    }

    /**
     * 发送物品超载警告消息给所有玩家
     */
    private static void sendItemWarningMessages(MinecraftServer server, ResourceLocation dimension, List<ChunkPos> overloadedChunks) {
        if (overloadedChunks.isEmpty()) return;

        try {
            // 获取该维度所有区块的实体数量统计
            Map<ChunkPos, Integer> entityCountMap = ChunkDataCache.getEntityCountByDimension(dimension);

            for (ChunkPos chunkPos : overloadedChunks) {
                int itemCount = entityCountMap.getOrDefault(chunkPos, 0);

                if (itemCount > 0) {
                    Component message = MessageHelper.getItemWarningMessage(itemCount,
                            MessageHelper.toWorldPos(chunkPos.x), MessageHelper.toWorldPos(chunkPos.z));

                    // 发送给所有玩家（在聊天栏显示）
                    MessageHelper.sendChatToAll(server, message);
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to send item warning messages for {}", dimension, e);
        }
    }

    private static int unfreezeExpiredChunks(ResourceLocation dimension, ServerLevel level) {
        int unfrozenCount = 0;

        try {
            // 获取所有物品冻结的区块
            List<ChunkPos> frozenChunks = ChunkDataCache.getChunksByState(dimension, ChunkDataCache.ITEM_FROZEN)
                    .stream()
                    .map(ChunkDataCache.ChunkInfo::pos)
                    .toList();

            for (ChunkPos chunkPos : frozenChunks) {
                // 检查是否到期
                if (ChunkDataCache.shouldUnfreezeItemFrozenChunk(dimension, chunkPos)) {
                    // 解冻：恢复管理
                    if (TicketManager.unfreezeChunk(dimension, chunkPos, level)) {
                        unfrozenCount++;
                    }
                }
            }
        } catch (Exception e) {
            Recyclingservice.LOGGER.debug("Failed to unfreeze expired chunks for {}", dimension, e);
        }

        return unfrozenCount;
    }
}
