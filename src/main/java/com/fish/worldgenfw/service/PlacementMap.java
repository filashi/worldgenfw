// com/fish/worldgenfw/service/PlacementMap.java
package com.fish.worldgenfw.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public class PlacementMap {
    // Key: 结构的原始区块坐标（用 ChunkPos 的 toString 作为 key，简单处理）
    // 或者直接用 ResourceLocation + originalChunkPos 组合，这里先仅用 ResourceLocation（假设每个结构只生成一次）
    private final ConcurrentHashMap<ResourceLocation, ChunkPos> placements = new ConcurrentHashMap<>();

    public void put(ResourceLocation structureId, ChunkPos newPos) {
        placements.put(structureId, newPos);
    }

    @Nullable
    public ChunkPos get(ResourceLocation structureId) {
        return placements.get(structureId);
    }

    public boolean contains(ResourceLocation structureId) {
        return placements.containsKey(structureId);
    }

    public void clear() {
        placements.clear();
    }
}