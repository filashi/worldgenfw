package com.fish.worldgenfw;

import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.structure.StructureTemplateLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(WorldGenFw.MODID)
public class WorldGenFw {
    public static final String MODID = "worldgenfw";
    private static final Logger LOGGER = LogUtils.getLogger();

    public WorldGenFw(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(new ServerEventHandler());
    }

    private static class ServerEventHandler {
        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
            ResourceManager resourceManager = event.getServer().getResourceManager();
            ClusterConfig.getInstance().loadBlueprints(resourceManager);
            LOGGER.info("WorldGenFW blueprints loaded.");
        }

        @SubscribeEvent
        public void onAddReloadListener(AddReloadListenerEvent event) {
            event.addListener((barrier, resourceManager, prepProfiler, reloadProfiler, backgroundExecutor, gameExecutor) -> {
                // 关键：将操作提交到同步执行器，避免和 ModernFix 等优化模组的重载流水线产生死锁
                gameExecutor.execute(() -> {
                    ClusterConfig.getInstance().loadBlueprints(resourceManager);
                    StructureTemplateLoader.clearCache();
                    LOGGER.info("WorldGenFW blueprints reloaded.");
                });
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            });
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            StructureTemplateLoader.clearCache();
            LOGGER.info("WorldGenFW template cache cleared.");
        }
    }
}