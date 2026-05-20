package com.fish.worldgenfw.service;

import com.fish.worldgenfw.api.ClusterCoordinator;
import com.fish.worldgenfw.api.StructureClusterProfile;
import com.fish.worldgenfw.api.StructureMetaRegistry;
import com.fish.worldgenfw.config.ClusterConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DefaultClusterCoordinator implements ClusterCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger("WorldGenFramework/Coordinator");

    public enum CollisionMode {
        CENTER_DISTANCE,
        AABB_NO_OVERLAP
    }

    private static final CollisionMode COLLISION_MODE = CollisionMode.AABB_NO_OVERLAP;

    private final StructureMetaRegistry metaRegistry;

    public DefaultClusterCoordinator(StructureMetaRegistry metaRegistry) {
        this.metaRegistry = metaRegistry;
    }

    @Override
    public ChunkPos resolvePosition(ResourceLocation structureId, ChunkPos original, AABB estimatedBox,
                                    SpatialIndex index, int instanceId, List<AABB> pieceBoxes) {
        if (pieceBoxes == null || pieceBoxes.isEmpty()) {
            // 退化为简单整体搜索（不应发生）
            return resolvePositionSimple(original, estimatedBox, index, instanceId);
        }

        StructureClusterProfile profile = metaRegistry.getProfile(structureId);
        if (!profile.allowsClustering()) {
            LOGGER.debug("结构 {} 不允许集群，跳过重定位", structureId);
            return original;
        }

        // 先检查原始位置是否冲突（从八叉树和意图缓存）
        if (!hasConflict(original, offsetsFromOrigin(original, pieceBoxes), pieceBoxes, index, instanceId)) {
            LOGGER.info("原始位置 {} 无冲突，保持原位", original);
            return original;
        }

        int searchRadius = ClusterConfig.searchRadius.get();
        int maxAttempts = ClusterConfig.maxAttempts.get();

        // 计算片断偏移
        List<Vec2d> offsets = offsetsFromOrigin(original, pieceBoxes);

        // 螺旋搜索，从半径1开始（跳过原区块）
        List<ChunkPos> candidates = new ArrayList<>();
        for (int r = 1; r <= searchRadius; r++) {
            candidates.addAll(generateRing(original, r));
        }
        candidates.sort(Comparator.comparingInt(c -> c.getChessboardDistance(original)));

        int attempts = 0;
        for (ChunkPos candidate : candidates) {
            if (attempts++ >= maxAttempts) {
                LOGGER.info("达到最大尝试次数 {}，终止搜索", maxAttempts);
                break;
            }
            if (!hasConflict(candidate, offsets, pieceBoxes, index, instanceId)) {
                LOGGER.info("找到合适空位：区块 {} (尝试次数: {}, 距离: {} 格)",
                        candidate, attempts, candidate.getChessboardDistance(original));
                return candidate;
            }
        }

        LOGGER.info("未找到无冲突空位，保留原始区块: {}", original);
        return original;
    }

    private boolean hasConflict(ChunkPos candidate, List<Vec2d> offsets,
                                List<AABB> pieceBoxes, SpatialIndex index, int instanceId) {
        double candCenterX = candidate.getMiddleBlockX();
        double candCenterZ = candidate.getMiddleBlockZ();
        double tolerance = ClusterConfig.collisionTolerance.get();

        for (int i = 0; i < offsets.size(); i++) {
            Vec2d offset = offsets.get(i);
            AABB originalPiece = pieceBoxes.get(i);
            double sizeX = originalPiece.getXsize();
            double sizeZ = originalPiece.getZsize();
            double newCenterX = candCenterX + offset.x;
            double newCenterZ = candCenterZ + offset.z;
            AABB movedPiece = new AABB(
                    newCenterX - sizeX / 2, originalPiece.minY,
                    newCenterZ - sizeZ / 2,
                    newCenterX + sizeX / 2, originalPiece.maxY,
                    newCenterZ + sizeZ / 2
            );

            // 🔧 膨胀容忍缓冲区
            if (tolerance > 0.0) {
                movedPiece = movedPiece.inflate(tolerance);
            }

            // 检查八叉树
            List<StructureBoundingBox> existing = index.query(movedPiece);
            for (StructureBoundingBox other : existing) {
                if (other.instanceId() != instanceId && movedPiece.intersects(other.box())) {
                    return true;
                }
            }

            // 检查意图缓存
            if (GenerationIntentCache.hasPlannedConflict(movedPiece, instanceId)) {
                return true;
            }
        }
        return false;
    }

    private List<Vec2d> offsetsFromOrigin(ChunkPos origin, List<AABB> pieceBoxes) {
        List<Vec2d> offsets = new ArrayList<>();
        double origCenterX = origin.getMiddleBlockX();
        double origCenterZ = origin.getMiddleBlockZ();
        for (AABB pieceBox : pieceBoxes) {
            double dx = (pieceBox.minX + pieceBox.maxX) / 2.0 - origCenterX;
            double dz = (pieceBox.minZ + pieceBox.maxZ) / 2.0 - origCenterZ;
            offsets.add(new Vec2d(dx, dz));
        }
        return offsets;
    }

    // 简单的整体盒子搜索（降级处理，基本不使用）
    private ChunkPos resolvePositionSimple(ChunkPos original, AABB estimatedBox, SpatialIndex index, int instanceId) {
        // 省略，同之前的简单模式
        return original;
    }

    @Override
    public int getPerceptionRadius() { return ClusterConfig.perceptionRadius.get(); }
    @Override
    public int getRetentionRadius() { return ClusterConfig.retentionRadius.get(); }

    private List<ChunkPos> generateRing(ChunkPos center, int radius) {
        List<ChunkPos> positions = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                    positions.add(new ChunkPos(center.x + dx, center.z + dz));
                }
            }
        }
        return positions;
    }

    private static class Vec2d {
        double x, z;
        Vec2d(double x, double z) { this.x = x; this.z = z; }
    }
}