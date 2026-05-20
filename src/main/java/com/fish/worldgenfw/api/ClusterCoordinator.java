package com.fish.worldgenfw.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import com.fish.worldgenfw.service.SpatialIndex;

import java.util.List;

public interface ClusterCoordinator {
    /**
     * 根据现有空间索引和预估包围盒，计算最终生成位置。
     * @param structureId 结构的注册名
     * @param originalPos 原始区块位置
     * @param estimatedBoundingBox 整个结构的预估包围盒（用于快速初步查询）
     * @param spatialIndex 空间索引
     * @param instanceId 当前结构实例的唯一 ID
     * @param pieceBoxes 新结构所有片段的包围盒列表
     * @return 最终区块位置
     */
    ChunkPos resolvePosition(ResourceLocation structureId,
                             ChunkPos originalPos,
                             AABB estimatedBoundingBox,
                             SpatialIndex spatialIndex,
                             int instanceId,
                             List<AABB> pieceBoxes);

    int getPerceptionRadius();
    int getRetentionRadius();
}