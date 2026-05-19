// com/fish/worldgenfw/mixin/StructureStartMixin.java
package com.fish.worldgenfw.mixin;

import com.fish.worldgenfw.service.*;
import com.fish.worldgenfw.util.GlobalIdGenerator;
import com.fish.worldgenfw.util.InstanceIdContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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

    @Unique
    private int worldgenfw$instanceId = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(CallbackInfo ci) {
        // 优先使用通过 InstanceIdContext 传递的预分配ID
        Integer preId = InstanceIdContext.getNextId();
        if (preId != null) {
            worldgenfw$instanceId = preId;
            InstanceIdContext.clear();
        } else if (worldgenfw$instanceId == -1) {
            worldgenfw$instanceId = GlobalIdGenerator.nextId();
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

        // 将生成意图从缓存中移除（已实际生成）
        GenerationIntentCache.remove(instanceId);

        // 获取维度（暂用于记录，实际不参与八叉树区分）
        ResourceKey<Level> dimension = resolveDimension(worldLevel);
        if (dimension == null) return;

        Structure structure = self.getStructure();
        String structureId = resolveStructureId(structure);

        Set<StructurePiece> insertedPieces = GlobalIndexManager.INSERTED_PIECES;
        SpatialIndex index = GlobalIndexManager.INDEX;

        List<StructurePiece> pieces = self.getPieces();
        for (StructurePiece piece : pieces) {
            if (insertedPieces.contains(piece)) continue;
            insertedPieces.add(piece);

            AABB pieceBox = AABB.of(piece.getBoundingBox());
            // 使用三个参数的构造器
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
    private ResourceKey<Level> resolveDimension(WorldGenLevel worldLevel) {
        if (worldLevel instanceof ServerLevel serverLevel) {
            return serverLevel.dimension();
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level == worldLevel) return level.dimension();
            }
        }
        return null;
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