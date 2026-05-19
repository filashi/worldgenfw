// com/fish/worldgenfw/service/DefaultClusterCoordinator.java
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
        // 无片段列表时使用简单整体盒子搜索
        if (pieceBoxes == null || pieceBoxes.isEmpty()) {
            return resolvePositionSimple(structureId, original, estimatedBox, index, instanceId);
        }

        int perceptionRadius = getPerceptionRadius();
        StructureClusterProfile profile = metaRegistry.getProfile(structureId);
        if (!profile.allowsClustering()) {
            LOGGER.debug("结构 {} 不允许集群，跳过重定位", structureId);
            return original;
        }

        List<StructureBoundingBox> conflicts = index.query(estimatedBox).stream()
                .filter(b -> b.instanceId() != instanceId)
                .filter(b -> distance(estimatedBox, b.box()) <= perceptionRadius)
                .toList();

        LOGGER.info("协调器查询：结构 {} (实例{}), 感知半径: {}, 查询到总片段数: {}, 过滤后冲突数: {}",
                structureId, instanceId, perceptionRadius, index.query(estimatedBox).size(), conflicts.size());
        if (conflicts.isEmpty() && !hasPlannedConflict(estimatedBox, instanceId)) {
            return original;
        }

        int searchRadius = ClusterConfig.searchRadius.get();
        int maxAttempts = ClusterConfig.maxAttempts.get();

        double originCenterX = original.getMiddleBlockX();
        double originCenterZ = original.getMiddleBlockZ();
        List<Vec2d> offsets = new ArrayList<>();
        for (AABB pieceBox : pieceBoxes) {
            double dx = (pieceBox.minX + pieceBox.maxX) / 2.0 - originCenterX;
            double dz = (pieceBox.minZ + pieceBox.maxZ) / 2.0 - originCenterZ;
            offsets.add(new Vec2d(dx, dz));
        }

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
            if (isPositionValid(candidate, offsets, pieceBoxes, index, instanceId, perceptionRadius)) {
                LOGGER.info("找到合适空位：区块 {} (尝试次数: {}, 距离: {} 格)",
                        candidate, attempts, candidate.getChessboardDistance(original));
                return candidate;
            }
        }
        LOGGER.info("未找到满足分离条件的空位，保留原始区块: {}", original);
        return original;
    }

    /**
     * 简单整体盒子搜索（当无法提供片段列表时使用）
     */
    private ChunkPos resolvePositionSimple(ResourceLocation structureId, ChunkPos original, AABB estimatedBox,
                                           SpatialIndex index, int instanceId) {
        List<StructureBoundingBox> conflicts = index.query(estimatedBox).stream()
                .filter(b -> b.instanceId() != instanceId)
                .toList();
        if (conflicts.isEmpty() && !hasPlannedConflict(estimatedBox, instanceId)) {
            return original;
        }

        int searchRadius = ClusterConfig.searchRadius.get();
        int maxAttempts = ClusterConfig.maxAttempts.get();

        List<ChunkPos> candidates = new ArrayList<>();
        for (int r = 1; r <= searchRadius; r++) {
            candidates.addAll(generateRing(original, r));
        }
        candidates.sort(Comparator.comparingInt(c -> c.getChessboardDistance(original)));

        int attempts = 0;
        for (ChunkPos candidate : candidates) {
            if (attempts++ >= maxAttempts) break;
            AABB candidateBox = new AABB(
                    candidate.getMiddleBlockX() - estimatedBox.getXsize() / 2,
                    estimatedBox.minY,
                    candidate.getMiddleBlockZ() - estimatedBox.getZsize() / 2,
                    candidate.getMiddleBlockX() + estimatedBox.getXsize() / 2,
                    estimatedBox.maxY,
                    candidate.getMiddleBlockZ() + estimatedBox.getZsize() / 2
            );
            List<StructureBoundingBox> candidateConflicts = index.query(candidateBox).stream()
                    .filter(b -> b.instanceId() != instanceId)
                    .toList();
            if (candidateConflicts.isEmpty() && !hasPlannedConflict(candidateBox, instanceId)) {
                LOGGER.info("找到合适空位（简单模式）：区块 {} (尝试次数: {}, 距离: {} 格)",
                        candidate, attempts, candidate.getChessboardDistance(original));
                return candidate;
            }
        }
        LOGGER.info("未找到无冲突空位（简单模式），保留原区块: {}", original);
        return original;
    }

    private boolean isPositionValid(ChunkPos candidate, List<Vec2d> offsets,
                                    List<AABB> pieceBoxes, SpatialIndex index,
                                    int instanceId, int perceptionRadius) {
        double candCenterX = candidate.getMiddleBlockX();
        double candCenterZ = candidate.getMiddleBlockZ();

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

            List<StructureBoundingBox> allCandidates = index.query(movedPiece).stream()
                    .filter(b -> b.instanceId() != instanceId)
                    .toList();

            if (COLLISION_MODE == CollisionMode.AABB_NO_OVERLAP) {
                for (StructureBoundingBox other : allCandidates) {
                    if (movedPiece.intersects(other.box())) {
                        return false;
                    }
                }
                if (hasPlannedConflict(movedPiece, instanceId)) {
                    return false;
                }
            } else if (COLLISION_MODE == CollisionMode.CENTER_DISTANCE) {
                int minSeparation = ClusterConfig.minSeparation.get();
                boolean tooClose = allCandidates.stream().anyMatch(b -> {
                    double bx = (b.box().minX + b.box().maxX) / 2.0;
                    double bz = (b.box().minZ + b.box().maxZ) / 2.0;
                    double dist = Math.sqrt((newCenterX - bx) * (newCenterX - bx) + (newCenterZ - bz) * (newCenterZ - bz));
                    return dist < minSeparation;
                });
                if (tooClose) return false;
                if (hasPlannedConflict(movedPiece, instanceId)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasPlannedConflict(AABB box, int excludeInstanceId) {
        for (GenerationIntentCache.PlannedStructure planned : GenerationIntentCache.getAllPlanned()) {
            if (planned.instanceId() == excludeInstanceId) continue;
            if (box.intersects(planned.boundingBox())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getPerceptionRadius() { return ClusterConfig.perceptionRadius.get(); }

    @Override
    public int getRetentionRadius() { return ClusterConfig.retentionRadius.get(); }

    private double distance(AABB a, AABB b) {
        double cx1 = (a.minX + a.maxX) / 2.0;
        double cz1 = (a.minZ + a.maxZ) / 2.0;
        double cx2 = (b.minX + b.maxX) / 2.0;
        double cz2 = (b.minZ + b.maxZ) / 2.0;
        return Math.sqrt((cx1 - cx2) * (cx1 - cx2) + (cz1 - cz2) * (cz1 - cz2));
    }

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
        Vec2d(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}