package com.fish.worldgenfw.structure.layout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

public class GridLayout implements LayoutStrategy {

    public static class GridParams extends LayoutParams {
        public final int rows;
        public final int cols;
        public final int spacingX;      // 0 表示自动
        public final int spacingZ;      // 0 表示自动
        public final boolean autoSpaceX;
        public final boolean autoSpaceZ;

        public GridParams(int rows, int cols, int spacingX, int spacingZ) {
            this.rows = rows;
            this.cols = cols;
            this.spacingX = spacingX;
            this.spacingZ = spacingZ;
            this.autoSpaceX = (spacingX <= 0);
            this.autoSpaceZ = (spacingZ <= 0);
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
        if (!(params instanceof GridParams grid)) return offsetMap;

        int count = templates.size();
        if (count == 0) return offsetMap;

        int cols = grid.cols > 0 ? grid.cols : Math.max(1, (int) Math.ceil(Math.sqrt(count)));
        int rows = grid.rows > 0 ? grid.rows : (int) Math.ceil((double) count / cols);

        List<String> shuffled = new ArrayList<>(templates);
        Collections.shuffle(shuffled, new Random(random.nextLong()));

        // 记录每列的最大宽度和每行的最大深度
        int[] colWidths = new int[cols];
        int[] rowDepths = new int[rows];
        // 先分配模板到网格位置（不计算偏移）
        String[][] gridMap = new String[rows][cols];
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (idx < shuffled.size()) {
                    gridMap[r][c] = shuffled.get(idx++);
                }
            }
        }

        // 收集尺寸并确定列宽/行高
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                String id = gridMap[r][c];
                if (id == null) continue;
                var opt = templateManager.get(ResourceLocation.parse(id));
                if (opt.isPresent()) {
                    Vec3i size = opt.get().getSize();
                    if (size.getX() > colWidths[c]) colWidths[c] = size.getX();
                    if (size.getZ() > rowDepths[r]) rowDepths[r] = size.getZ();
                }
            }
        }

        // 计算累加 X/Z 偏移
        int[] xOffsets = new int[cols];
        int[] zOffsets = new int[rows];
        int xAcc = 0;
        for (int c = 0; c < cols; c++) {
            xOffsets[c] = xAcc;
            int gapX = grid.autoSpaceX ? colWidths[c] + minSeparation : grid.spacingX + minSeparation;
            xAcc += gapX;
        }
        int zAcc = 0;
        for (int r = 0; r < rows; r++) {
            zOffsets[r] = zAcc;
            int gapZ = grid.autoSpaceZ ? rowDepths[r] + minSeparation : grid.spacingZ + minSeparation;
            zAcc += gapZ;
        }

        // 填充偏移
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                String id = gridMap[r][c];
                if (id != null) {
                    offsetMap.put(id, new BlockPos(xOffsets[c], 0, zOffsets[r]));
                }
            }
        }

        return offsetMap;
    }
}