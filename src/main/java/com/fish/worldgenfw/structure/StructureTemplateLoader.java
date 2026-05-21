package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StructureTemplateLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ConcurrentHashMap<String, Optional<StructureTemplate>> templateCache = new ConcurrentHashMap<>();

    public static Optional<StructureTemplate> loadTemplate(ResourceLocation structureId,
                                                           StructureTemplateManager templateManager) {
        String cacheKey = structureId.toString();

        return templateCache.computeIfAbsent(cacheKey, key -> {
            // 关键修正：直接使用 structureId 作为模板路径，因为 StructureTemplateManager 会自动添加 structure/ 前缀
            Optional<StructureTemplate> template = templateManager.get(structureId);

            if (template.isEmpty()) {
                LOGGER.warn("Structure template not found for {} (tried {})", structureId, structureId);
            } else {
                LOGGER.debug("Loaded structure template for {}", structureId);
            }
            return template;
        });
    }

    public static void clearCache() {
        templateCache.clear();
    }
}