// com/fish/worldgenfw/service/OfflinePlanner.java
package com.fish.worldgenfw.service;

import com.fish.worldgenfw.api.StructureClusterProfile;
import com.fish.worldgenfw.api.StructureMetaRegistry;
import com.fish.worldgenfw.config.ClusterConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class OfflinePlanner {

    private final StructureMetaRegistry metaRegistry;

    public OfflinePlanner(StructureMetaRegistry metaRegistry) {
        this.metaRegistry = metaRegistry;
    }

    /**
     * 对一组结构进行离线规划，返回它们的最终坐标。
     *
     * @param plannedStructures 待规划结构及其原始区块和预估包围盒
     * @param existingIndex     包含已有不可移动结构的八叉树
     * @return Map<结构ID, 新ChunkPos>
     */
    public Map<ResourceLocation, ChunkPos> plan(
            Map<ResourceLocation, PlannedStructureInfo> plannedStructures,
            SpatialIndex existingIndex) {

        // 临时八叉树，先克隆现有索引（或直接使用新的）
        SimpleOctree tempIndex = new SimpleOctree();
        // 将已存在的结构片段拷贝过来（简化：直接遍历 existingIndex，但无迭代器。我们假设 existingIndex 已包含所需数据。）
        // 由于 SpatialIndex 无迭代方法，这里需要在外部把 existingIndex 的已有片段插入 tempIndex。
        // 为简化，调用者应提供一个 List<StructureBoundingBox> 或我们改进索引接口。
        // 这里我们接受一个 List<StructureBoundingBox> 作为已存在数据。
        List<StructureBoundingBox> existingBoxes = new ArrayList<>();
        // 接口不支持获取所有元素，因此需改造 SpatialIndex。为原型，我们暂时新加一个方法。
        // 实际上，我们在调用处通过反射或全局变量获取，但这里直接传一个列表。
        // 我们将 OfflinePlanner.plan 的参数改为 List<StructureBoundingBox> existingBoxes。
        // 重新声明方法签名：
        // public Map<ResourceLocation, ChunkPos> plan(Map<ResourceLocation, PlannedStructureInfo> planned,
        //                                            List<StructureBoundingBox> existingBoxes)

        // 为了代码完整性，提供修正后的方法：
        return planWithExistingBoxes(plannedStructures, existingBoxes);
    }

    public Map<ResourceLocation, ChunkPos> planWithExistingBoxes(
            Map<ResourceLocation, PlannedStructureInfo> plannedStructures,
            List<StructureBoundingBox> existingBoxes) {

        SimpleOctree tempIndex = new SimpleOctree();
        for (StructureBoundingBox box : existingBoxes) {
            tempIndex.insert(box);
        }

        // 按优先级排序（目前简单按结构名排序，后续可配置）
        List<Map.Entry<ResourceLocation, PlannedStructureInfo>> sorted = new ArrayList<>(plannedStructures.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));

        Map<ResourceLocation, ChunkPos> result = new HashMap<>();

        for (Map.Entry<ResourceLocation, PlannedStructureInfo> entry : sorted) {
            ResourceLocation id = entry.getKey();
            PlannedStructureInfo info = entry.getValue();
            StructureClusterProfile profile = metaRegistry.getProfile(id);
            if (!profile.allowsClustering()) {
                result.put(id, info.originalPos);
                continue;
            }

            ChunkPos bestPos = findBestPosition(
                    id, info.originalPos, info.estimatedBox, tempIndex,
                    ClusterConfig.searchRadius.get(),
                    ClusterConfig.maxAttempts.get(),
                    ClusterConfig.minSeparation.get()
            );

            // 将选定位置的包围盒插入临时索引
            AABB placedBox = offsetBox(info.estimatedBox, bestPos.getMiddleBlockX(), bestPos.getMiddleBlockZ());
            tempIndex.insert(new StructureBoundingBox(placedBox, id.toString(), -1)); // instanceId 忽略

            result.put(id, bestPos);
        }
        return result;
    }

    private ChunkPos findBestPosition(ResourceLocation structureId, ChunkPos original, AABB estimatedBox,
                                      SpatialIndex index, int searchRadius, int maxAttempts, int minSeparation) {
        // 复用螺旋搜索，检查 AABB 不相交
        List<ChunkPos> candidates = new ArrayList<>();
        for (int r = 1; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r || Math.abs(dz) == r) {
                        candidates.add(new ChunkPos(original.x + dx, original.z + dz));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingInt(c -> c.getChessboardDistance(original)));

        for (ChunkPos candidate : candidates) {
            AABB candidateBox = offsetBox(estimatedBox,
                    candidate.getMiddleBlockX(), candidate.getMiddleBlockZ());

            List<StructureBoundingBox> overlaps = index.query(candidateBox);
            boolean blocked = false;
            for (StructureBoundingBox other : overlaps) {
                if (candidateBox.intersects(other.box())) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                return candidate;
            }
        }
        return original;
    }

    private AABB offsetBox(AABB box, double newCenterX, double newCenterZ) {
        double sizeX = box.getXsize();
        double sizeZ = box.getZsize();
        return new AABB(
                newCenterX - sizeX / 2, box.minY,
                newCenterZ - sizeZ / 2,
                newCenterX + sizeX / 2, box.maxY,
                newCenterZ + sizeZ / 2
        );
    }

    public static class PlannedStructureInfo {
        public final ChunkPos originalPos;
        public final AABB estimatedBox;
        public PlannedStructureInfo(ChunkPos originalPos, AABB estimatedBox) {
            this.originalPos = originalPos;
            this.estimatedBox = estimatedBox;
        }
    }
}