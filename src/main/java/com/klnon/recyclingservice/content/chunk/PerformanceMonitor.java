package com.klnon.recyclingservice.content.chunk;

import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务器性能监控器
 * 负责服务器tick时间监控和性能阈值判断
 */
public class PerformanceMonitor {
    
    /**
     * 获取服务器平均tick时间（MSPT）
     * @param server 服务器实例
     * @return 平均tick时间（毫秒）
     */
    public static double getAverageTickTime(MinecraftServer server) {
        try {
            ResourceLocation overworldKey = ResourceLocation.parse("minecraft:overworld");
            long[] recentTicks = server.getTickTime(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, overworldKey));
            
            if (recentTicks == null || recentTicks.length == 0) {
                return 50.0;
            }
            
            long sum = 0;
            int count = 0;
            for (int i = recentTicks.length - 1; i >= Math.max(0, recentTicks.length - 20); i--) {
                if (recentTicks[i] > 0) {
                    sum += recentTicks[i];
                    count++;
                }
            }
            
            if (count == 0) {
                return 50.0;
            }
            
            return ((double) sum / count) / 1_000_000.0;
            
        } catch (Exception e) {
            return 50.0;
        }
    }
    
    /**
     * 计算当前TPS
     */
    public static double calculateTPS(double averageTickTime) {
        return Math.min(20.0, 1000.0 / averageTickTime);
    }
}