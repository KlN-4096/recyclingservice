package com.klnon.recyclingservice.foundation.events;

import com.klnon.recyclingservice.content.chunk.ChunkManager;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
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
    //同时也是区块操作待执行信号
    private static boolean cleaning = false;

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        try{
            // 检查区块操作执行条件：全局信号false + cleaning信号表示在清扫后进行
            if (!CleanupManager.shouldDeleteEntity(event.getServer()) && cleaning) {
                performChunkOperations(event.getServer());
            }
            // 倒计时
            if (++ticks < Config.getCleanIntervalTicks()) {
                if (ticks % TICKS_PER_SECOND == 0 && Config.GAMEPLAY.showCleanupWarnings.get()) {
                    //检查并发送警告（仅在特定时间点）
                    int remainingSeconds = (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;

                    // 使用配置的倒计时开始时间
                    if (remainingSeconds <= Config.GAMEPLAY.warningCountdownStart.get() && remainingSeconds > 0) {
                        String message = MessageHelper.getWarningMessage(remainingSeconds);
                        MessageHelper.sendActionBarToAll(event.getServer(), message, MessageHelper.WARNING);
                    }
                }
                return;
            }

            // 清理时间到
            ticks = 0;
            if (!cleaning)
                doCleanup(event.getServer());
        }catch (Exception e){
            Recyclingservice.LOGGER.error("RecyclingService failed", e);
            // 出错时重置清理状态，避免卡死
            cleaning = false;
        }

    }

    /**
     * 执行清理
     */
    public static void doCleanup(MinecraftServer server) {
        if (!cleaning) {
            cleaning = true;
            int totalItemsBefore = CleanupManager.getAllEntityCount(CleanupManager.ITEM);
            int totalProjectilesBefore = CleanupManager.getAllEntityCount(CleanupManager.PROJECTILE);
            CleanupManager.performAutoCleanup(server);

            // 如果有清理结果才显示消息
            if (totalItemsBefore + totalProjectilesBefore > 0) {
                Component message = MessageHelper.getDetailedCleanupMessage(server);
                MessageHelper.sendChatToAll(server, message);
            }
        }
    }

    // === 公共API方法 ===

    /**
     * 执行区块操作
     */
    private static void performChunkOperations(MinecraftServer server) {
        // 1. 先执行需要实体计数数据的操作
        /*
        if (Config.TECHNICAL.enableItemBasedFreezing.get()) {
            ChunkManager.performItemMonitoring(server);
        }

        // 2. 执行区块管理操作（不依赖实体计数）
        if (Config.TECHNICAL.enableChunkManagement.get()) {
            ChunkManager.performTakeover(server);
            ChunkManager.performPerformanceAdjustment(server);
        }
        */
        cleaning = false; // 整个清理流程完成
    }
}
