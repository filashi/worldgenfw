package com.fish.worldgenfw.mixin;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.api.ClusterCoordinator;
import com.fish.worldgenfw.service.*;
import com.fish.worldgenfw.util.GlobalIdGenerator;
import com.fish.worldgenfw.util.InstanceIdContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
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

@Mixin(Structure.class)
public class StructureGenerateMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldGenFramework/Relocation");

    @Unique
    private static ClusterCoordinator getCoordinator() {
        return WorldGenFw.getCoordinator();
    }

    // 劫持 new StructureStart 构造，实现重定位
    @Redirect(method = "generate",
            at = @At(value = "NEW",
                    target = "net/minecraft/world/level/levelgen/structure/StructureStart"))
    private static StructureStart redirectNewStart(Structure structure, ChunkPos chunkPos, int references, PiecesContainer pieces) {
        String structureId = resolveStructureId(structure);
        ResourceLocation id = ResourceLocation.parse(structureId);

        // 分配实例ID
        int instanceId = GlobalIdGenerator.nextId();
        InstanceIdContext.setNextId(instanceId);  // 传递给 StructureStart 构造

        // 提取片断包围盒
        List<AABB> pieceBoxes = new ArrayList<>();
        AABB overallBox = null;
        if (!pieces.isEmpty()) {
            var tempBox = pieces.calculateBoundingBox();
            overallBox = AABB.of(tempBox);
            for (StructurePiece piece : pieces.pieces()) {
                pieceBoxes.add(AABB.of(piece.getBoundingBox()));
            }
        }

        // 注册生成意图（暂不启用暂缓，但注册用于后续兼容）
        if (overallBox != null) {
            GenerationIntentCache.put(instanceId,
                    new GenerationIntentCache.PlannedStructure(instanceId, structureId, overallBox,
                            GenerationIntentCache.Status.PLANNED));
        }

        // 调用协调器进行重定向
        SpatialIndex index = GlobalIndexManager.INDEX;
        ClusterCoordinator coordinator = getCoordinator();
        ChunkPos newPos = chunkPos;
        if (overallBox != null) {
            newPos = coordinator.resolvePosition(id, chunkPos, overallBox, index, instanceId, pieceBoxes);
        }

        if (!newPos.equals(chunkPos)) {
            LOGGER.info("结构 {} 重定位成功: {} -> {} (偏移 {} 格)",
                    structureId, chunkPos, newPos, chunkPos.getChessboardDistance(newPos));
        } else {
            LOGGER.debug("结构 {} 保持位置: {}", structureId, chunkPos);
        }

        return new StructureStart(structure, newPos, references, pieces);
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