package com.klnon.recyclingservice.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.Config;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 自动清理事件处理器 - 使用Timer简化定时清理
 * 
 * KISS原则实现：
 * - 使用标准Timer替代复杂的tick计数
 * - 简化警告系统为30秒和5秒两个关键时间点
 * - 异步清理处理，不阻塞服务器主线程
 */
public class AutoCleanupHandler {
    
    private static Timer cleanupTimer;
    private static Timer warningTimer;
    private static MinecraftServer serverInstance;
    private static final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);
    
    /**
     * 服务器启动时初始化定时器
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        serverInstance = event.getServer();
        startCleanupTimer();
    }
    
    /**
     * 服务器停止时清理定时器
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        stopAllTimers();
        serverInstance = null;
    }
    
    /**
     * 启动清理定时器 - KISS原则简化实现
     */
    private static void startCleanupTimer() {
        stopAllTimers(); // 确保没有重复的定时器
        
        int intervalSeconds = Config.AUTO_CLEAN_TIME.get();
        long intervalMs = intervalSeconds * 1000L;
        
        cleanupTimer = new Timer("AutoCleanup-Timer", true);
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (serverInstance != null) {
                    scheduleWarningsAndCleanup();
                }
            }
        }, intervalMs, intervalMs);
    }
    
    /**
     * 安排警告和清理 - 60s, 30s, 10s到1s倒计时
     */
    private static void scheduleWarningsAndCleanup() {
        if (!Config.SHOW_CLEANUP_WARNINGS.get()) {
            // 不显示警告，直接清理
            executeCleanup();
            return;
        }
        
        warningTimer = new Timer("Warning-Timer", true);
        
        // 60秒警告
        scheduleWarning(60);
        
        // 30秒警告
        warningTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                scheduleWarning(30);
            }
        }, 30 * 1000L); // 30秒后
        
        // 10秒到1秒倒计时警告
        for (int i = 10; i >= 1; i--) {
            final int seconds = i;
            long delay = (60 - seconds) * 1000L; // 计算延迟时间
            warningTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    scheduleWarning(seconds);
                }
            }, delay);
        }
        
        // 执行清理
        warningTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                executeCleanup();
            }
        }, 60 * 1000L); // 60秒后清理
    }
    
    /**
     * 发送警告消息
     */
    private static void scheduleWarning(int seconds) {
        if (serverInstance != null) {
            String message = Config.getWarningMessage(seconds);
            broadcastMessage(message);
        }
    }
    
    /**
     * 执行清理任务 - 简化版本
     */
    private static void executeCleanup() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            return; // 清理已在进行中
        }
        
        if (serverInstance == null) {
            cleanupInProgress.set(false);
            return;
        }
        
        // 调用异步清理服务
        CleanupService.performAutoCleanup(serverInstance)
            .thenAccept(result -> {
                try {
                    String message = result.getFormattedMessage();
                    broadcastMessage(message);
                } catch (Exception e) {
                    broadcastMessage("§c[Auto Clean] Cleanup completed but failed to show details.");
                } finally {
                    cleanupInProgress.set(false);
                }
            })
            .exceptionally(throwable -> {
                try {
                    String errorMessage = "§c[Auto Clean] Cleanup failed: " + throwable.getMessage();
                    broadcastMessage(errorMessage);
                } finally {
                    cleanupInProgress.set(false);
                }
                return null;
            });
    }
    
    /**
     * 向所有在线玩家广播消息
     */
    private static void broadcastMessage(String message) {
        if (serverInstance == null || message == null || message.isEmpty()) {
            return;
        }
        
        try {
            Component component = Component.literal(message);
            for (ServerPlayer player : serverInstance.getPlayerList().getPlayers()) {
                try {
                    player.sendSystemMessage(component);
                } catch (Exception e) {
                    // 单个玩家发送失败不影响其他玩家
                }
            }
        } catch (Exception e) {
            // 消息创建失败
        }
    }
    
    /**
     * 停止所有定时器
     */
    private static void stopAllTimers() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
            cleanupTimer = null;
        }
        if (warningTimer != null) {
            warningTimer.cancel();
            warningTimer = null;
        }
    }
    
    /**
     * 手动触发清理
     */
    public static boolean triggerManualCleanup() {
        if (cleanupInProgress.get() || serverInstance == null) {
            return false;
        }
        executeCleanup();
        return true;
    }
    
    /**
     * 检查清理是否正在进行中
     */
    public static boolean isCleanupInProgress() {
        return cleanupInProgress.get();
    }
}