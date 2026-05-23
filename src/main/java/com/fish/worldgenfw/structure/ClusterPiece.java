package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterPiece extends StructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();

    // 任务去重：键格式 "templateId@x,y,z"
    private static final ConcurrentHashMap<String, Boolean> submittedTasks = new ConcurrentHashMap<>();

    private final String templateId;
    private final int[] offset;
    private final StructureTemplateManager templateManager;
    private final RandomState randomState;
    private final boolean deferredMode;

    // 从 NBT 反序列化
    public ClusterPiece(CompoundTag tag) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), tag);
        this.templateId = tag.getString("TemplateId");
        this.offset = tag.getIntArray("Offset");
        this.templateManager = null;
        this.randomState = null;
        this.deferredMode = tag.getBoolean("DeferredMode");
    }

    // 主要构造函数
    public ClusterPiece(StructureTemplateManager templateManager, RandomState randomState,
                        String templateId, int[] offset, BoundingBox bounds, boolean deferredMode) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), 0, bounds);
        this.templateManager = templateManager;
        this.randomState = randomState;
        this.templateId = templateId;
        this.offset = offset;
        this.deferredMode = deferredMode;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("TemplateId", this.templateId);
        tag.putIntArray("Offset", this.offset);
        tag.putBoolean("DeferredMode", this.deferredMode);
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource random,
                            BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos piecePos) {
        if (templateManager == null || randomState == null) {
            return;
        }

        BlockPos targetXZ = piecePos.offset(offset[0], 0, offset[2]);
        int surfaceY = chunkGenerator.getFirstOccupiedHeight(
                targetXZ.getX(), targetXZ.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                worldGenLevel,
                randomState
        );
        BlockPos targetPos = new BlockPos(targetXZ.getX(), surfaceY - 1, targetXZ.getZ());
        ResourceLocation location = ResourceLocation.parse(templateId);
        Rotation rotation = Rotation.NONE; // 未来可从蓝图读取

        if (deferredMode) {
            // 延迟模式：通过 WorldGenRegion 获取 ServerLevel，在主线程安全放置
            if (worldGenLevel instanceof WorldGenRegion region) {
                ServerLevel level = region.getLevel();

                // 任务去重：避免因多线程调用导致同一位置重复提交
                String taskKey = templateId + "@" + targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ();
                if (submittedTasks.putIfAbsent(taskKey, Boolean.TRUE) != null) {
                    LOGGER.warn("Task already submitted for {} at {}, skipping duplicate.", templateId, targetPos);
                    return;
                }

                level.getServer().execute(() -> {
                    Optional<StructureTemplate> templateOpt = level.getStructureManager().get(location);
                    if (templateOpt.isEmpty()) {
                        LOGGER.error("Template not found for deferred placement: {}", location);
                        return;
                    }
                    StructureTemplate template = templateOpt.get();

                    // 碰撞检测：暂时跳过，因为可能与世界生成过程中的中间结构发生误判
                    // Vec3i rawSize = template.getSize();
                    // BoundingBox newBox = calculateBoundingBox(targetPos, rawSize, rotation);
                    // if (newBox != null && isOverlappingWithExistingStructures(level, newBox)) {
                    //     LOGGER.warn("Structure {} at {} overlaps an existing structure. Skipping.", location, targetPos);
                    //     return;
                    // }

                    StructurePlaceSettings settings = new StructurePlaceSettings()
                            .setRotation(rotation)
                            .setIgnoreEntities(false);
                    template.placeInWorld(level, targetPos, targetPos, settings, level.getRandom(), 2);
                    LOGGER.info("Deferred placed {} at {}", location, targetPos);
                });
            }
        } else {
            // 常规放置（小建筑、小间距），同样加入去重
            String taskKey = templateId + "@" + targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ();
            if (submittedTasks.putIfAbsent(taskKey, Boolean.TRUE) != null) {
                LOGGER.warn("Task already submitted for {} at {}, skipping duplicate.", templateId, targetPos);
                return;
            }

            Optional<StructureTemplate> templateOpt = templateManager.get(location);
            if (templateOpt.isPresent()) {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                templateOpt.get().placeInWorld(worldGenLevel, targetPos, targetPos, settings, random, 2);
                LOGGER.info("Placed {} at {}", templateId, targetPos);
            } else {
                LOGGER.error("Template not found: {}", location);
            }
        }
    }

    // 辅助方法保留，但暂时不被调用
    private BoundingBox calculateBoundingBox(BlockPos pos, Vec3i size, Rotation rotation) {
        int x = size.getX(), y = size.getY(), z = size.getZ();
        int rotatedX, rotatedZ;
        switch (rotation) {
            case CLOCKWISE_90:
            case COUNTERCLOCKWISE_90:
                rotatedX = z;
                rotatedZ = x;
                break;
            default:
                rotatedX = x;
                rotatedZ = z;
        }
        return new BoundingBox(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + rotatedX - 1, pos.getY() + y - 1, pos.getZ() + rotatedZ - 1
        );
    }

    private boolean isOverlappingWithExistingStructures(ServerLevel level, BoundingBox newBox) {
        StructureManager manager = level.structureManager();
        ChunkPos centerChunk = new ChunkPos(new BlockPos(newBox.getCenter()));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                List<StructureStart> starts = manager.startsForStructure(checkChunk, structure -> true);
                for (StructureStart start : starts) {
                    if (start.isValid() && start.getBoundingBox().intersects(newBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}