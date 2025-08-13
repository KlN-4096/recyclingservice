package com.klnon.recyclingservice;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// 此类不会在专用服务器上加载。从这里访问客户端代码是安全的。
@Mod(value = Recyclingservice.MODID, dist = Dist.CLIENT)
// 您可以使用 EventBusSubscriber 来自动注册类中所有带有 @SubscribeEvent 注解的静态方法
@EventBusSubscriber(modid = Recyclingservice.MODID, value = Dist.CLIENT)
public class RecyclingserviceClient {
    public RecyclingserviceClient(ModContainer container) {
        // 允许 NeoForge 为此模组的配置创建配置界面。
        // 配置界面可通过转到模组界面 > 点击您的模组 > 点击配置来访问。
        // 不要忘记为您的配置选项添加翻译到 en_us.json 文件中。
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // 一些客户端设置代码
        Recyclingservice.LOGGER.info("HELLO FROM CLIENT SETUP");
        Recyclingservice.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
