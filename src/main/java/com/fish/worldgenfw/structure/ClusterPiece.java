package com.fish.worldgenfw.structure;

import com.fish.worldgenfw.WorldGenFw;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import java.util.Optional;

public class ClusterPiece extends StructurePiece {
    // 示例：硬编码要放置的结构及其偏移量
    // 你可以在这里实现自己的蓝图加载逻辑
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
        // 如果有需要保存的数据，在此添加
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager structureManager,
                            ChunkGenerator chunkGenerator, RandomSource random,
                            BoundingBox chunkBounds, ChunkPos chunkPos, BlockPos piecePos) {
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
                // 可选：设置旋转、镜像等
                templateOpt.get().placeInWorld(level, targetPos, targetPos, settings, level.getRandom(), 2);
            }
        }
    }
}