package com.klnon.recyclingservice.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.util.MessageUtils;
import com.klnon.recyclingservice.Config;

/**
 * 自动清理事件处理器 - 定时触发清理并显示警告
 */
public class AutoCleanupEvent {
    
    private static final int TICKS_PER_SECOND = 20;
    private static int ticks = 0;
    private static boolean cleaning = false;
    
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (++ticks < Config.getCleanIntervalTicks()) {
            if (ticks % TICKS_PER_SECOND == 0 && Config.GAMEPLAY.showCleanupWarnings.get()) {
                checkAndSendWarning(event.getServer());
            }
            return;
        }
        
        // 清理时间到
        ticks = 0;
        if (cleaning) return;
        
        cleaning = true;
        doCleanup(event.getServer());
    }
    
    /**
     * 检查并发送警告（仅在特定时间点）
     */
    private static void checkAndSendWarning(MinecraftServer server) {
        int remainingSeconds = getRemainingSeconds();
        
        // 使用配置的倒计时开始时间
        if (remainingSeconds <= Config.GAMEPLAY.warningCountdownStart.get() && remainingSeconds > 0) {
            String message = MessageUtils.getWarningMessage(remainingSeconds);
            MessageUtils.showActionBar(server, message, MessageUtils.MessageType.WARNING.getColor());
        }
    }
    

    /**
     * 执行清理
     */
    private static void doCleanup(MinecraftServer server) {
        CleanupService.performAutoCleanup(server)
            .thenAccept(result -> {
                // 如果有清理结果才显示消息
                if (result.totalItemsCleaned() > 0 || result.totalProjectilesCleaned() > 0) {
                    Component message = MessageUtils.getDetailedCleanupMessage(result.dimensionStats());
                    MessageUtils.sendChatMessage(server, message);
                }
                cleaning = false;
            })
            .exceptionally(e -> {
                MessageUtils.showActionBar(server, Config.MESSAGE.errorCleanupFailed.get(), MessageUtils.MessageType.ERROR.getColor());
                cleaning = false;
                return null;
            });
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

    /**
     * 获取距离下次清理的剩余秒数
     */
    public static int getRemainingSeconds() {
        return (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;
    }
}