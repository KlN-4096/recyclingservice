package com.klnon.recyclingservice.util;

import com.klnon.recyclingservice.Recyclingservice;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class UiChoose {
    public static boolean hasModInstalled(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        
        return ErrorHandler.handleStaticOperation("modDetection", () -> {
            // 检查客户端是否注册了我们的mod网络通道
            ResourceLocation modChannel = ResourceLocation.fromNamespaceAndPath(
                Recyclingservice.MODID, "main"
            );
            
            // NeoForge网络通道检测
            return serverPlayer.connection.hasChannel(modChannel);
        }, false); // 如果检测失败，默认认为客户端无mod（安全策略）
    }
}
