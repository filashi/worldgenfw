package com.fish.worldgenfw.mixin;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.service.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void onApplyBiomeDecoration(WorldGenLevel level, ChunkAccess chunkAccess,
                                        StructureManager structureManager, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        ChunkPos chunkPos = chunkAccess.getPos();
        int perceptionRadius = ClusterConfig.perceptionRadius.get();
        int radiusChunks = (perceptionRadius / 16) + 1;

        // 预测规划区域内的结构
        Map<ResourceLocation, List<ChunkPos>> predictions =
                StructurePredictor.predictStructures(serverLevel, chunkPos, radiusChunks);

        if (predictions.isEmpty()) return;

        // 构建离线规划信息
        Map<ResourceLocation, OfflinePlanner.PlannedStructureInfo> planned = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<ChunkPos>> entry : predictions.entrySet()) {
            ResourceLocation id = entry.getKey();
            for (ChunkPos pos : entry.getValue()) {
                double halfSize = 100.0;
                AABB estimated = new AABB(
                        pos.getMiddleBlockX() - halfSize, -64,
                        pos.getMiddleBlockZ() - halfSize,
                        pos.getMiddleBlockX() + halfSize, 320,
                        pos.getMiddleBlockZ() + halfSize
                );
                planned.put(id, new OfflinePlanner.PlannedStructureInfo(pos, estimated));
            }
        }

        // 获取已存在结构片段
        List<StructureBoundingBox> existingBoxes = GlobalIndexManager.INDEX.getAll();

        // 执行离线规划
        OfflinePlanner planner = new OfflinePlanner(WorldGenFw.getMetaRegistry());
        Map<ResourceLocation, ChunkPos> result = planner.planWithExistingBoxes(planned, existingBoxes);

        // 写入全局 PlacementMap
        PlacementMap placementMap = WorldGenFw.getPlacementMap();
        for (Map.Entry<ResourceLocation, ChunkPos> entry : result.entrySet()) {
            placementMap.put(entry.getKey(), entry.getValue());
        }
    }
}