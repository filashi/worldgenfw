package com.fish.worldgenfw;

import com.fish.worldgenfw.structure.ClusterPiece;
import com.fish.worldgenfw.structure.ClusterStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
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
            STRUCTURE_PIECE_TYPES.register("cluster_piece", () -> (structurePieceType, compoundTag) -> new ClusterPiece(compoundTag));

    public WorldGenFw(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECE_TYPES.register(modEventBus);
    }
}