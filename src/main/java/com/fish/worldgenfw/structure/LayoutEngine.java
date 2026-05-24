package com.fish.worldgenfw.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

public class LayoutEngine {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static class SizedTemplate {
        final String id;
        final int width;
        final int depth;
        SizedTemplate(String id, int width, int depth) {
            this.id = id;
            this.width = width;
            this.depth = depth;
        }
    }

    /**
     * 计算偏移，返回模板ID到偏移的映射。
     * 布局随机化：打乱模板顺序，随机货架宽度，随机起始偏移。
     */
    public static Map<String, BlockPos> computeOffsets(
            List<String> templates,
            int minSeparation,
            StructureTemplateManager templateManager,
            RandomSource random) {   // 新增 random 参数

        Map<String, BlockPos> offsetMap = new LinkedHashMap<>();
        List<SizedTemplate> sized = new ArrayList<>();

        for (String id : templates) {
            var opt = templateManager.get(ResourceLocation.parse(id));
            if (opt.isPresent()) {
                Vec3i size = opt.get().getSize();
                sized.add(new SizedTemplate(id, size.getX(), size.getZ()));
                LOGGER.debug("LayoutEngine loaded template {}", id);
            } else {
                LOGGER.warn("LayoutEngine could not load template {}, skipping.", id);
            }
        }

        if (sized.isEmpty()) return offsetMap;

        // 1. 随机打乱模板顺序，而不是按宽度排序
        Collections.shuffle(sized, new Random(random.nextLong())); // 使用独立 Random 避免影响主随机序列

        // 2. 随机货架最大宽度（例如 48 ~ 80 方块）
        int maxShelfWidth = 48 + random.nextInt(33); // 48 + [0, 32] -> 48~80

        int currentShelfZ = 0;
        int currentShelfX = 0;

        // 3. 随机起始 X 偏移（0 ~ 8）
        currentShelfX = random.nextInt(9); // 0~8

        for (SizedTemplate st : sized) {
            // 如果当前货架放不下，换行
            if (currentShelfX + st.width + minSeparation > maxShelfWidth) {
                currentShelfZ += maxDepth(sized) + minSeparation;
                currentShelfX = random.nextInt(9); // 新行起始也随机偏移
            }

            offsetMap.put(st.id, new BlockPos(currentShelfX, 0, currentShelfZ));
            currentShelfX += st.width + minSeparation;
        }

        return offsetMap;
    }

    private static int maxDepth(List<SizedTemplate> sized) {
        int max = 0;
        for (SizedTemplate st : sized) {
            if (st.depth > max) max = st.depth;
        }
        return max;
    }
}