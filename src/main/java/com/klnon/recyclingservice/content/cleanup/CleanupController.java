package com.klnon.recyclingservice.content.cleanup;

import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.service.TrashPaymentHandler;
import net.minecraft.server.MinecraftServer;

/**
 * 全局删除信号 - KISS原则实现
 */
public class CleanupController {

    private static volatile boolean deleteSignalActive = false;
    private static volatile long signalStartTick = 0;

    /**
     * 执行自动清理
     */
    public static void performAutoCleanup(MinecraftServer server) {
        TrashBoxManager.clearAll();
        TrashPaymentHandler.resetExtractHistory();
        activate(server);
    }

    /**
     * 激活删除信号
     */
    public static void activate(MinecraftServer server) {
        deleteSignalActive = true;
        signalStartTick = server.getTickCount();
    }

    public static boolean isDeleteSignalActive() {
        return deleteSignalActive;
    }
    
    /**
     * 检查是否应该删除
     * 双重条件：缓存清空 OR 5秒超时
     */
    public static boolean shouldDelete(MinecraftServer server) {
        if (!deleteSignalActive) {
            return false;
        }
        
        // 检查关闭条件
        boolean timeOut = (server.getTickCount() - signalStartTick) > 100; // 5秒=100tick
        boolean cacheEmpty = CleanupManager.getTotalReportedCount() == 0;
        
        if (timeOut || cacheEmpty) {
            CleanupManager.clearAll();
            deleteSignalActive = false;
            return false;
        }
        
        return true;
    }
}
