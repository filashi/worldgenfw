// com/fish/worldgenfw/service/StructurePredictor.java
package com.fish.worldgenfw.service;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.*;

public class StructurePredictor {

    /**
     * 预测指定规划区域内所有即将生成的结构起始区块。
     * @param level 当前维度
     * @param center 中心区块
     * @param radiusChunks 搜索半径（区块数）
     * @return Map<结构ID, List<起始区块>>
     */
    public static Map<ResourceLocation, List<ChunkPos>> predictStructures(
            ServerLevel level, ChunkPos center, int radiusChunks) {

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Registry<StructureSet> structureSets = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE_SET);
        HolderLookup<StructureSet> holderLookup = structureSets.asLookup();
        ChunkGeneratorStructureState state = generator.createState(
                holderLookup,
                level.getChunkSource().randomState(),
                level.getSeed()
        );
        state.ensureStructuresGenerated(); // 必须调用，否则 placements 未初始化

        Registry<Structure> structureRegistry = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        Map<ResourceLocation, List<ChunkPos>> result = new HashMap<>();

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;

                for (Holder<StructureSet> holder : state.possibleStructureSets()) {
                    StructureSet set = holder.value();
                    StructurePlacement placement = set.placement();

                    if (placement.isStructureChunk(state, chunkX, chunkZ)) {
                        for (var entry : set.structures()) {
                            Structure structure = entry.structure().value();
                            ResourceLocation id = structureRegistry.getKey(structure);
                            if (id != null) {
                                result.computeIfAbsent(id, k -> new ArrayList<>())
                                        .add(new ChunkPos(chunkX, chunkZ));
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}