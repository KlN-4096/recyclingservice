package com.klnon.recyclingservice.content.cleanup.signal;

import com.klnon.recyclingservice.content.cleanup.entity.EntityReportCache;
import net.minecraft.server.MinecraftServer;

/**
 * 全局删除信号 - KISS原则实现
 */
public class GlobalDeleteSignal {
    
    private static volatile boolean deleteSignalActive = false;
    private static volatile long signalStartTick = 0;
    
    /**
     * 激活删除信号
     */
    public static void activate(MinecraftServer server) {
        deleteSignalActive = true;
        signalStartTick = server.getTickCount();
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
        boolean timeOut = (server.getTickCount() - signalStartTick) > 200; // 10秒=200tick
        boolean cacheEmpty = EntityReportCache.getTotalReportedCount() == 0;
        
        if (timeOut || cacheEmpty) {
            deleteSignalActive = false;
            return false;
        }
        
        return true;
    }

    public static boolean getSignal(){
        return deleteSignalActive;
    }
}