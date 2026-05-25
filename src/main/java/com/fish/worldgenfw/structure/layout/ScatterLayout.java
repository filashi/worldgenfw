package com.fish.worldgenfw.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

public class ScatterLayout implements LayoutStrategy {

    public static class ScatterParams extends LayoutParams {
        public final int maxRadius;
        public final int attempts;

        public ScatterParams(int maxRadius, int attempts) {
            this.maxRadius = maxRadius;
            this.attempts = attempts;
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
        if (!(params instanceof ScatterParams scatter)) return offsetMap;

        Random rand = new Random(random.nextLong());
        List<BlockPos> placed = new ArrayList<>();
        List<Double> radii = new ArrayList<>(); // 存储每个已放置建筑的半尺寸 + 间距
        List<String> shuffled = new ArrayList<>(templates);
        Collections.shuffle(shuffled, rand);

        for (String id : shuffled) {
            var opt = templateManager.get(ResourceLocation.parse(id));
            Vec3i size = opt.isPresent() ? opt.get().getSize() : new Vec3i(1, 1, 1);
            double half = Math.max(size.getX(), size.getZ()) / 2.0 + minSeparation;
            boolean found = false;
            for (int attempt = 0; attempt < scatter.attempts; attempt++) {
                double angle = rand.nextDouble() * 2 * Math.PI;
                double dist = rand.nextDouble() * scatter.maxRadius;
                int x = (int) Math.round(dist * Math.cos(angle));
                int z = (int) Math.round(dist * Math.sin(angle));
                BlockPos candidate = new BlockPos(x, 0, z);

                boolean overlap = false;
                for (int i = 0; i < placed.size(); i++) {
                    BlockPos p = placed.get(i);
                    double r = radii.get(i);
                    if (Math.abs(p.getX() - x) < (r + half) && Math.abs(p.getZ() - z) < (r + half)) {
                        overlap = true;
                        break;
                    }
                }
                if (!overlap) {
                    placed.add(candidate);
                    radii.add(half);
                    offsetMap.put(id, candidate);
                    found = true;
                    break;
                }
            }
            if (!found) {
                // 所有尝试失败，随机放置远处
                int x = rand.nextInt(scatter.maxRadius * 2) - scatter.maxRadius;
                int z = rand.nextInt(scatter.maxRadius * 2) - scatter.maxRadius;
                placed.add(new BlockPos(x, 0, z));
                radii.add(half);
                offsetMap.put(id, new BlockPos(x, 0, z));
            }
        }
        return offsetMap;
    }
}