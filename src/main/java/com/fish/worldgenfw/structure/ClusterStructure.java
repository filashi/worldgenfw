package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import java.util.Optional;

public class ClusterStructure extends Structure {

    public static final MapCodec<ClusterStructure> CODEC = Structure.simpleCodec(ClusterStructure::new);

    public ClusterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        BlockPos origin = chunkPos.getMiddleBlockPosition(0);

        // 获取世界的最低和最高构建高度
        int minY = context.heightAccessor().getMinBuildHeight();
        int maxY = context.heightAccessor().getMaxBuildHeight();

        // 使用六参数构造包围盒（minX, minY, minZ, maxX, maxY, maxZ）
        BoundingBox bounds = new BoundingBox(
                chunkPos.getMinBlockX(), minY, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), maxY, chunkPos.getMaxBlockZ()
        );

        ClusterPiece piece = new ClusterPiece(bounds);
        return Optional.of(new GenerationStub(origin, piecesBuilder -> piecesBuilder.addPiece(piece)));
    }

    @Override
    public StructureType<?> type() {
        return WorldGenFw.CLUSTER_STRUCTURE_TYPE.get();
    }
}