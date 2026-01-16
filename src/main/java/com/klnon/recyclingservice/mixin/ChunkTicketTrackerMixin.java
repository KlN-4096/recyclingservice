package com.klnon.recyclingservice.mixin;

import com.klnon.recyclingservice.content.chunk.ChunkDataCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * DistanceManager内部类ChunkTicketTracker的Mixin
 * 通过拦截getLevelFromSource方法，使冻结的区块不被加载
 */
@Mixin(targets = "net.minecraft.server.level.DistanceManager$ChunkTicketTracker")
public class ChunkTicketTrackerMixin {
    
    /**
     * 拦截区块加载等级计算
     * 如果区块在ChunkDataCache中标记为冻结，返回高值阻止加载
     */
    @Inject(method = "getLevelFromSource", at = @At("RETURN"), cancellable = true)
    private void checkFrozenChunk(long chunkPos, CallbackInfoReturnable<Integer> cir) {
        try {
            // 获取当前返回值
            int currentLevel = cir.getReturnValue();
            
            // 如果已经是不加载的等级，无需处理
            if (currentLevel > 33) return;
            
            // 获取外部类DistanceManager实例
            Field outerField = this.getClass().getDeclaredField("this$0");
            outerField.setAccessible(true);
            DistanceManager distanceManager = (DistanceManager) outerField.get(this);
            
            // 再获取ChunkMap
            Field chunkMapField = DistanceManager.class.getDeclaredField("this$0");
            chunkMapField.setAccessible(true);
            ChunkMap chunkMap = (ChunkMap) chunkMapField.get(distanceManager);
            
            // 获取ServerLevel
            ServerLevel level = chunkMap.level;
            ResourceLocation dimension = level.dimension().location();
            
            // 解析坐标
            ChunkPos pos = new ChunkPos(chunkPos);
            
            // 查询ChunkDataCache
            ChunkDataCache.ChunkInfo info = ChunkDataCache.getChunkInfo(dimension, pos);
            
            // 判断是否需要冻结
            if (info != null && 
                (info.state() == ChunkDataCache.UNIMPORTANT ||
                 info.state() == ChunkDataCache.ITEM_FROZEN || 
                 info.state() == ChunkDataCache.PERFORMANCE_FROZEN)) {
                // 返回34确保不加载（阈值是33）
                cir.setReturnValue(34);
            }
        } catch (Exception e) {
            // 静默处理异常
        }
    }
}