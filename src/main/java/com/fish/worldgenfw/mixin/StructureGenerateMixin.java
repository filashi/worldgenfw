// com/fish/worldgenfw/mixin/StructureGenerateMixin.java
package com.fish.worldgenfw.mixin;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.api.ClusterCoordinator;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Structure.class)
public class StructureGenerateMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldGenFramework/Relocation");

    @Unique
    private static ClusterCoordinator getCoordinator() {
        return WorldGenFw.getCoordinator();
    }

    /**
     * 劫持 GenerationContext 构造，实现重定位并注册生成意图。
     */
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

        // 临时维度固定为 OVERWORLD，后续改进
        ResourceKey<Level> dimension = Level.OVERWORLD;
        SpatialIndex index = GlobalIndexManager.INDEX;

        // 固定尺寸预估包围盒（覆盖典型村庄）
        double halfSize = 100.0;
        double centerX = originalChunkPos.getMiddleBlockX();
        double centerZ = originalChunkPos.getMiddleBlockZ();
        AABB estimatedBox = new AABB(
                centerX - halfSize, -64,
                centerZ - halfSize,
                centerX + halfSize, 320,
                centerZ + halfSize
        );

        // 预分配实例ID并存入ThreadLocal，供StructureStartMixin使用
        int instanceId = GlobalIdGenerator.nextId();
        InstanceIdContext.setNextId(instanceId);

        // 注册生成意图（缓存），使用 unknown 作为结构ID（后续可在StructureStart中更新）
        GenerationIntentCache.put(instanceId,
                new GenerationIntentCache.PlannedStructure(instanceId, "unknown", estimatedBox,
                        GenerationIntentCache.Status.PLANNED));

        ClusterCoordinator coordinator = getCoordinator();

        // 调用协调器获取新位置（简单模式，无片段列表）
        List<AABB> emptyPieceBoxes = new ArrayList<>();
        ChunkPos newChunkPos = coordinator.resolvePosition(
                ResourceLocation.withDefaultNamespace("unknown"),
                originalChunkPos, estimatedBox, index, instanceId, emptyPieceBoxes);

        if (!newChunkPos.equals(originalChunkPos)) {
            LOGGER.info("结构重定位成功: {} -> {} (偏移 {} 格)",
                    originalChunkPos, newChunkPos, originalChunkPos.getChessboardDistance(newChunkPos));
        }

        // 构造新的 GenerationContext，使用新 chunkPos
        return new Structure.GenerationContext(
                registryAccess, chunkGenerator, biomeSource, randomState,
                structureTemplateManager, seed, newChunkPos, heightAccessor, validBiome);
    }

    // 保留但不再做关键逻辑（仅日志输出），确保编译通过
    @Redirect(method = "generate",
            at = @At(value = "NEW",
                    target = "net/minecraft/world/level/levelgen/structure/StructureStart"))
    private static StructureStart redirectNewStart(Structure structure, ChunkPos chunkPos, int references, PiecesContainer pieces) {
        String structureId = resolveStructureId(structure);
        LOGGER.debug("结构 {} 最终区块: {}", structureId, chunkPos);
        return new StructureStart(structure, chunkPos, references, pieces);
    }

    @Unique
    private static String resolveStructureId(Structure structure) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return "unknown";
            Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
            ResourceLocation id = registry.getKey(structure);
            return id != null ? id.toString() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}