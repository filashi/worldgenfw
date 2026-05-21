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

/**
 * 负责从任意模组的数据包中加载结构模板（.nbt 文件），并缓存。
 * 借鉴 StructurifyTemplatePoolProvider 的资源遍历思路，但专注于模板加载。
 */
public class StructureTemplateLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    // 使用 ConcurrentHashMap 保证线程安全（世界生成可能多线程）
    private static final ConcurrentHashMap<String, Optional<StructureTemplate>> templateCache = new ConcurrentHashMap<>();
    private static final Logger PROFILER = LogManager.getLogger("WorldGenFWProfiler");


    /**
     * 加载指定 ResourceLocation 的结构模板。
     * @param structureId   结构 ID（例如 "minecraft:desert_pyramid"）
     * @param templateManager 模板管理器，来自 ServerLevel.getStructureManager()
     * @return 模板 Optional，可能为空
     */
    public static Optional<StructureTemplate> loadTemplate(ResourceLocation structureId,
                                                           StructureTemplateManager templateManager) {
        String cacheKey = structureId.toString();

        return templateCache.computeIfAbsent(cacheKey, key -> {
            // 构造 NBT 文件的 ResourceLocation，通常路径为 data/<namespace>/structures/<path>.nbt
            ResourceLocation nbtLocation;
            try {
                nbtLocation = ResourceLocation.fromNamespaceAndPath(
                        structureId.getNamespace(),
                        "structures/" + structureId.getPath() + ".nbt"
                );
            } catch (ResourceLocationException e) {
                LOGGER.error("Invalid structure ID: {}", structureId, e);
                return Optional.empty();
            }

            long loadStartTime = System.currentTimeMillis();
            PROFILER.info("loadTemplate trying to load: {}", nbtLocation);
            Optional<StructureTemplate> template = templateManager.get(nbtLocation);
            PROFILER.info("loadTemplate took: {}ms for {}", System.currentTimeMillis() - loadStartTime, nbtLocation);

            // 通过 StructureTemplateManager 加载（内部会使用 ResourceManager）
//            Optional<StructureTemplate> template = templateManager.get(nbtLocation);

            if (template.isEmpty()) {
                LOGGER.warn("Structure template not found for {} (tried {})", structureId, nbtLocation);
            } else {
                LOGGER.debug("Loaded structure template for {}", structureId);
            }
            return template;
        });
    }

    /**
     * 清除缓存，用于数据包重载。
     */
    public static void clearCache() {
        templateCache.clear();
    }
}