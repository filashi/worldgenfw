package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
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

    private static final String[] TEMPLATES = {
            "ati_structures:ancient_vessel",
            "ati_structures:ati_stoneworks"
    };
    private static final int[][] OFFSETS = {
            {0, 0, 0},
            {48, 0, 0}
    };

    public ClusterPiece(BoundingBox boundingBox) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), 0, boundingBox);
    }

    public ClusterPiece(CompoundTag tag) {
        super(WorldGenFw.CLUSTER_PIECE_TYPE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource random,
                            BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos piecePos) {
        LOGGER.info("postProcess called at {}", piecePos); // 调试日志
        if (!(worldGenLevel instanceof ServerLevel level)) {
            return;
        }

        StructureTemplateManager templateManager = level.getStructureManager();

        for (int i = 0; i < TEMPLATES.length; i++) {
            ResourceLocation templateId = ResourceLocation.parse(TEMPLATES[i]);
            Optional<StructureTemplate> templateOpt = templateManager.get(templateId);

            if (templateOpt.isPresent()) {
                BlockPos targetPos = piecePos.offset(OFFSETS[i][0], OFFSETS[i][1], OFFSETS[i][2]);
                StructurePlaceSettings settings = new StructurePlaceSettings();
                templateOpt.get().placeInWorld(level, targetPos, targetPos, settings, level.getRandom(), 2);
                LOGGER.info("Placed template {} at {}", templateId, targetPos); // 调试日志
            } else {
                LOGGER.error("Template not found: {}", templateId);
            }
        }
    }
}