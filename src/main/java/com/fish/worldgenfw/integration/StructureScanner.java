package com.fish.worldgenfw.integration;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

public class StructureScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record JigsawStructureInfo(ResourceLocation baseNbt, List<ResourceLocation> subTemplates) {}

    private static final Map<ResourceLocation, List<ResourceLocation>> cachedSubTemplates = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static List<JigsawStructureInfo> scan(ResourceManager resourceManager) {
        List<JigsawStructureInfo> result = new ArrayList<>();
        Map<ResourceLocation, Resource> allNbt = resourceManager.listResources(
                "structure", loc -> loc.getPath().endsWith(".nbt")
        );

        List<ResourceLocation> baseCandidates = new ArrayList<>();
        Map<String, List<ResourceLocation>> subDirMap = new LinkedHashMap<>();

        for (ResourceLocation loc : allNbt.keySet()) {
            String path = loc.getPath();
            if (!path.startsWith("structure/")) continue;
            String relative = path.substring("structure/".length());
            int slashIdx = relative.indexOf('/');
            if (slashIdx == -1) {
                baseCandidates.add(loc);
            } else {
                String subDir = relative.substring(0, slashIdx);
                subDirMap.computeIfAbsent(subDir, k -> new ArrayList<>()).add(loc);
            }
        }

        for (ResourceLocation baseLoc : baseCandidates) {
            String basePath = baseLoc.getPath();
            String baseName = basePath.substring("structure/".length(), basePath.length() - ".nbt".length());
            List<ResourceLocation> matchedSubs = subDirMap.get(baseName);
            if (matchedSubs == null) {
                for (Map.Entry<String, List<ResourceLocation>> entry : subDirMap.entrySet()) {
                    if (baseName.contains(entry.getKey()) || entry.getKey().contains(baseName)) {
                        matchedSubs = entry.getValue();
                        break;
                    }
                }
            }
            if (matchedSubs != null && !matchedSubs.isEmpty()) {
                String baseTemplateId = baseLoc.getNamespace() + ":" + baseName;
                ResourceLocation baseTemplateLoc = ResourceLocation.parse(baseTemplateId);
                List<ResourceLocation> subList = new ArrayList<>();
                for (ResourceLocation subLoc : matchedSubs) {
                    String subPath = subLoc.getPath();
                    String subRelative = subPath.substring("structure/".length());
                    subList.add(ResourceLocation.parse(subLoc.getNamespace() + ":" + subRelative.substring(0, subRelative.length() - ".nbt".length())));
                }
                result.add(new JigsawStructureInfo(baseTemplateLoc, subList));
                cachedSubTemplates.put(baseTemplateLoc, subList);
                LOGGER.debug("Found jigsaw: {} ({} sub-templates)", baseTemplateId, subList.size());
            }
        }
        LOGGER.info("StructureScanner found {} jigsaw structures.", result.size());
        return result;
    }

    public static boolean isJigsawStructure(ResourceLocation templateId) {
        return cachedSubTemplates.containsKey(templateId);
    }

    public static List<ResourceLocation> getSubTemplates(ResourceLocation templateId) {
        return cachedSubTemplates.getOrDefault(templateId, Collections.emptyList());
    }

    /**
     * 为给定的模板列表生成一个 Template Pool JSON 字符串。
     * 用于 Lithostitched 注入。
     */
    public static String generatePoolJson(String poolName, List<String> templates) {
        List<Map<String, Object>> elements = new ArrayList<>();
        for (int i = 0; i < templates.size(); i++) {
            Map<String, Object> element = new LinkedHashMap<>();
            element.put("weight", 1);
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("element_type", "minecraft:single_pool_element");
            inner.put("location", templates.get(i));
            inner.put("projection", "rigid");
            inner.put("processors", "minecraft:empty");
            element.put("element", inner);
            elements.add(element);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", poolName);
        root.put("fallback", "minecraft:empty");
        root.put("elements", elements);
        return GSON.toJson(root);
    }
}