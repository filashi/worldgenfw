package com.fish.worldgenfw.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import com.fish.worldgenfw.structure.LayoutEngine;

import java.util.*;

public class SnowflakeLayout implements LayoutStrategy {

    public static class SnowflakeParams extends LayoutParams {
        public final String centerId;
        public final int branchLength;
        public final int branches;
        public final int branchSpacing;
        public final boolean autoSpace;
        public final int centerExtraMargin;   // 新增：中心建筑额外间距

        public SnowflakeParams(String centerId, int branchLength, int branches, int branchSpacing, int centerExtraMargin) {
            this.centerId = centerId;
            this.branchLength = branchLength;
            this.branches = branches;
            this.branchSpacing = branchSpacing;
            this.autoSpace = (branchSpacing <= 0);
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
        if (!(params instanceof SnowflakeParams snow)) return offsetMap;

        // 分离中心建筑
        List<String> branchTemplates = new ArrayList<>(templates);
        double centerHalfSize = 0;
        if (snow.centerId != null && branchTemplates.remove(snow.centerId)) {
            offsetMap.put(snow.centerId, BlockPos.ZERO);
            centerHalfSize = LayoutEngine.getBuildingHalfExtent(snow.centerId, templateManager);
        }

        int remaining = branchTemplates.size();
        if (remaining == 0) return offsetMap;

        // 分支建筑最大半尺寸
        double maxBranchHalfSize = 0;
        for (String id : branchTemplates) {
            double half = LayoutEngine.getBuildingHalfExtent(id, templateManager);
            if (half > maxBranchHalfSize) maxBranchHalfSize = half;
        }

        int branches = Math.max(1, snow.branches);
        int lenPerBranch = snow.branchLength > 0 ? snow.branchLength :
                (int) Math.ceil((double) remaining / branches);
        lenPerBranch = Math.max(1, lenPerBranch);

        int autoSpacing = snow.branchSpacing;
        if (snow.autoSpace) {
            autoSpacing = (int) Math.ceil(maxBranchHalfSize * 2) + minSeparation;
        }

        // 第一层距离：中心建筑边界 + 分支建筑半尺寸 + 间距 + 额外余量
        double firstDist = centerHalfSize + maxBranchHalfSize + minSeparation + snow.centerExtraMargin;
        if (firstDist < autoSpacing) firstDist = autoSpacing;

        double angleStep = 2 * Math.PI / branches;
        List<String> shuffled = new ArrayList<>(branchTemplates);
        Collections.shuffle(shuffled, new Random(random.nextLong()));

        int idx = 0;
        for (int b = 0; b < branches && idx < shuffled.size(); b++) {
            double baseAngle = b * angleStep;
            for (int j = 0; j < lenPerBranch && idx < shuffled.size(); j++) {
                double dist = firstDist + j * autoSpacing;
                int x = (int) Math.round(dist * Math.cos(baseAngle));
                int z = (int) Math.round(dist * Math.sin(baseAngle));
                offsetMap.put(shuffled.get(idx++), new BlockPos(x, 0, z));
            }
        }

        // 剩余建筑随机散射
        while (idx < shuffled.size()) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = firstDist + (lenPerBranch + random.nextInt(3)) * autoSpacing;
            int x = (int) Math.round(dist * Math.cos(angle));
            int z = (int) Math.round(dist * Math.sin(angle));
            offsetMap.put(shuffled.get(idx++), new BlockPos(x, 0, z));
        }

        return offsetMap;
    }
}