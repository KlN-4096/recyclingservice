package com.klnon.recyclingservice;

import com.klnon.recyclingservice.compact.clientsort.ClientSortPolicyCompat;
import com.klnon.recyclingservice.foundation.command.BinCommand;
import com.klnon.recyclingservice.foundation.events.AutoCleanupEvent;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * Recycling Service Mod - 自动清理与垃圾箱系统
 */
@Mod(Recyclingservice.MODID)
public class Recyclingservice {

    public static final String MODID = "recyclingservice";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Recyclingservice(ModContainer modContainer) {
        // 注册游戏事件监听
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(AutoCleanupEvent.class);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info("[RecyclingService] Mod initialized");
    }

    /**
     * 服务器启动时初始化配置缓存
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Config.updateCaches();
        LOGGER.info("[RecyclingService] Config caches loaded");
        ClientSortPolicyCompat.disableTrashBoxPolicyIfPresent();
    }

    /**
     * 注册命令
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BinCommand.register(event.getDispatcher());
        LOGGER.debug("[RecyclingService] Commands registered");
    }
}