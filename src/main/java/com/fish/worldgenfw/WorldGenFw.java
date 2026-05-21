package com.fish.worldgenfw;

import com.fish.worldgenfw.command.ClusterCommand;
import com.fish.worldgenfw.command.ReloadBlueprintsCommand;
import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.structure.ClusterPiece;
import com.fish.worldgenfw.structure.ClusterStructure;
import com.fish.worldgenfw.structure.StructureTemplateLoader;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.function.Supplier;

@Mod(WorldGenFw.MODID)
public class WorldGenFw {
    public static final String MODID = "worldgenfw";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);

    public static final Supplier<StructurePieceType> CLUSTER_PIECE_TYPE =
            STRUCTURE_PIECE_TYPES.register("cluster_piece",
                    () -> (StructurePieceType.ContextlessType) ClusterPiece::new);

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);

    public static final Supplier<StructureType<ClusterStructure>> CLUSTER_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("cluster_structure",
                    () -> new StructureType<ClusterStructure>() {
                        @Override
                        public MapCodec<ClusterStructure> codec() {
                            return ClusterStructure.CODEC;
                        }
                    });

    public WorldGenFw(IEventBus modEventBus) {
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(new ServerEventHandler());
    }

    /**
     * 静态内部类：处理游戏总线的生命周期事件，并直接实现资源重载接口。
     */
    private static class ServerEventHandler implements ResourceManagerReloadListener {
        @SubscribeEvent
        public void onServerAboutToStart(ServerAboutToStartEvent event) {
            MinecraftServer server = event.getServer();
            ClusterConfig.getInstance().setServer(server);
            // 可选：预加载常用蓝图以便命令可用
            ResourceManager resourceManager = server.getResourceManager();
            ClusterConfig.getInstance().loadBlueprintDirect(resourceManager, ResourceLocation.fromNamespaceAndPath(WorldGenFw.MODID, "test_cluster"));
            ClusterConfig.getInstance().loadBlueprintDirect(resourceManager, ResourceLocation.fromNamespaceAndPath(WorldGenFw.MODID, "mod_test"));

            ClusterCommand.register(server.getCommands().getDispatcher());
            ReloadBlueprintsCommand.register(server.getCommands().getDispatcher());
            LOGGER.info("WorldGenFW blueprints loaded and commands registered.");
        }

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            // 只清理结构模板缓存，蓝图保留（因为可以通过 server 重新加载）
            StructureTemplateLoader.clearCache();
            LOGGER.info("WorldGenFW template caches cleared after reload.");
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            StructureTemplateLoader.clearCache();
            ClusterConfig.getInstance().clearCache();
            ClusterConfig.getInstance().setServer(null);
            LOGGER.info("WorldGenFW caches cleared on shutdown.");
        }
    }
}