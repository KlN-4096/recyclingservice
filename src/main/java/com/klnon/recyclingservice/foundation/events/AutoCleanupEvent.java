package com.klnon.recyclingservice.foundation.events;

import com.klnon.recyclingservice.content.chunk.ChunkManager;
import com.klnon.recyclingservice.content.chunk.ChunkCache;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.cleanup.CleanupService;
import com.klnon.recyclingservice.content.cleanup.entity.EntityCache;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

/**
 * 自动清理事件处理器 - 定时触发清理并显示警告
 * 新增动态区块管理功能
 */
public class AutoCleanupEvent {

    private static final int TICKS_PER_SECOND = 20;
    private static int ticks = 0;
    private static boolean cleaning = false;
    private static boolean chunkOperationPending = false; // 区块操作待执行信号

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        
        // 检查区块操作执行条件：全局信号false + 区块操作信号true
        if (!CleanupManager.shouldDeleteEntity(event.getServer()) && chunkOperationPending) {
            performChunkOperations(event.getServer());
            chunkOperationPending = false; // 执行完后重置
            cleaning = false; // 整个清理流程完成
            return;
        }

        // 清理逻辑
        if (++ticks < Config.getCleanIntervalTicks()) {
            if (ticks % TICKS_PER_SECOND == 0 && Config.GAMEPLAY.showCleanupWarnings.get()) {
                //检查并发送警告（仅在特定时间点）
                int remainingSeconds = (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;

                // 使用配置的倒计时开始时间
                if (remainingSeconds <= Config.GAMEPLAY.warningCountdownStart.get() && remainingSeconds > 0) {
                    String message = MessageHelper.getWarningMessage(remainingSeconds);
                    MessageHelper.showActionBar(event.getServer(), message, MessageHelper.MessageType.WARNING.getColor());
                }
            }
            return;
        }

        // 清理时间到
        ticks = 0;
        if (cleaning) return;

        cleaning = true;
        doCleanup(event.getServer());
        chunkOperationPending = true; // 设置区块操作待执行信号
    }

    /**
     * 执行清理
     */
    private static void doCleanup(MinecraftServer server) {
        try {
            CleanupService.CleanupResult result = CleanupManager.performAutoCleanup(server);

            // 如果有清理结果才显示消息
            if (result.totalItemsCleaned() > 0 || result.totalProjectilesCleaned() > 0) {
                Component message = MessageHelper.getDetailedCleanupMessage(result.dimensionStats());
                MessageHelper.sendChatMessage(server, message);
            }

        } catch (Exception e) {
            MessageHelper.showActionBar(server, Config.MESSAGE.errorCleanupFailed.get(), MessageHelper.MessageType.ERROR.getColor());
            // 出错时重置所有信号
            chunkOperationPending = false;
            cleaning = false;
        }
        // 注意：成功时不在这里重置cleaning，等区块操作完成后再重置
    }

    // === 公共API方法 ===

    /**
     * 执行区块操作
     */
    private static void performChunkOperations(MinecraftServer server) {
        try {
            // 1. 先执行需要实体计数数据的操作
            if (Config.TECHNICAL.enableItemBasedFreezing.get()) {
                ChunkManager.performItemMonitoring(server);
            }
            
            // 2. 执行区块管理操作（不依赖实体计数）
            if (Config.TECHNICAL.enableChunkManagement.get()) {
                ChunkManager.performTakeover(server);
                ChunkManager.performPerformanceAdjustment(server);
            }
            
            // 3. 最后清空缓存（所有操作完成后）
            server.getAllLevels().forEach(level -> 
                ChunkCache.clearEntityCounts(level.dimension().location())
            );
            EntityCache.clearAll();
            
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("Chunk operations failed", e);
            // 出错时重置清理状态，避免卡死
            chunkOperationPending = false;
            cleaning = false;
        }
    }

    /**
     * 手动触发清理
     */
    public static void manualClean(MinecraftServer server) {
        if (!cleaning) {
            cleaning = true;
            doCleanup(server);
            chunkOperationPending = true; // 手动清理也要设置区块操作信号
        }
    }
}