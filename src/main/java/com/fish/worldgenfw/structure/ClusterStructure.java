package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.slf4j.Logger;

import java.util.Optional;

public class ClusterStructure extends Structure {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<ClusterStructure> CODEC = Structure.simpleCodec(ClusterStructure::new);

    public ClusterStructure(StructureSettings settings) {
        super(settings);
        LOGGER.info("ClusterStructure instance created."); // 调试日志
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        BlockPos origin = chunkPos.getMiddleBlockPosition(0);

        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();

        BoundingBox bounds = new BoundingBox(
                chunkPos.getMinBlockX(), minY, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), maxY, chunkPos.getMaxBlockZ()
        );

        LOGGER.info("findGenerationPoint called at {}", chunkPos); // 调试日志

        ClusterPiece piece = new ClusterPiece(bounds);
        return Optional.of(new GenerationStub(origin, piecesBuilder -> piecesBuilder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return WorldGenFw.CLUSTER_STRUCTURE_TYPE.get();
    }
}