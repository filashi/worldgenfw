package com.fish.worldgenfw.mixin;

import com.fish.worldgenfw.service.*;
import com.fish.worldgenfw.util.GlobalIdGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

@Mixin(StructureStart.class)
public class StructureStartMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldGenFramework/StructureCollision");

    // instanceId 将完全由 StructureGenerateMixin 分配并通过 ThreadLocal 传递，这里不再自动分配。
    // 保留此字段，但由外部设置。
    @Unique
    private int worldgenfw$instanceId = -1;

    // 这个注入不再负责分配ID，仅用于日志？我们移除构造器注入，改为在 placeInChunk 中记录。
    // 但 instanceId 需要在 placeInChunk 前设置，所以在 StructureGenerateMixin 中创建 StructureStart 后会通过 reflection 或 Accessor 设置。
    // 为了简单，我们保留 onConstruct 但改为从 ThreadLocal 读取（与之前类似），并保留字段。
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(CallbackInfo ci) {
        // ID 由 StructureGenerateMixin 通过 InstanceIdContext 传递，这里直接读取并设置。
        Integer preId = com.fish.worldgenfw.util.InstanceIdContext.getNextId();
        if (preId != null) {
            worldgenfw$instanceId = preId;
            com.fish.worldgenfw.util.InstanceIdContext.clear();
        } else {
            // 如果没有预分配，自己分配（作为后备）
            if (worldgenfw$instanceId == -1) {
                worldgenfw$instanceId = GlobalIdGenerator.nextId();
            }
        }
    }

    @Inject(method = "placeInChunk", at = @At("TAIL"))
    private void afterPlaceInChunk(
            WorldGenLevel worldLevel,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource randomSource,
            BoundingBox boundingBox,
            ChunkPos chunkPos,
            CallbackInfo ci) {

        StructureStart self = (StructureStart) (Object) this;
        int instanceId = this.worldgenfw$instanceId;

        // 更新意图缓存状态（如果存在）
        GenerationIntentCache.remove(instanceId);

        Structure structure = self.getStructure();
        String structureId = resolveStructureId(structure);

        Set<StructurePiece> insertedPieces = GlobalIndexManager.INSERTED_PIECES;
        SpatialIndex index = GlobalIndexManager.INDEX;

        List<StructurePiece> pieces = self.getPieces();
        for (StructurePiece piece : pieces) {
            if (insertedPieces.contains(piece)) continue;
            insertedPieces.add(piece);

            AABB pieceBox = AABB.of(piece.getBoundingBox());
            StructureBoundingBox sbb = new StructureBoundingBox(pieceBox, structureId, instanceId);

            List<StructureBoundingBox> allOverlaps = index.query(pieceBox);
            List<StructureBoundingBox> externalConflicts = allOverlaps.stream()
                    .filter(other -> other.instanceId() != instanceId)
                    .toList();

            if (!externalConflicts.isEmpty()) {
                LOGGER.info("结构冲突检测：结构 {} (实例{}) 的片段 {} 与 {} 个其他结构的片段重叠",
                        structureId, instanceId, formatAABB(pieceBox), externalConflicts.size());
                for (StructureBoundingBox other : externalConflicts) {
                    LOGGER.info("  -> 冲突片段: 来自结构 {} (实例{}) 边界 {}",
                            other.structureId(), other.instanceId(), formatAABB(other.box()));
                }
            } else if (!allOverlaps.isEmpty()) {
                LOGGER.debug("结构内部连接：结构 {} (实例{}) 的片段 {} 与 {} 个自身片段重叠",
                        structureId, instanceId, formatAABB(pieceBox), allOverlaps.size());
            }

            index.insert(sbb);
        }
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

    @Unique
    private static String formatAABB(AABB box) {
        return String.format("(%.1f, %.1f, %.1f) - (%.1f, %.1f, %.1f)",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
}