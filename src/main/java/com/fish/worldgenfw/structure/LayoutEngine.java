package com.fish.worldgenfw.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

public class LayoutEngine {

    // 内部辅助记录：模板ID及其水平尺寸
    private static class SizedTemplate {
        final String id;
        final int width;   // X方向
        final int depth;   // Z方向

        SizedTemplate(String id, int width, int depth) {
            this.id = id;
            this.width = width;
            this.depth = depth;
        }
    }

    /**
     * 根据模板列表计算无重叠的偏移坐标。
     * @param templates 模板 ID 列表
     * @param minSeparation 最小间距（方块）
     * @param templateManager 用于获取模板尺寸
     * @return 偏移量列表，顺序与模板顺序对应
     */
    public static List<BlockPos> computeOffsets(
            List<String> templates,
            int minSeparation,
            StructureTemplateManager templateManager) {

        // 1. 加载模板并获取尺寸（仅考虑水平方向，忽略 Y 轴）
        List<SizedTemplate> sized = new ArrayList<>();
        for (String id : templates) {
            var res = ResourceLocation.parse(id);
            var opt = templateManager.get(res);
            if (opt.isPresent()) {
                Vec3i size = opt.get().getSize();
                sized.add(new SizedTemplate(id, size.getX(), size.getZ()));
            }
        }
        if (sized.isEmpty()) return List.of();

        // 2. 按宽度降序排序（Shelf Packing 常用策略）
        sized.sort((a, b) -> Integer.compare(b.width, a.width));

        // 3. 初始化货架
        List<BlockPos> offsets = new ArrayList<>();
        int currentShelfZ = 0;          // Z 方向偏移（货架基线）
        int currentShelfX = 0;          // 当前货架上已使用的 X 末端

        for (SizedTemplate st : sized) {
            int w = st.width;
            int d = st.depth;

            // 如果当前货架放不下（宽度超出限制？这里不设硬限制，只是换行）
            // 为模拟“自然集群”，我们设一个最大货架宽度（比如 64），超过就另起一行
            if (currentShelfX + w + minSeparation > 64) {
                // 换下一行：移动到已用区域下方
                currentShelfZ += maxDepthOnShelf(sized, offsets) + minSeparation;
                currentShelfX = 0;
            }

            // 放置
            BlockPos offset = new BlockPos(currentShelfX, 0, currentShelfZ);
            offsets.add(offset);

            // 更新货架指针
            currentShelfX += w + minSeparation;
        }

        return offsets;
    }

    // 辅助：计算当前货架上所有结构的最大深度（用于下移）
    private static int maxDepthOnShelf(List<SizedTemplate> sized, List<BlockPos> placed) {
        // 简单策略：取所有模板的最大深度
        int max = 0;
        for (SizedTemplate st : sized) {
            if (st.depth > max) max = st.depth;
        }
        return max;
    }
}