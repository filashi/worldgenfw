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
        public final int centerExtraMargin;

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

        // 收集每个环形建筑的半尺寸
        double[] halfSizes = new double[n];
        double maxRingHalfSize = 0;
        for (int i = 0; i < n; i++) {
            halfSizes[i] = LayoutEngine.getBuildingHalfExtent(ringTemplates.get(i), templateManager);
            if (halfSizes[i] > maxRingHalfSize) maxRingHalfSize = halfSizes[i];
        }

        double angleStep = 2 * Math.PI / n;
        int effectiveRadius = circle.radius;
        if (circle.autoRadius) {
            double rFromChord = (maxRingHalfSize + minSeparation / 2.0) / Math.sin(angleStep / 2);
            double rFromCenter = centerHalfSize + maxRingHalfSize + minSeparation + circle.centerExtraMargin;
            effectiveRadius = (int) Math.ceil(Math.max(rFromChord, rFromCenter));
        }

        // 打乱顺序并分配初始角度
        List<String> shuffledRing = new ArrayList<>(ringTemplates);
        Collections.shuffle(shuffledRing, new Random(random.nextLong()));

        double[] angles = new double[n];
        int[] radii = new int[n];
        for (int i = 0; i < n; i++) {
            angles[i] = Math.toRadians(circle.startAngle) + i * angleStep;
            radii[i] = effectiveRadius;
        }

        // 两两碰撞检测与径向微调（最多5轮）
        int maxIterations = 5;
        boolean collisionFixed;
        do {
            collisionFixed = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double minDist = halfSizes[i] + halfSizes[j] + minSeparation;
                    double x1 = radii[i] * Math.cos(angles[i]);
                    double z1 = radii[i] * Math.sin(angles[i]);
                    double x2 = radii[j] * Math.cos(angles[j]);
                    double z2 = radii[j] * Math.sin(angles[j]);
                    double dist = Math.sqrt((x1 - x2) * (x1 - x2) + (z1 - z2) * (z1 - z2));
                    if (dist < minDist) {
                        // 将索引较大的建筑向外推
                        radii[j] += (int) Math.ceil(minDist - dist) + 1;
                        collisionFixed = true;
                    }
                }
            }
            maxIterations--;
        } while (collisionFixed && maxIterations > 0);

        // 应用最终偏移
        for (int i = 0; i < n; i++) {
            int x = (int) Math.round(radii[i] * Math.cos(angles[i]));
            int z = (int) Math.round(radii[i] * Math.sin(angles[i]));
            offsetMap.put(shuffledRing.get(i), new BlockPos(x, 0, z));
        }

        return offsetMap;
    }
}