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
import net.minecraft.world.level.block.Blocks;
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

    private static final ConcurrentHashMap<String, Boolean> submittedTasks = new ConcurrentHashMap<>();

    private final String templateId;
    private final int[] offset;
    private final StructureTemplateManager templateManager;
    private final RandomState randomState;
    private final boolean deferredMode;
    private final int yOffset;

    public ClusterPiece(CompoundTag tag) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), tag);
        this.templateId = tag.getString("TemplateId");
        this.offset = tag.getIntArray("Offset");
        this.templateManager = null;
        this.randomState = null;
        this.deferredMode = tag.getBoolean("DeferredMode");
        this.yOffset = tag.contains("YOffset") ? tag.getInt("YOffset") : -1;
    }

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

        BlockPos targetXZ = piecePos.offset(offset[0], 0, offset[2]);
        int surfaceY = chunkGenerator.getFirstOccupiedHeight(
                targetXZ.getX(), targetXZ.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                worldGenLevel,
                randomState
        );
        BlockPos targetPos = new BlockPos(targetXZ.getX(), surfaceY + yOffset, targetXZ.getZ());

        ResourceLocation location = ResourceLocation.parse(templateId);
        Rotation rotation = Rotation.NONE;

        if (deferredMode) {
            if (worldGenLevel instanceof WorldGenRegion region) {
                ServerLevel level = region.getLevel();

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

                    // 拼图检测（已省略打印，可保留）
                    StructurePlaceSettings settings = new StructurePlaceSettings()
                            .setRotation(rotation)
                            .setIgnoreEntities(false);
                    template.placeInWorld(level, targetPos, targetPos, settings, level.getRandom(), 2);

                    // 移除拼图方块
                    removeJigsawBlocks(level, template, targetPos, rotation);

                    LOGGER.info("Deferred placed {} at {}", templateId, targetPos);
                });
            }
        } else {
            String taskKey = templateId + "@" + targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ();
            if (submittedTasks.putIfAbsent(taskKey, Boolean.TRUE) != null) {
                LOGGER.warn("Task already submitted for {} at {}, skipping duplicate.", templateId, targetPos);
                return;
            }

            Optional<StructureTemplate> templateOpt = templateManager.get(location);
            if (templateOpt.isPresent()) {
                StructureTemplate template = templateOpt.get();
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
                template.placeInWorld(worldGenLevel, targetPos, targetPos, settings, random, 2);
                // 常规模式下 worldGenLevel 是 WorldGenRegion，可尝试移除拼图
                if (worldGenLevel instanceof ServerLevel serverLevel) {
                    removeJigsawBlocks(serverLevel, template, targetPos, rotation);
                }
                LOGGER.info("Placed {} at {}", templateId, targetPos);
            } else {
                LOGGER.error("Template not found: {}", location);
            }
        }
    }

    /**
     * 移除模板放置后残留的拼图方块。
     */
    private static void removeJigsawBlocks(ServerLevel level, StructureTemplate template, BlockPos pos, Rotation rotation) {
        Vec3i size = template.getSize(rotation);
        BlockPos min = pos;
        BlockPos max = pos.offset(size).offset(-1, -1, -1);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(p).is(Blocks.JIGSAW)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}