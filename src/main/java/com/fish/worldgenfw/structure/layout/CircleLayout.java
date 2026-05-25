package com.fish.worldgenfw.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import com.fish.worldgenfw.structure.LayoutEngine;

import java.util.*;

public class CircleLayout implements LayoutStrategy {

    public static class CircleParams extends LayoutParams {
        public final int radius;
        public final double startAngle;
        public final String centerId;
        public final BlockPos centerOffset;
        public final boolean autoRadius;
        public final int centerExtraMargin;   // 新增：中心建筑额外间距

        public CircleParams(int radius, double startAngle, String centerId, BlockPos centerOffset, int centerExtraMargin) {
            this.radius = radius;
            this.startAngle = startAngle;
            this.centerId = centerId;
            this.centerOffset = centerOffset;
            this.autoRadius = (radius <= 0);
            this.centerExtraMargin = centerExtraMargin;
        }
    }

    @Override
    public Map<String, BlockPos> computeOffsets(
            List<String> templates,
            int minSeparation,
            StructureTemplateManager templateManager,
            RandomSource random,
            LayoutParams params) {

        Map<String, BlockPos> offsetMap = new LinkedHashMap<>();
        if (!(params instanceof CircleParams circle)) return offsetMap;

        // 分离中心建筑
        List<String> ringTemplates = new ArrayList<>(templates);
        double centerHalfSize = 0;
        if (circle.centerId != null && ringTemplates.remove(circle.centerId)) {
            offsetMap.put(circle.centerId, circle.centerOffset != null ? circle.centerOffset : BlockPos.ZERO);
            centerHalfSize = LayoutEngine.getBuildingHalfExtent(circle.centerId, templateManager);
        }

        int n = ringTemplates.size();
        if (n == 0) return offsetMap;

        // 环形建筑最大半尺寸
        double maxRingHalfSize = 0;
        for (String id : ringTemplates) {
            double half = LayoutEngine.getBuildingHalfExtent(id, templateManager);
            if (half > maxRingHalfSize) maxRingHalfSize = half;
        }

        double angleStep = 2 * Math.PI / n;
        int effectiveRadius = circle.radius;
        if (circle.autoRadius) {
            double rFromChord = (maxRingHalfSize + minSeparation / 2.0) / Math.sin(angleStep / 2);
            double rFromCenter = centerHalfSize + maxRingHalfSize + minSeparation + circle.centerExtraMargin;
            effectiveRadius = (int) Math.ceil(Math.max(rFromChord, rFromCenter));
        }

        List<String> shuffledRing = new ArrayList<>(ringTemplates);
        Collections.shuffle(shuffledRing, new Random(random.nextLong()));

        for (int i = 0; i < n; i++) {
            double angle = Math.toRadians(circle.startAngle) + i * angleStep;
            int x = (int) Math.round(effectiveRadius * Math.cos(angle));
            int z = (int) Math.round(effectiveRadius * Math.sin(angle));
            offsetMap.put(shuffledRing.get(i), new BlockPos(x, 0, z));
        }

        return offsetMap;
    }
}