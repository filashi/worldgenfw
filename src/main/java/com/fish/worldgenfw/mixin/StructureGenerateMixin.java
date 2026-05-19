// com/fish/worldgenfw/mixin/StructureGenerateMixin.java
package com.fish.worldgenfw.mixin;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.service.*;
import com.fish.worldgenfw.util.GlobalIdGenerator;
import com.fish.worldgenfw.util.InstanceIdContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(Structure.class)
public class StructureGenerateMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldGenFramework/Relocation");

    @Redirect(method = "generate",
            at = @At(value = "NEW",
                    target = "net/minecraft/world/level/levelgen/structure/Structure$GenerationContext"))
    private static Structure.GenerationContext redirectGenerationContext(
            RegistryAccess registryAccess,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkPos originalChunkPos,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> validBiome) {

        // 获取结构ID（通过 ThreadLocal 从外部传入，或通过结构实例？无法直接获取）
        // 我们使用近似：根据 originalChunkPos 和种子反推结构ID？困难。
        // 暂定：从 WorldGenFw 的当前规划任务中获取。我们需要在 WorldGenFw 中维护一个 Map<ChunkPos, ResourceLocation>。
        // 这里简化：使用 unknown。
        ResourceLocation structureId = ResourceLocation.withDefaultNamespace("unknown");
        ChunkPos finalPos = originalChunkPos;

        // 查询 PlacementMap（全局静态）
        PlacementMap map = WorldGenFw.getPlacementMap();
        if (map != null) {
            ChunkPos planned = map.get(structureId);
            if (planned != null) {
                finalPos = planned;
                LOGGER.info("使用离线规划坐标：{} -> {}", originalChunkPos, finalPos);
            }
        }

        // 预分配 instanceId
        int instanceId = GlobalIdGenerator.nextId();
        InstanceIdContext.setNextId(instanceId);

        // 注册生成意图（但离线规划可能已覆盖，仍保留）
        double halfSize = 100;
        AABB estimatedBox = new AABB(
                originalChunkPos.getMiddleBlockX() - halfSize, -64,
                originalChunkPos.getMiddleBlockZ() - halfSize,
                originalChunkPos.getMiddleBlockX() + halfSize, 320,
                originalChunkPos.getMiddleBlockZ() + halfSize
        );
        GenerationIntentCache.put(instanceId,
                new GenerationIntentCache.PlannedStructure(instanceId, structureId.toString(), estimatedBox,
                        GenerationIntentCache.Status.PLANNED));

        return new Structure.GenerationContext(
                registryAccess, chunkGenerator, biomeSource, randomState,
                structureTemplateManager, seed, finalPos, heightAccessor, validBiome);
    }
}