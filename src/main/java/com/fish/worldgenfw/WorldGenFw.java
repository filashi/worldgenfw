package com.fish.worldgenfw;

import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.structure.ClusterPiece;
import com.fish.worldgenfw.structure.ClusterStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(WorldGenFw.MODID)
public class WorldGenFw {
    public static final String MODID = "worldgenfw";
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);

    public static final Supplier<StructureType<ClusterStructure>> CLUSTER_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("cluster_structure", () -> () -> ClusterStructure.CODEC);
    public static final Supplier<StructurePieceType> CLUSTER_PIECE_TYPE =
            STRUCTURE_PIECE_TYPES.register("cluster_piece",
                    () -> (StructurePieceType.ContextlessType) ClusterPiece::new);

    public WorldGenFw(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
        // 注册游戏总线事件，用于加载蓝图
        NeoForge.EVENT_BUS.register(new ServerEventHandler());
    }

    private static class ServerEventHandler {
        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
            ResourceManager resourceManager = event.getServer().getResourceManager();
            ClusterConfig.getInstance().loadBlueprints(resourceManager);
        }
    }
}