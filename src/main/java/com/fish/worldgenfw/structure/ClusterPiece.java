package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private final int yOffset;   // Y 方向额外偏移

    // 从 NBT 反序列化
    public ClusterPiece(CompoundTag tag) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), tag);
        this.templateId = tag.getString("TemplateId");
        this.offset = tag.getIntArray("Offset");
        this.templateManager = null;
        this.randomState = null;
        this.deferredMode = tag.getBoolean("DeferredMode");
        this.yOffset = tag.contains("YOffset") ? tag.getInt("YOffset") : -1;
    }

    // 主要构造函数
    public ClusterPiece(StructureTemplateManager templateManager, RandomState randomState,
                        String templateId, int[] offset, BoundingBox bounds, boolean deferredMode,
                        int yOffset) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), 0, bounds);
        this.templateManager = templateManager;
        this.randomState = randomState;
        this.templateId = templateId;
        this.offset = offset;
        this.deferredMode = deferredMode;
        this.yOffset = yOffset;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("TemplateId", this.templateId);
        tag.putIntArray("Offset", this.offset);
        tag.putBoolean("DeferredMode", this.deferredMode);
        tag.putInt("YOffset", this.yOffset);
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource random,
                            BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos piecePos) {
        if (templateManager == null || randomState == null) {
            return;
        }

        // 计算放置坐标（基于 piecePos 和蓝图中定义的偏移）
        BlockPos targetXZ = piecePos.offset(offset[0], 0, offset[2]);
        int surfaceY = chunkGenerator.getFirstOccupiedHeight(
                targetXZ.getX(), targetXZ.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                worldGenLevel,
                randomState
        );
        // 最终 Y 坐标 = 地表高度 + 用户指定的 yOffset（默认 -1 使建筑下沉一格）
        BlockPos targetPos = new BlockPos(targetXZ.getX(), surfaceY + yOffset, targetXZ.getZ());

        ResourceLocation location = ResourceLocation.parse(templateId);
        Rotation rotation = Rotation.NONE; // 未来可从蓝图读取旋转值

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

                    // ========== 拼图方块检测 (基于 NBT) ==========
                    List<JigsawDetector.JigsawInfo> jigsaws = JigsawDetector.detectFromNBT(location, level.getServer());
                    boolean hasDoor = JigsawDetector.hasDoors(location, level.getServer());
                    JigsawDetector.JigsawInfo selected = JigsawDetector.selectConnectionPoint(jigsaws, location, level.getServer());
                    if (selected != null) {
                        Direction worldDir = JigsawDetector.getWorldDirection(selected.facing(), rotation);
                        LOGGER.info("[JigsawDetect] Template: {} | Jigsaw at local {} | Has door: {} | Selected world direction: {}",
                                templateId, selected.pos(), hasDoor, worldDir.getName());
                    } else {
                        LOGGER.info("[JigsawDetect] Template: {} | No jigsaw blocks found.", templateId);
                    }
                    // ============================================

                    StructurePlaceSettings settings = new StructurePlaceSettings()
                            .setRotation(rotation)
                            .setIgnoreEntities(false);
                    template.placeInWorld(level, targetPos, targetPos, settings, level.getRandom(), 2);
                    LOGGER.info("Deferred placed {} at {}", templateId, targetPos);
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
                StructureTemplate template = templateOpt.get();

                // 拼图方块检测（常规模式也做，但需要获取 Server 实例，这里可能无法直接获取，可跳过）
                // 由于常规模式下可能没有 Server 实例，暂时省略检测，或以后实现。为简洁，此处跳过。
                // 若需要，可传入 Server 实例，但当前 deferredMode 为 true 所以常规模式不会被使用。

                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                template.placeInWorld(worldGenLevel, targetPos, targetPos, settings, random, 2);
                LOGGER.info("Placed {} at {}", templateId, targetPos);
            } else {
                LOGGER.error("Template not found: {}", location);
            }
        }
    }
}