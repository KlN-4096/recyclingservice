package com.klnon.recyclingservice;

import com.klnon.recyclingservice.content.chunk.ChunkManager;
import com.klnon.recyclingservice.foundation.events.AutoCleanupEvent;
import com.klnon.recyclingservice.foundation.commadn.BinCommand;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// 这里的值应该与 META-INF/neoforge.mods.toml 文件中的条目匹配
@Mod(Recyclingservice.MODID)
public class Recyclingservice {
    // 在公共位置定义模组 ID，供所有内容引用
    public static final String MODID = "recyclingservice";
    // 直接引用 slf4j 日志记录器
    public static final Logger LOGGER = LogUtils.getLogger();

    // 模组类的构造函数是在加载模组时运行的第一段代码。
    // FML 将识别一些参数类型（如 IEventBus 或 ModContainer）并自动传入。
    public Recyclingservice(IEventBus modEventBus, ModContainer modContainer) {
        // 为模组加载注册 commonSetup 方法
        modEventBus.addListener(this::commonSetup);

        // 为我们感兴趣的服务器和其他游戏事件注册自己。
        // 请注意，只有当我们希望 *这个* 类（Recyclingservice）直接响应事件时，这才是必要的。
        // 如果此类中没有带有 @SubscribeEvent 注解的函数（如下面的 onServerStarting()），请不要添加此行。
        NeoForge.EVENT_BUS.register(this);

        // 注册自动清理事件处理器
        NeoForge.EVENT_BUS.register(AutoCleanupEvent.class);

        // 注册我们模组的 ModConfigSpec，以便 FML 可以为我们创建和加载配置文件
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 一些公共设置代码
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    // 您可以使用 SubscribeEvent 并让事件总线发现要调用的方法
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 在服务器启动时做一些事情
        LOGGER.info("HELLO from server starting");
        
        // 初始化性能优化缓存
        Config.updateCaches();
        LOGGER.info("Performance caches initialized");
    }
    
    // 服务器启动完成事件 - 执行启动区块清理
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Server fully started, performing startup chunk cleanup");
        
        // 执行启动区块接管
        ChunkManager.performStartupTakeover(event.getServer());
    }
    
    // 注册命令事件
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering bin command");
        BinCommand.register(event.getDispatcher());
    }
}
