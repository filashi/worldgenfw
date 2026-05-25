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

    public static Map<String, BlockPos> computeOffsets(
            List<String> templates,
            int minSeparation,
            StructureTemplateManager templateManager,
            RandomSource random) {

        Map<String, BlockPos> offsetMap = new LinkedHashMap<>();
        List<SizedTemplate> sized = new ArrayList<>();

        for (String id : templates) {
            var opt = templateManager.get(ResourceLocation.parse(id));
            if (opt.isPresent()) {
                Vec3i size = opt.get().getSize();
                sized.add(new SizedTemplate(id, size.getX(), size.getZ()));
            }
        }
        if (sized.isEmpty()) return offsetMap;

        Collections.shuffle(sized, new Random(random.nextLong()));

        int maxShelfWidth = 48 + random.nextInt(33); // 48~80
        int currentX = random.nextInt(9); // 起始偏移 0~8
        int currentZ = 0;
        int rowMaxDepth = 0;

        for (SizedTemplate st : sized) {
            // 如果当前行放不下，换行
            if (currentX + st.width + minSeparation > maxShelfWidth) {
                currentZ += rowMaxDepth + minSeparation;
                currentX = random.nextInt(9);
                rowMaxDepth = 0;
            }
            offsetMap.put(st.id, new BlockPos(currentX, 0, currentZ));
            currentX += st.width + minSeparation;
            if (st.depth > rowMaxDepth) rowMaxDepth = st.depth;
        }
        return offsetMap;
    }

    public static double getBuildingHalfExtent(String templateId, StructureTemplateManager manager) {
        var opt = manager.get(ResourceLocation.parse(templateId));
        if (opt.isPresent()) {
            Vec3i size = opt.get().getSize();
            return Math.max(size.getX(), size.getZ()) / 2.0;
        }
        return 0.5;
    }

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
}