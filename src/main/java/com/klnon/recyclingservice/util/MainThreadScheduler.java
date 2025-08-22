package com.klnon.recyclingservice.util;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * 主线程任务调度器 - 分片处理避免TPS下降
 * 
 * 设计原则：
 * - 每tick处理固定数量实体 (50个)
 * - 时间限制：2ms内完成
 * - 非阻塞：异步完成通知
 * - 保证TPS稳定性
 */
public class MainThreadScheduler {
    
    // 单例实例
    private static final MainThreadScheduler INSTANCE = new MainThreadScheduler();
    
    // 待删除实体队列
    private final Queue<EntityDeletionTask> deletionQueue = new ConcurrentLinkedQueue<>();
    
    // 配置常量
    private static final int MAX_ENTITIES_PER_TICK = 50;
    private static final long MAX_PROCESSING_TIME_NS = 2_000_000L; // 2ms
    
    private MainThreadScheduler() {}
    
    public static MainThreadScheduler getInstance() {
        return INSTANCE;
    }
    
    /**
     * 提交实体删除任务
     * @param entities 待删除的实体列表
     * @return CompletableFuture，任务完成时触发
     */
    public CompletableFuture<Void> scheduleEntityDeletion(List<Entity> entities) {
        if (entities.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        deletionQueue.offer(new EntityDeletionTask(entities, future));
        return future;
    }
    
    /**
     * 服务器tick事件处理 - 分片处理实体删除
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.processTickTasks();
    }
    
    private void processTickTasks() {
        long startTime = System.nanoTime();
        int processedCount = 0;
        
        while (!deletionQueue.isEmpty() && 
               processedCount < MAX_ENTITIES_PER_TICK && 
               (System.nanoTime() - startTime) <= MAX_PROCESSING_TIME_NS) {
            
            EntityDeletionTask task = deletionQueue.peek();
            if (task == null) break;
            
            if (task.processNext()) {
                processedCount++;
            } else {
                // 任务完成，移除并完成
                deletionQueue.poll();
                task.complete();
            }
        }
    }
    
    /**
     * 实体删除任务
     */
    private static class EntityDeletionTask {
        private final List<Entity> entities;
        private final CompletableFuture<Void> completionFuture;
        private int currentIndex = 0;
        
        public EntityDeletionTask(List<Entity> entities, CompletableFuture<Void> completionFuture) {
            this.entities = entities;
            this.completionFuture = completionFuture;
        }
        
        /**
         * 处理下一个实体
         * @return 如果还有更多实体需要处理返回true，否则返回false
         */
        public boolean processNext() {
            if (currentIndex >= entities.size()) {
                return false;
            }
            
            Entity entity = entities.get(currentIndex++);
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
            
            return currentIndex < entities.size();
        }
        
        /**
         * 完成任务
         */
        public void complete() {
            completionFuture.complete(null);
        }
    }
}