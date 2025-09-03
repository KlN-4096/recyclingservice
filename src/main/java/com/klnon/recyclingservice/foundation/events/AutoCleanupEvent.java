package com.klnon.recyclingservice.foundation.events;

import com.klnon.recyclingservice.content.chunk.freezer.ChunkFreezer;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
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
    
    private static final int TICKS_PER_SECOND = 20;
    private static int ticks = 0;
    private static boolean cleaning = false;
    
    // 动态区块管理计时器
    private static int dynamicManagementTicks = 0;
    private static long lastSuspendCheck = 0;
    private static long lastRestoreCheck = 0;
    
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        // 动态区块管理
        dynamicManagementTicks++;
        if (dynamicManagementTicks % TICKS_PER_SECOND == 0) { // 每秒检查一次
            checkDynamicChunkManagement(event.getServer());
        }
        
        // 原有的清理逻辑
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
     * 检查动态区块管理 - 根据配置间隔执行性能检查和区块调整
     */
    private static void checkDynamicChunkManagement(MinecraftServer server) {
        if (!Config.TECHNICAL.enableDynamicChunkManagement.get()) {
            return;
        }
        
        long currentTimeSeconds = dynamicManagementTicks / TICKS_PER_SECOND;
        int restoreInterval = Config.TECHNICAL.restoreCheckInterval.get() * 60; // 转换为秒
        int suspendInterval = Config.TECHNICAL.suspendCheckInterval.get() * 60; // 转换为秒
        
        // 检查是否需要进行恢复检查（更频繁）
        boolean shouldCheckRestore = (currentTimeSeconds - lastRestoreCheck) >= restoreInterval;
        
        // 检查是否需要进行暂停检查（较少频繁）
        boolean shouldCheckSuspend = (currentTimeSeconds - lastSuspendCheck) >= suspendInterval;
        
        // 执行检查的条件：到了恢复检查时间，或者到了暂停检查时间
        if (shouldCheckRestore || shouldCheckSuspend) {
            ChunkFreezer.performDynamicChunkManagement(server);
            
            if (shouldCheckRestore) {
                lastRestoreCheck = currentTimeSeconds;
            }
            
            if (shouldCheckSuspend) {
                lastSuspendCheck = currentTimeSeconds;
            }
        }
    }
    
    /**
     * 检查并发送警告（仅在特定时间点）
     */
    private static void checkAndSendWarning(MinecraftServer server) {
        int remainingSeconds = getRemainingSeconds();
        
        // 使用配置的倒计时开始时间
        if (remainingSeconds <= Config.GAMEPLAY.warningCountdownStart.get() && remainingSeconds > 0) {
            String message = MessageHelper.getWarningMessage(remainingSeconds);
            MessageHelper.showActionBar(server, message, MessageHelper.MessageType.WARNING.getColor());
        }
    }
    

    /**
     * 执行清理
     */
    private static void doCleanup(MinecraftServer server) {
        try {
            CleanupManager.CleanupResult result = CleanupManager.performAutoCleanup(server);
            
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

    /**
     * 获取距离下次清理的剩余秒数
     */
    public static int getRemainingSeconds() {
        return (Config.getCleanIntervalTicks() - ticks) / TICKS_PER_SECOND;
    }
}