package com.klnon.recyclingservice.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.service.CleanupService;
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
        int remainingSeconds = (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;
        
        // 只在特定时间点发送警告
        if (remainingSeconds == 60 || remainingSeconds == 30 || 
           (remainingSeconds <= 10 && remainingSeconds >= 1)) {
            
            String message = Config.getWarningMessage(remainingSeconds);
            int color = getWarningColor(remainingSeconds);
            showActionBar(server, message, color);
        }
    }
    
    /**
     * 根据剩余时间获取警告颜色
     */
    private static int getWarningColor(int seconds) {
        if (seconds > 10) return 0xFFCC00;      // 黄色
        if (seconds > 5) return 0xFF6600;       // 橙色
        return 0xFF3300;                        // 红色
    }
    
    /**
     * 显示ActionBar消息
     */
    private static void showActionBar(MinecraftServer server, String message, int color) {
        Component component = Component.literal(message).withStyle(style -> 
            style.withColor(color).withBold(true));
        
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(component);
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }
    
    /**
     * 执行清理
     */
    private static void doCleanup(MinecraftServer server) {
        CleanupService.performAutoCleanup(server)
            .thenAccept(result -> {
                int totalCleaned = result.getTotalItemsCleaned() + result.getTotalProjectilesCleaned();
                String message = Config.getCleanupCompleteMessage(totalCleaned);
                showActionBar(server, message, 0x00FF00); // 绿色
                cleaning = false;
            })
            .exceptionally(e -> {
                showActionBar(server, "§c清理失败", 0xFF0000); // 红色
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
     * 获取清理状态
     */
    public static boolean isCleanupInProgress() {
        return cleaning;
    }
    
    /**
     * 获取距离下次清理的剩余秒数
     */
    public static int getRemainingSeconds() {
        return (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;
    }
    
    /**
     * 重置清理计时器（用于命令或特殊情况）
     */
    public static void resetTimer() {
        ticks = 0;
        cleaning = false;
    }
}