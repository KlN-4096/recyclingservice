package com.klnon.recyclingservice.foundation.events;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.cleanup.CleanupController;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 自动清理事件处理器 - 定时触发清理并显示警告
 */
public class AutoCleanupEvent {

    private static final int TICKS_PER_SECOND = 20;

    private static int ticks = 0;
    private static final CleanupJob CLEANUP_JOB = new CleanupJob();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        try {
            MinecraftServer server = event.getServer();

            if (CLEANUP_JOB.isRunning()) {
                CLEANUP_JOB.tick(server);
                return;
            }
            // 倒计时
            if (++ticks < Config.getCleanIntervalTicks()) {
                if (ticks % TICKS_PER_SECOND == 0 && Config.GAMEPLAY.showCleanupWarnings.get()) {
                    //检查并发送警告（仅在特定时间点）
                    int remainingSeconds = (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;

                    // 使用配置的倒计时开始时间
                    if (remainingSeconds <= Config.GAMEPLAY.warningCountdownStart.get() && remainingSeconds > 0) {
                        String message = MessageHelper.getWarningMessage(remainingSeconds);
                        MessageHelper.sendActionBarToAll(server, message, MessageHelper.COLOR_WARNING);
                    }
                }
                return;
            }

            // 清理时间到
            ticks = 0;
            CLEANUP_JOB.start();
        } catch (Exception e) {
            Recyclingservice.LOGGER.error("RecyclingService failed", e);
            // 出错时重置清理状态，避免卡死
            CLEANUP_JOB.reset();
        }
    }

    /**
     * 执行清理
     */
    public static void doCleanup() {
        ticks = 0;
        CLEANUP_JOB.start();
    }

    /**
     * CleanupJob 是清理流程的状态机：
     * 1) PREPARE：执行清理前置动作并激活删除信号、发送消息；
     * 2) WAIT_DELETE：持续轮询删除信号，直到超时或缓存清空后结束；
     * 3) IDLE：空闲等待下一次触发。
     * 作用是防止倒计时触发时重复进入清理逻辑，并让清理过程可控。
     */
    private static class CleanupJob {
        private enum Phase {
            IDLE,
            PREPARE,
            WAIT_DELETE
        }

        private Phase phase = Phase.IDLE;
        private Component pendingMessage = null;

        private boolean isRunning() {
            return phase != Phase.IDLE;
        }

        /**
         * 进入 PREPARE 阶段：
         * 只有在当前处于 IDLE 时才会切换，避免重复触发。
         */
        private void start() {
            if (phase != Phase.IDLE) {
                return;
            }
            phase = Phase.PREPARE;
        }

        private void tick(MinecraftServer server) {
            switch (phase) {
                case PREPARE -> prepare(server);
                case WAIT_DELETE -> waitForDelete(server);
                default -> {
                }
            }
        }

        /**
         * 执行 PREPARE 阶段的实际清理动作，并进入 WAIT_DELETE 阶段。
         */
        private void prepare(MinecraftServer server) {
            CleanupManager.pruneExpiredCache();
            pendingMessage = null;
            if (CleanupManager.getTotalReportedCount() > 0) {
                pendingMessage = MessageHelper.buildCleanupResultMessage(server);
            }
            TrashBoxManager.clearAll();
            TrashBoxManager.resetExtractHistory();
            CleanupController.activate(server);
            phase = Phase.WAIT_DELETE;
        }

        /**
         * 进入 WAIT_DELETE 阶段：
         * 每 tick 轮询删除信号状态，当信号关闭（超时或缓存清空）则回到 IDLE。
         */
        private void waitForDelete(MinecraftServer server) {
            CleanupController.shouldDelete(server);
            if (!CleanupController.isDeleteSignalActive()) {
                if (pendingMessage != null) {
                    MessageHelper.sendToAll(server, pendingMessage);
                }
                reset();
            }
        }

        private void reset() {
            phase = Phase.IDLE;
            pendingMessage = null;
        }
    }
}
