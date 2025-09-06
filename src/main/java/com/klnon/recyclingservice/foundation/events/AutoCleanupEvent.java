package com.klnon.recyclingservice.foundation.events;

import com.klnon.recyclingservice.content.chunk.ChunkManager;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.cleanup.CleanupService;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.Config;

/**
 * 自动清理事件处理器 - 定时触发清理并显示警告
 * 新增动态区块管理功能
 */
public class AutoCleanupEvent {

    private static final int TICKS_PER_SECOND = 200;
    private static int ticks = 0;
    private static boolean cleaning = false;

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {

        // 清理逻辑
        if (++ticks < Config.getCleanIntervalTicks()) {
            if (ticks % TICKS_PER_SECOND == 0 && Config.GAMEPLAY.showCleanupWarnings.get()) {
                //检查并发送警告（仅在特定时间点）
                int remainingSeconds = (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;;

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
        //同步管理区块,物品过多监控
        if (Config.TECHNICAL.enableDynamicChunkManagement.get())
            ChunkManager.performPerformanceAdjustment(event.getServer());
        if (Config.TECHNICAL.enableItemBasedFreezing.get())
            ChunkManager.performItemMonitoring(event.getServer());
        doCleanup(event.getServer());
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
        } finally {
            cleaning = false;
        }
    }

    // === 公共API方法 ===

    /**
     * 手动触发清理
     */
    public static void manualClean(MinecraftServer server) {
        if (!cleaning) {
            cleaning = true;
            doCleanup(server);
        }
    }
}