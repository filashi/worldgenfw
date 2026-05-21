package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterBlueprint;
import com.fish.worldgenfw.config.ClusterConfig;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.slf4j.Logger;

import java.util.Optional;

public class ClusterStructure extends Structure {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final MapCodec<ClusterStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    ResourceLocation.CODEC.fieldOf("cluster_id").forGetter(s -> s.clusterId)
            ).apply(instance, ClusterStructure::new)
    );

    private final ResourceLocation clusterId;

    public ClusterStructure(StructureSettings settings, ResourceLocation clusterId) {
        super(settings);
        this.clusterId = clusterId;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ClusterBlueprint blueprint = ClusterConfig.getInstance().getBlueprint(clusterId).orElse(null);
        if (blueprint == null) {
            LOGGER.warn("Cluster blueprint not found: {}", clusterId);
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        BlockPos origin = chunkPos.getMiddleBlockPosition(0);

        BoundingBox bounds = BoundingBox.fromCorners(origin.offset(-8, 0, -8), origin.offset(8, 255, 8));
        ClusterPiece piece = new ClusterPiece(
                clusterId,
                bounds,
                context.structureTemplateManager(), // 传入 StructureTemplateManager
                context.random() // 传入 Random
        );

        return Optional.of(new GenerationStub(origin, piecesBuilder -> {
            piecesBuilder.addPiece(piece);
        }));
    }

    @Override
    public StructureType<?> type() {
        return WorldGenFw.CLUSTER_STRUCTURE_TYPE.get();
    }
}