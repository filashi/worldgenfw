package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.integration.StructureScanner;
import com.fish.worldgenfw.structure.layout.*;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

import java.util.*;

public class ClusterStructure extends Structure {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_THRESHOLD_TEMPLATE = "ati_structures:ati_stoneworks";
    public static final MapCodec<ClusterStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    ResourceLocation.CODEC.fieldOf("cluster_id").forGetter(s -> s.clusterId)
            ).apply(instance, ClusterStructure::new)
    );

    private final ResourceLocation clusterId;
    private final Set<ChunkPos> generatedChunks = new HashSet<>();

    public ClusterStructure(StructureSettings settings, ResourceLocation clusterId) {
        super(settings);
        this.clusterId = clusterId;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var blueprintOpt = ClusterConfig.getInstance().getBlueprint(clusterId);
        if (blueprintOpt.isEmpty()) return Optional.empty();
        var blueprint = blueprintOpt.get();

        ChunkPos chunkPos = context.chunkPos();
        if (!generatedChunks.add(chunkPos)) return Optional.empty();

        BlockPos origin = chunkPos.getMiddleBlockPosition(0);
        StructureTemplateManager templateManager = context.structureTemplateManager();
        RandomState randomState = context.randomState();
        RandomSource random = context.random();

        // 快速预检查
        boolean hasAnyValid = false;
        for (var m : blueprint.members()) {
            if (templateManager.get(ResourceLocation.parse(m.template())).isPresent()) {
                hasAnyValid = true;
                break;
            }
        }
        if (!hasAnyValid) return Optional.empty();

        return Optional.of(new GenerationStub(origin, piecesBuilder -> {
            // ---- 回调中执行完整生成逻辑 ----

            // 1. 获取中心尺寸阈值
            double thresholdHalfExtent = 0;
            double configuredThreshold = blueprint.centerSizeThreshold();
            if (configuredThreshold <= 0) {
                var thresholdOpt = templateManager.get(ResourceLocation.parse(DEFAULT_THRESHOLD_TEMPLATE));
                if (thresholdOpt.isPresent()) {
                    var size = thresholdOpt.get().getSize();
                    thresholdHalfExtent = Math.max(size.getX(), size.getZ()) / 2.0;
                }
            } else {
                thresholdHalfExtent = configuredThreshold;
            }

            // 2. 过滤有效模板并动态设置 isCenter
            List<ClusterConfig.Member> rawMembers = blueprint.members();
            List<ClusterConfig.Member> validPool = new ArrayList<>();
            for (var m : rawMembers) {
                var res = ResourceLocation.parse(m.template());
                var templateOpt = templateManager.get(res);
                if (templateOpt.isEmpty()) continue;
                boolean effectiveIsCenter = m.isCenter();
                if (!effectiveIsCenter && thresholdHalfExtent > 0) {
                    double half = LayoutEngine.getBuildingHalfExtent(m.template(), templateManager);
                    if (half >= thresholdHalfExtent) effectiveIsCenter = true;
                }
                validPool.add(new ClusterConfig.Member(
                        m.template(), m.offset(), m.rotation(), m.yOffset(), m.weight(), effectiveIsCenter
                ));
            }

            if (validPool.isEmpty()) return;

            // 3. 目标抽取数量
            int targetCount;
            boolean autoLayout = blueprint.autoLayout();
            String layoutType = blueprint.layout();
            Map<String, Object> layoutParams = blueprint.layoutParams();
            int count = validPool.size();

            if (autoLayout) {
                if (count < 3) return;
                boolean hasCenter = validPool.stream().anyMatch(ClusterConfig.Member::isCenter);
                if (hasCenter) {
                    int min = Math.max(blueprint.minPoolSize(), 9);
                    int max = Math.max(min, blueprint.maxPoolSize());
                    max = Math.min(max, count);
                    if (min > max) min = max;
                    targetCount = min + random.nextInt(max - min + 1);
                } else {
                    int min = blueprint.minPoolSize();
                    int max = Math.min(blueprint.maxPoolSize(), count);
                    if (max < min) max = min;
                    targetCount = min + random.nextInt(max - min + 1);
                }
            } else {
                targetCount = blueprint.poolSize();
                if (targetCount <= 0 && blueprint.maxPoolSize() > 0) {
                    int min = Math.max(1, blueprint.minPoolSize());
                    int max = Math.min(blueprint.maxPoolSize(), count);
                    if (min > max) min = max;
                    targetCount = min + random.nextInt(max - min + 1);
                }
                if (targetCount <= 0) targetCount = count;
            }

            // 4. 抽取成员
            List<ClusterConfig.Member> selectedMembers;
            if (autoLayout && validPool.stream().anyMatch(ClusterConfig.Member::isCenter) && targetCount >= 9) {
                selectedMembers = weightedRandomSelectWithCenter(validPool, targetCount, random);
                if (selectedMembers.stream().noneMatch(ClusterConfig.Member::isCenter)) {
                    selectedMembers = weightedRandomSelect(validPool, targetCount, random);
                }
            } else {
                selectedMembers = weightedRandomSelect(validPool, targetCount, random);
            }
            if (selectedMembers.size() < targetCount) {
                List<ClusterConfig.Member> remaining = new ArrayList<>(validPool);
                remaining.removeAll(selectedMembers);
                while (selectedMembers.size() < targetCount && !remaining.isEmpty()) {
                    selectedMembers.add(remaining.remove(random.nextInt(remaining.size())));
                }
            }

            // 5. 自动布局参数决策
            if (autoLayout) {
                if (selectedMembers.stream().anyMatch(ClusterConfig.Member::isCenter)) {
                    layoutType = random.nextBoolean() ? "circle" : "snowflake";
                    String centerId = null;
                    List<String> centers = new ArrayList<>();
                    for (var m : selectedMembers) if (m.isCenter()) centers.add(m.template());
                    if (!centers.isEmpty()) centerId = centers.get(random.nextInt(centers.size()));
                    int cem = 5;
                    if (blueprint.layoutParams() != null && blueprint.layoutParams().containsKey("centerExtraMargin"))
                        cem = ((Number) blueprint.layoutParams().get("centerExtraMargin")).intValue();
                    layoutParams = new HashMap<>();
                    if ("circle".equals(layoutType)) {
                        layoutParams.put("radius", 0);
                        layoutParams.put("startAngle", 0);
                    } else {
                        layoutParams.put("branches", 4 + random.nextInt(3));
                        layoutParams.put("branchLength", 0);
                        layoutParams.put("branchSpacing", 0);
                    }
                    layoutParams.put("centerId", centerId);
                    layoutParams.put("centerExtraMargin", cem);
                } else {
                    layoutType = random.nextBoolean() ? "random_pack" : "grid";
                    layoutParams = new HashMap<>();
                    if ("grid".equals(layoutType)) {
                        int cols = (int) Math.ceil(Math.sqrt(selectedMembers.size()));
                        layoutParams.put("rows", 0);
                        layoutParams.put("cols", cols);
                        layoutParams.put("spacingX", 0);
                        layoutParams.put("spacingZ", 0);
                    }
                }
            } else {
                if (layoutParams == null) layoutParams = new HashMap<>();
                if (layoutParams.containsKey("centerExtraMargin"))
                    layoutParams.put("centerExtraMargin", ((Number) layoutParams.get("centerExtraMargin")).intValue());
            }

            List<String> validTemplateIds = new ArrayList<>();
            for (var m : selectedMembers) validTemplateIds.add(m.template());
            if (validTemplateIds.isEmpty()) return;

            int sep = blueprint.minSeparation() > 0 ? blueprint.minSeparation() : 8;

            Map<String, BlockPos> offsetMap;
            LayoutStrategy strategy = null;
            LayoutParams params = null;
            int cem = layoutParams != null ? ((Number) layoutParams.getOrDefault("centerExtraMargin", 5)).intValue() : 5;

            if (layoutParams != null) {
                switch (layoutType) {
                    case "linear" -> {
                        String axis = (String) layoutParams.getOrDefault("axis", "x");
                        int spacing = ((Number) layoutParams.getOrDefault("spacing", 0)).intValue();
                        boolean shuffle = (boolean) layoutParams.getOrDefault("shuffle", true);
                        params = new LinearLayout.LinearParams(axis, spacing, shuffle);
                        strategy = new LinearLayout();
                    }
                    case "grid" -> {
                        int rows = ((Number) layoutParams.getOrDefault("rows", 0)).intValue();
                        int cols = ((Number) layoutParams.getOrDefault("cols", 0)).intValue();
                        int spacingX = ((Number) layoutParams.getOrDefault("spacingX", 0)).intValue();
                        int spacingZ = ((Number) layoutParams.getOrDefault("spacingZ", 0)).intValue();
                        params = new GridLayout.GridParams(rows, cols, spacingX, spacingZ);
                        strategy = new GridLayout();
                    }
                    case "circle" -> {
                        int radius = ((Number) layoutParams.getOrDefault("radius", 0)).intValue();
                        double startAngle = ((Number) layoutParams.getOrDefault("startAngle", 0)).doubleValue();
                        String centerId = (String) layoutParams.getOrDefault("centerId", null);
                        params = new CircleLayout.CircleParams(radius, startAngle, centerId, BlockPos.ZERO, cem);
                        strategy = new CircleLayout();
                    }
                    case "snowflake" -> {
                        int branchLength = ((Number) layoutParams.getOrDefault("branchLength", 0)).intValue();
                        int branches = ((Number) layoutParams.getOrDefault("branches", 4)).intValue();
                        int branchSpacing = ((Number) layoutParams.getOrDefault("branchSpacing", 0)).intValue();
                        String centerId = (String) layoutParams.getOrDefault("centerId", null);
                        params = new SnowflakeLayout.SnowflakeParams(centerId, branchLength, branches, branchSpacing, cem);
                        strategy = new SnowflakeLayout();
                    }
                    case "scatter" -> {
                        int maxRadius = ((Number) layoutParams.getOrDefault("maxRadius", 30)).intValue();
                        int attempts = ((Number) layoutParams.getOrDefault("attempts", 10)).intValue();
                        params = new ScatterLayout.ScatterParams(maxRadius, attempts);
                        strategy = new ScatterLayout();
                    }
                }
            }

            if (strategy != null && params != null) {
                offsetMap = strategy.computeOffsets(validTemplateIds, sep, templateManager, random, params);
            } else {
                offsetMap = LayoutEngine.computeOffsets(validTemplateIds, sep, templateManager, random);
            }

            // 7. 创建最终成员列表（带计算后的偏移）
            List<ClusterConfig.Member> finalMembers = new ArrayList<>();
            for (var m : selectedMembers) {
                BlockPos off = offsetMap.get(m.template());
                if (off != null) {
                    finalMembers.add(new ClusterConfig.Member(
                            m.template(), new int[]{off.getX(), off.getY(), off.getZ()},
                            m.rotation(), m.yOffset(), m.weight(), m.isCenter()
                    ));
                }
            }
            if (finalMembers.isEmpty()) return;

            // 8. 获取世界构建高度
            int minY = context.heightAccessor().getMinBuildHeight();
            int maxY = context.heightAccessor().getMaxBuildHeight();

            // 9. 创建 Piece（统一为一个成员创建一个 Piece，不再拆分）
            // 在创建 Piece 的回调中，替换原本的遍历 finalMembers 创建 Piece 的逻辑

            for (var member : finalMembers) {
                int[] memberOffset = member.offset();
                String templateId = member.template();
                ResourceLocation templateLoc = ResourceLocation.parse(templateId);

                // 判断是否为拼图结构
                if (StructureScanner.isJigsawStructure(templateLoc)) {
                    // 使用统一的触发器模板 ID（拼图方块将在 postProcess 中直接创建）
                    String triggerId = "worldgenfw:trigger_" + templateLoc.getPath().replace('/', '_');
                    // 包围盒覆盖 1x1 即可
                    BoundingBox bounds = new BoundingBox(
                            origin.getX() + memberOffset[0], minY,
                            origin.getZ() + memberOffset[2],
                            origin.getX() + memberOffset[0], maxY,
                            origin.getZ() + memberOffset[2]
                    );
                    ClusterPiece triggerPiece = new ClusterPiece(triggerId, memberOffset, bounds, member.yOffset());
                    piecesBuilder.addPiece(triggerPiece);
                } else {
                    // 普通模板
                    var templateOpt = templateManager.get(templateLoc);
                    if (templateOpt.isEmpty()) continue;
                    Vec3i size = templateOpt.get().getSize();
                    BoundingBox bounds = new BoundingBox(
                            origin.getX() + memberOffset[0] - size.getX()/2, minY,
                            origin.getZ() + memberOffset[2] - size.getZ()/2,
                            origin.getX() + memberOffset[0] + size.getX()/2, maxY,
                            origin.getZ() + memberOffset[2] + size.getZ()/2
                    );
                    ClusterPiece piece = new ClusterPiece(templateId, memberOffset, bounds, member.yOffset());
                    piecesBuilder.addPiece(piece);
                }
            }

            LOGGER.info("Cluster {} generated with {} pieces.", clusterId, finalMembers.size());
        }));
    }

    private static List<ClusterConfig.Member> weightedRandomSelect(List<ClusterConfig.Member> pool, int count, RandomSource random) {
        List<ClusterConfig.Member> remaining = new ArrayList<>(pool);
        List<ClusterConfig.Member> selected = new ArrayList<>();
        for (int i = 0; i < count && !remaining.isEmpty(); i++) {
            int totalWeight = 0;
            for (var m : remaining) totalWeight += m.weight();
            if (totalWeight <= 0) {
                selected.add(remaining.remove(random.nextInt(remaining.size())));
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

    private static List<ClusterConfig.Member> weightedRandomSelectWithCenter(List<ClusterConfig.Member> pool, int count, RandomSource random) {
        List<ClusterConfig.Member> centers = new ArrayList<>();
        List<ClusterConfig.Member> nonCenters = new ArrayList<>();
        for (var m : pool) {
            if (m.isCenter()) centers.add(m);
            else nonCenters.add(m);
        }
        if (centers.isEmpty()) return weightedRandomSelect(pool, count, random);
        int totalWeightC = 0;
        for (var c : centers) totalWeightC += c.weight();
        int r = random.nextInt(totalWeightC);
        int cumul = 0;
        ClusterConfig.Member selectedCenter = centers.get(0);
        for (var c : centers) {
            cumul += c.weight();
            if (r < cumul) {
                selectedCenter = c;
                break;
            }
        }
        List<ClusterConfig.Member> remaining = new ArrayList<>(pool);
        remaining.remove(selectedCenter);
        List<ClusterConfig.Member> others = weightedRandomSelect(remaining, count - 1, random);
        others.add(selectedCenter);
        Collections.shuffle(others);
        return others;
    }

    @Override
    public StructureType<?> type() {
        return WorldGenFw.CLUSTER_STRUCTURE_TYPE.get();
    }
}