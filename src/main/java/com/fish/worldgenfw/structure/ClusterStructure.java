package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterConfig;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
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
        RandomSource random = context.random(); // 使用 context 提供的随机源，保证确定性

        List<ClusterConfig.Member> members = new ArrayList<>(blueprint.members());

        // ========== P0 随机抽取 ==========
        int actualPoolSize = blueprint.poolSize();
        if (actualPoolSize <= 0 && blueprint.maxPoolSize() > 0) {
            // 使用随机范围
            int min = Math.max(1, blueprint.minPoolSize());
            int max = Math.min(blueprint.maxPoolSize(), members.size());
            if (min > max) min = max;
            actualPoolSize = min + random.nextInt(max - min + 1);
        }
        if (actualPoolSize > 0 && actualPoolSize < members.size()) {
            members = weightedRandomSelect(members, actualPoolSize, random);
            LOGGER.info("Randomly selected {} members from pool of {} for cluster {}",
                    actualPoolSize, blueprint.members().size(), clusterId);
        }

        // ========== 自动化布局模式 ==========
        if ("random_pack".equals(blueprint.layout())) {
            int sep = blueprint.minSeparation() > 0 ? blueprint.minSeparation() : 8;

            // 筛选可加载的模板
            List<String> validTemplateIds = new ArrayList<>();
            for (var m : members) {
                var res = ResourceLocation.parse(m.template());
                if (templateManager.get(res).isPresent()) {
                    validTemplateIds.add(m.template());
                } else {
                    LOGGER.warn("Template {} not found, skipping.", m.template());
                }
            }

            if (validTemplateIds.isEmpty()) {
                LOGGER.error("No valid templates in cluster {}, aborting.", clusterId);
                return Optional.empty();
            }

            // 计算偏移（只对有效模板）
            Map<String, BlockPos> offsetMap = LayoutEngine.computeOffsets(validTemplateIds, sep, templateManager, context.random());

            // 重新构造成员列表，保留原始成员的 yOffset、weight 等字段
            List<ClusterConfig.Member> autoMembers = new ArrayList<>();
            for (var m : members) {
                BlockPos off = offsetMap.get(m.template());
                if (off != null) {
                    autoMembers.add(new ClusterConfig.Member(
                            m.template(),
                            new int[]{ off.getX(), off.getY(), off.getZ() },
                            m.rotation(),
                            m.yOffset()
                    ));
                }
            }
            members = autoMembers;
        }

        if (members.isEmpty()) {
            return Optional.empty();
        }

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
                        true,
                        member.yOffset()
                );
                piecesBuilder.addPiece(piece);
            }
        }));
    }

    /**
     * 加权随机抽取 poolSize 个成员（不重复）。
     */
    private static List<ClusterConfig.Member> weightedRandomSelect(
            List<ClusterConfig.Member> pool, int count, RandomSource random) {
        // 复制列表，避免修改原列表
        List<ClusterConfig.Member> remaining = new ArrayList<>(pool);
        List<ClusterConfig.Member> selected = new ArrayList<>();

        for (int i = 0; i < count && !remaining.isEmpty(); i++) {
            // 计算总权重
            int totalWeight = 0;
            for (var m : remaining) {
                totalWeight += m.weight();
            }
            if (totalWeight <= 0) {
                // 权重全为0，随机等概率选一个
                int idx = random.nextInt(remaining.size());
                selected.add(remaining.remove(idx));
                continue;
            }
            int r = random.nextInt(totalWeight);
            int cumulative = 0;
            for (int j = 0; j < remaining.size(); j++) {
                cumulative += remaining.get(j).weight();
                if (r < cumulative) {
                    selected.add(remaining.remove(j));
                    break;
                }
            }
        }
        return selected;
    }

    @Override
    public StructureType<?> type() {
        return WorldGenFw.CLUSTER_STRUCTURE_TYPE.get();
    }
}