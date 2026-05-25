package com.fish.worldgenfw.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

public class LinearLayout implements LayoutStrategy {

    public static class LinearParams extends LayoutParams {
        public final String axis;
        public final int spacing;       // 0 表示自动
        public final boolean shuffle;
        public final boolean autoSpace;

        public LinearParams(String axis, int spacing, boolean shuffle) {
            this.axis = axis;
            this.spacing = spacing;
            this.shuffle = shuffle;
            this.autoSpace = (spacing <= 0);
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
        if (!(params instanceof LinearParams linear)) return offsetMap;

        List<String> ordered = new ArrayList<>(templates);
        if (linear.shuffle) {
            Collections.shuffle(ordered, new Random(random.nextLong()));
        }

        int pos = 0;
        for (String id : ordered) {
            int offsetX = 0, offsetZ = 0;
            if ("x".equalsIgnoreCase(linear.axis)) {
                offsetX = pos;
            } else {
                offsetZ = pos;
            }
            offsetMap.put(id, new BlockPos(offsetX, 0, offsetZ));

            // 动态步长：当前建筑宽度 + 间距
            var opt = templateManager.get(ResourceLocation.parse(id));
            int size = 1;
            if (opt.isPresent()) {
                Vec3i s = opt.get().getSize();
                size = "x".equalsIgnoreCase(linear.axis) ? s.getX() : s.getZ();
            }
            int step = linear.autoSpace ? size + minSeparation : linear.spacing + minSeparation;
            pos += step;
        }
        return offsetMap;
    }
}