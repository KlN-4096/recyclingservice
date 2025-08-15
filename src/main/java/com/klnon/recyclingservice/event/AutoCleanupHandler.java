package com.klnon.recyclingservice.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.service.CleanupService;
import com.klnon.recyclingservice.Config;

/**
 * 超简化自动清理处理器
 * 性能友好 + 代码最少 + KISS原则
 */
public class AutoCleanupHandler {
    
    private static final int TICKS_PER_SECOND = 20;
    private static int ticks = 0;
    private static boolean cleaning = false;
    
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (++ticks < Config.getCleanIntervalTicks()) return;
        
        ticks = 0; // 重置计数器
        if (cleaning) return; // 防重复
        
        cleaning = true;
        MinecraftServer server = event.getServer();
        
        // 警告（如果启用）
        if (Config.SHOW_CLEANUP_WARNINGS.get()) {
            scheduleWarnings(server);
        } else {
            doCleanup(server);
        }
    }
    
    private static void scheduleWarnings(MinecraftServer server) {
        int currentTick = (int) server.getTickCount();
        
        // 60秒警告
        server.tell(new TickTask(currentTick + 1, 
            () -> broadcast(server, Config.getWarningMessage(60))));
        
        // 30秒警告
        server.tell(new TickTask(currentTick + 30 * TICKS_PER_SECOND,
            () -> broadcast(server, Config.getWarningMessage(30))));
        
        // 10秒到1秒倒计时
        for (int i = 10; i >= 1; i--) {
            final int seconds = i;
            int delay = (60 - seconds) * TICKS_PER_SECOND; // 计算延迟tick数
            server.tell(new TickTask(currentTick + delay,
                () -> broadcast(server, Config.getWarningMessage(seconds))));
        }
        
        // 60秒后执行清理
        server.tell(new TickTask(currentTick + 60 * TICKS_PER_SECOND,
            () -> doCleanup(server)));
    }
    
    private static void doCleanup(MinecraftServer server) {
        CleanupService.performAutoCleanup(server)
            .thenAccept(result -> {
                broadcast(server, result.getFormattedMessage());
                cleaning = false;
            })
            .exceptionally(e -> {
                broadcast(server, "§c清理失败");
                cleaning = false;
                return null;
            });
    }
    
    private static void broadcast(MinecraftServer server, String msg) {
        if (msg == null || server == null) return;
        Component text = Component.literal(msg);
        server.getPlayerList().getPlayers()
            .forEach(p -> p.sendSystemMessage(text));
    }
    
    // 手动清理
    public static void manualClean(MinecraftServer server) {
        if (!cleaning) {
            cleaning = true;
            doCleanup(server);
        }
    }
    
    // 获取清理状态
    public static boolean isCleanupInProgress() {
        return cleaning;
    }
    
    // 获取距离下次清理的剩余时间
    public static int getRemainingSeconds() {
        int intervalTicks = Config.getCleanIntervalTicks();
        int remainingTicks = intervalTicks - ticks;
        return remainingTicks / TICKS_PER_SECOND;
    }
}