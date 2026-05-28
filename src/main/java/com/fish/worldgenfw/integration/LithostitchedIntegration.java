package com.fish.worldgenfw.integration;

import com.fish.worldgenfw.WorldGenFw;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class LithostitchedIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 生成 Lithostitched worldgen_modifier JSON 文件，将扫描到的拼图结构的子模板注入到统一池中。
     */
    public static void generateModifier(List<StructureScanner.JigsawStructureInfo> structures, Path configDir) {
        if (structures.isEmpty()) return;

        Map<String, Object> modifier = new LinkedHashMap<>();
        modifier.put("type", "lithostitched:add_template_pool_elements");
        modifier.put("template_pools", List.of("worldgenfw:trigger_pool"));

        List<Map<String, Object>> elements = new ArrayList<>();
        for (var info : structures) {
            // 添加主模板
            elements.add(createElement(info.baseNbt().toString(), 1));
            // 添加所有子模板
            for (var sub : info.subTemplates()) {
                elements.add(createElement(sub.toString(), 2));
            }
        }
        modifier.put("elements", elements);

        try {
            Path modifierDir = configDir.resolve("lithostitched").resolve("worldgen_modifier");
            Files.createDirectories(modifierDir);
            Path file = modifierDir.resolve("injected_structures.json");
            Files.writeString(file, GSON.toJson(modifier));
            LOGGER.info("Generated Lithostitched modifier at {}", file);
        } catch (IOException e) {
            LOGGER.error("Failed to generate Lithostitched modifier", e);
        }
    }

    private static Map<String, Object> createElement(String templateId, int weight) {
        Map<String, Object> element = new LinkedHashMap<>();
        element.put("weight", weight);
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("element_type", "minecraft:single_pool_element");
        inner.put("location", templateId);
        inner.put("projection", "rigid");
        inner.put("processors", "minecraft:empty");
        element.put("element", inner);
        return element;
    }
}