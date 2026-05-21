package com.fish.worldgenfw;

import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.structure.StructureTemplateLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(WorldGenFw.MODID)
public class WorldGenFw {
    public static final String MODID = "worldgenfw";
    private static final Logger LOGGER = LogUtils.getLogger();

    public WorldGenFw(IEventBus modEventBus) {
        // 只注册游戏总线事件，不再需要 modEventBus
        NeoForge.EVENT_BUS.register(new ServerEventHandler());
    }

    /**
     * 静态内部类：处理游戏总线的生命周期事件，并直接实现资源重载接口。
     */
    private static class ServerEventHandler implements ResourceManagerReloadListener {

        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
            ResourceManager resourceManager = event.getServer().getResourceManager();
            ClusterConfig.getInstance().loadBlueprints(resourceManager);
            LOGGER.info("WorldGenFW blueprints loaded.");
        }

        /**
         * 实现 ResourceManagerReloadListener 接口，在每次资源重载时被调用。
         * 此方法是同步的，直接在主线程执行，无需通过事件动态注册监听器。
         */
        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            ClusterConfig.getInstance().loadBlueprints(resourceManager);
            StructureTemplateLoader.clearCache();
            LOGGER.info("WorldGenFW blueprints reloaded via ResourceManagerReloadListener.");
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            StructureTemplateLoader.clearCache();
            LOGGER.info("WorldGenFW template cache cleared.");
        }
    }
}