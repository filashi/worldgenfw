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

import java.util.*;

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
        var blueprintOpt = ClusterConfig.getInstance().getBlueprint(clusterId);
        if (blueprintOpt.isEmpty()) {
            LOGGER.warn("Cluster blueprint not found: {}", clusterId);
            return Optional.empty();
        }
        var blueprint = blueprintOpt.get();

        ChunkPos chunkPos = context.chunkPos();
        BlockPos origin = chunkPos.getMiddleBlockPosition(0);
        StructureTemplateManager templateManager = context.structureTemplateManager();
        RandomState randomState = context.randomState();

        List<ClusterConfig.Member> members = blueprint.members();

        // 判断布局模式
        boolean isAutoLayout = "random_pack".equals(blueprint.layout());
        if (isAutoLayout) {
            // 自动布局：收集模板ID，调用引擎计算偏移
            List<String> templateIds = new ArrayList<>();
            for (var m : members) {
                templateIds.add(m.template());
            }
            int sep = blueprint.minSeparation() > 0 ? blueprint.minSeparation() : 8;
            List<BlockPos> offsets = LayoutEngine.computeOffsets(templateIds, sep, templateManager);

            // 重新构造成员列表（带自动计算的偏移）
            List<ClusterConfig.Member> autoMembers = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                var m = members.get(i);
                BlockPos off = offsets.get(i);
                autoMembers.add(new ClusterConfig.Member(
                        m.template(),
                        new int[]{ off.getX(), off.getY(), off.getZ() },
                        m.rotation()
                ));
            }
            members = autoMembers;
        }

        // 统一处理（无论是手动偏移还是自动计算）
        final var finalMembers = members;
        return Optional.of(new GenerationStub(origin, piecesBuilder -> {
            for (var member : finalMembers) {
                int[] offset = member.offset();
                ClusterPiece piece = new ClusterPiece(
                        templateManager,
                        randomState,
                        member.template(),
                        offset,
                        new BoundingBox(
                                origin.getX() + offset[0] - 8, origin.getY() + offset[1] - 8,
                                origin.getZ() + offset[2] - 8,
                                origin.getX() + offset[0] + 8, origin.getY() + offset[1] + 255,
                                origin.getZ() + offset[2] + 8
                        ),
                        true
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