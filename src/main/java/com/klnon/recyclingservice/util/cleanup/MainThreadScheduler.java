package com.klnon.recyclingservice.util.cleanup;

import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.klnon.recyclingservice.Config;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * 主线程任务调度器 - 分片处理避免TPS下降
 * 每tick限制处理数量和时间，异步完成通知
 */
public class MainThreadScheduler {
    
    // 单例实例
    private static final MainThreadScheduler INSTANCE = new MainThreadScheduler();
    
    // 待删除实体队列
    private final Queue<EntityDeletionTask> deletionQueue = new ConcurrentLinkedQueue<>();
    
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
        
        // 从配置获取动态值
        int maxEntitiesPerTick = Config.TECHNICAL.batchSize.get();
        long maxProcessingTimeNs = Config.TECHNICAL.maxProcessingTimeMs.get() * 1_000_000L;
        
        while (!deletionQueue.isEmpty() && 
               processedCount < maxEntitiesPerTick && 
               (System.nanoTime() - startTime) <= maxProcessingTimeNs) {
            
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
                // 使用server.execute延迟到安全时机删除，避免ConcurrentModificationException
                entity.getServer().execute(() -> {
                    if (entity.isAlive()) {
                        entity.discard();
                    }
                });
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