package com.fish.worldgenfw;

import com.fish.worldgenfw.command.ClusterCommand;
import com.fish.worldgenfw.command.ReloadBlueprintsCommand;
import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.structure.ClusterPiece;
import com.fish.worldgenfw.structure.ClusterStructure;
import com.fish.worldgenfw.structure.StructureTemplateLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
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
                    () -> () -> ClusterStructure.CODEC);

    public WorldGenFw(IEventBus modEventBus) {
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(new ServerEventHandler());
    }

    /**
     * 静态内部类：处理游戏总线的生命周期事件，并直接实现资源重载接口。
     */
    private static class ServerEventHandler {
        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
            ResourceManager resourceManager = event.getServer().getResourceManager();
            ClusterConfig.getInstance().loadBlueprints(resourceManager);

            // 注册测试命令和重载命令
            ClusterCommand.register(event.getServer().getCommands().getDispatcher());
            ReloadBlueprintsCommand.register(event.getServer().getCommands().getDispatcher());

            LOGGER.info("WorldGenFW blueprints loaded and commands registered.");
        }

        @SubscribeEvent
        public void onServerStopped(ServerStoppedEvent event) {
            StructureTemplateLoader.clearCache();
            LOGGER.info("WorldGenFW template cache cleared.");
        }
    }
}