package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterBlueprint;
import com.fish.worldgenfw.config.ClusterConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

import java.util.Optional;

public class ClusterPiece extends StructurePiece {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ResourceLocation clusterId;
    private final StructureTemplateManager templateManager;
    private final RandomSource random;
    // 移除不需要的 ServerLevelAccessor

    public ClusterPiece(ResourceLocation clusterId, BoundingBox boundingBox, StructureTemplateManager templateManager, RandomSource random) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), 0, boundingBox);
        this.clusterId = clusterId;
        this.templateManager = templateManager;
        this.random = random;
    }

    public ClusterPiece(CompoundTag tag) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), tag);
        this.clusterId = ResourceLocation.parse(tag.getString("ClusterId"));
        // 通过 NBT 加载时，`templateManager` 和 `random` 将不可用，这意味着如果仅依赖 NBT 将无法重新生成结构。
        this.templateManager = null;
        this.random = null;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("ClusterId", this.clusterId.toString());
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource randomSource,
                            BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos piecePos) {
        // 直接转换为 ServerLevel，WorldGenLevel 在运行时通常是 ServerLevel 的实例
        ServerLevel level = (ServerLevel) worldGenLevel;
        StructureTemplateManager currentTemplateManager = this.templateManager != null ? this.templateManager : level.getStructureManager();
        RandomSource currentRandom = this.random != null ? this.random : randomSource;

        ClusterBlueprint blueprint = ClusterConfig.getInstance().getBlueprint(clusterId).orElse(null);
        if (blueprint == null) {
            LOGGER.error("Cluster blueprint not found: {}", clusterId);
            return;
        }

        for (var entry : blueprint.getStructures()) {
            ResourceLocation structureId = entry.getResourceLocation();
            Rotation rotation = switch (entry.getRotation().toLowerCase()) {
                case "clockwise_90" -> Rotation.CLOCKWISE_90;
                case "clockwise_180" -> Rotation.CLOCKWISE_180;
                case "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };

            Optional<StructureTemplate> templateOpt = StructureTemplateLoader.loadTemplate(structureId, currentTemplateManager);
            if (templateOpt.isEmpty()) {
                LOGGER.error("Template not found: {}", structureId);
                if (entry.isRequired()) {
                    LOGGER.warn("Skipping cluster placement due to missing required structure.");
                    return;
                }
                continue;
            }

            BlockPos targetPos = piecePos.offset(entry.getOffset()[0], entry.getOffset()[1], entry.getOffset()[2]);

            // 适配方块：获取给定坐标的地形高度，以 `piecePos` 为基础添加偏移量
            int terrainHeight = piecePos.getY(); // 使用 `piecePos` 的 Y 作为默认值
            try {
                // 使用 `chunkGenerator` 来适配地形高度
                terrainHeight = chunkGenerator.getFirstOccupiedHeight(
                        targetPos.getX(),
                        targetPos.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG,
                        worldGenLevel,
                        level.getChunkSource().randomState() // 获取 RandomState
                );
            } catch (Exception e) {
                LOGGER.warn("Failed to get terrain height for {}, using default.", targetPos);
            }
            targetPos = new BlockPos(targetPos.getX(), terrainHeight, targetPos.getZ());

            // 原版村庄模板需向下 1 格
            BlockPos adjustedPos = targetPos.below();

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(false);

            StructureTemplate template = templateOpt.get();
            template.placeInWorld(level, adjustedPos, adjustedPos, settings, currentRandom, 2);

            // 清理拼图方块
            removeJigsawBlocks(level, template, adjustedPos, settings);

            LOGGER.info("Placed structure {} at {}", structureId, adjustedPos);
        }
    }

    private void removeJigsawBlocks(ServerLevel level, StructureTemplate template, BlockPos pos, StructurePlaceSettings settings) {
        Vec3i rawSize = template.getSize();
        BlockPos size = rotateSize(rawSize, settings.getRotation());
        BlockPos min = pos;
        BlockPos max = pos.offset(size).offset(-1, -1, -1);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(p).is(Blocks.JIGSAW)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private BlockPos rotateSize(Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-size.getZ(), size.getY(), size.getX());
            case CLOCKWISE_180 -> new BlockPos(-size.getX(), size.getY(), -size.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(size.getZ(), size.getY(), -size.getX());
            default -> new BlockPos(size);
        };
    }
}