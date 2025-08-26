package com.klnon.recyclingservice.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.util.other.MessageSender;
import com.klnon.recyclingservice.Config;

/**
 * 优化的自动清理处理器 - 遵循KISS原则
 * 性能友好的实时警告系统
 */
public class AutoCleanupEvent {
    
    private static final int TICKS_PER_SECOND = 20;
    private static int ticks = 0;
    private static boolean cleaning = false;
    
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (++ticks < Config.getCleanIntervalTicks()) {
            if (ticks % TICKS_PER_SECOND == 0 && Config.SHOW_CLEANUP_WARNINGS.get()) {
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
        
        // 只在特定时间点发送警告
        if (remainingSeconds == 60 || remainingSeconds == 30 || 
           (remainingSeconds <= 10 && remainingSeconds >= 1)) {
            
            String message = Config.getWarningMessage(remainingSeconds);
            // 根据剩余时间选择消息类型
            MessageSender.MessageType messageType = remainingSeconds > 10 ? 
                MessageSender.MessageType.WARNING_NORMAL : 
                (remainingSeconds > 5 ? MessageSender.MessageType.WARNING_URGENT : MessageSender.MessageType.WARNING_CRITICAL);
            MessageSender.showActionBar(server, message, messageType.getColor());
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
                    Component message = Config.getDetailedCleanupMessage(result.dimensionStats());
                    MessageSender.sendChatMessage(server, message);
                }
                cleaning = false;
            })
            .exceptionally(e -> {
                MessageSender.showActionBar(server, Config.getCleanupFailedMessage(), MessageSender.MessageType.ERROR.getColor());
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