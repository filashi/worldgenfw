package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
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

    private final String templateId;
    private final int[] offset;
    private final int yOffset;

    public ClusterPiece(CompoundTag tag) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), tag);
        this.templateId = tag.getString("TemplateId");
        this.offset = tag.getIntArray("Offset");
        this.yOffset = tag.contains("YOffset") ? tag.getInt("YOffset") : -1;
    }

    public ClusterPiece(String templateId, int[] offset, BoundingBox bounds, int yOffset) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), 0, bounds);
        this.templateId = templateId;
        this.offset = offset;
        this.yOffset = yOffset;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("TemplateId", this.templateId);
        tag.putIntArray("Offset", this.offset);
        tag.putInt("YOffset", this.yOffset);
    }

    @Override
    public boolean isCloseToChunk(ChunkPos chunkPos, int distance) {
        return true;
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource random,
                            BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos piecePos) {
        LOGGER.info("postProcess called for {} at {}", templateId, piecePos);

        MinecraftServer server = worldGenLevel.getServer();
        StructureTemplateManager templateManager = server.getStructureManager();

        BlockPos targetXZ = piecePos.offset(offset[0], 0, offset[2]);
        int surfaceY = chunkGenerator.getFirstOccupiedHeight(
                targetXZ.getX(), targetXZ.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG, worldGenLevel,
                server.overworld().getChunkSource().randomState()
        );
        BlockPos targetPos = new BlockPos(targetXZ.getX(), surfaceY + yOffset, targetXZ.getZ());
        ResourceLocation location = ResourceLocation.parse(templateId);
        Rotation rotation = Rotation.NONE;

        // 触发器模板：直接放置拼图方块实体
        if (templateId.startsWith("worldgenfw:trigger_")) {
            placeTriggerBlock(worldGenLevel, targetPos);
            LOGGER.info("Placed trigger at {}", targetPos);
            return;
        }

        Optional<StructureTemplate> templateOpt = templateManager.get(location);
        if (templateOpt.isEmpty()) {
            LOGGER.error("Template not found: {}", location);
            return;
        }
        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        boolean placed = template.placeInWorld(worldGenLevel, targetPos, targetPos, settings, random, 2);
        if (placed) {
            removeJigsawBlocks(worldGenLevel, template, targetPos, rotation);
            LOGGER.info("Placed {} at {}", templateId, targetPos);
        } else {
            LOGGER.error("placeInWorld returned false for {} at {}", templateId, targetPos);
        }
    }

    /**
     * 在指定位置放置一个拼图方块，其 NBT 指向 worldgenfw:trigger_pool。
     */
    private void placeTriggerBlock(WorldGenLevel level, BlockPos pos) {
        // 设置拼图方块
        var jigsawState = Blocks.JIGSAW.defaultBlockState()
                .setValue(JigsawBlock.ORIENTATION, FrontAndTop.NORTH_UP);
        level.setBlock(pos, jigsawState, 2);

        // 设置方块实体 NBT
        if (level.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("name", "minecraft:bottom");
            nbt.putString("target", "minecraft:bottom");
            nbt.putString("pool", "worldgenfw:trigger_pool");
            nbt.putString("final_state", "minecraft:air");
            nbt.putString("joint", "rollable");
            jigsaw.loadWithComponents(nbt, level.registryAccess());
        }
    }

    private static void removeJigsawBlocks(WorldGenLevel level, StructureTemplate template, BlockPos pos, Rotation rotation) {
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