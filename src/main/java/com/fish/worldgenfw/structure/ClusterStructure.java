package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterConfig;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
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
        // 从蓝图配置中获取集群成员（蓝图已在服务器启动时加载）
        var blueprintOpt = ClusterConfig.getInstance().getBlueprint(clusterId);
        if (blueprintOpt.isEmpty()) {
            LOGGER.warn("Cluster blueprint not found for ID: {}", clusterId);
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        BlockPos origin = chunkPos.getMiddleBlockPosition(0);
        StructureTemplateManager templateManager = context.structureTemplateManager();
        RandomState randomState = context.randomState();

        return Optional.of(new GenerationStub(origin, piecesBuilder -> {
            for (var member : blueprintOpt.get().members()) {
                // 为每个成员创建一个独立的 ClusterPiece
                ClusterPiece piece = new ClusterPiece(
                        templateManager,
                        randomState,
                        member.template(),
                        member.offset(),
                        new BoundingBox(
                                origin.getX() + member.offset()[0] - 8, origin.getY() + member.offset()[1] - 8,
                                origin.getZ() + member.offset()[2] - 8,
                                origin.getX() + member.offset()[0] + 8, origin.getY() + member.offset()[1] + 255,
                                origin.getZ() + member.offset()[2] + 8
                        ),
                        true   // deferredMode
                );
                piecesBuilder.addPiece(piece);
            }
        }));
    }

    @Override
    public StructureType<?> type() {
        return WorldGenFw.CLUSTER_STRUCTURE_TYPE.get();
    }
}