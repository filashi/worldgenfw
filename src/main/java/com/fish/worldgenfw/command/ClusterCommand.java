package com.fish.worldgenfw.command;

import com.fish.worldgenfw.WorldGenFw;
import com.fish.worldgenfw.config.ClusterConfig;
import com.fish.worldgenfw.config.ClusterBlueprint;
import com.fish.worldgenfw.structure.StructureTemplateLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

import java.util.Optional;

public class ClusterCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("wgfwtest")
                        .then(Commands.argument("cluster_id", StringArgumentType.string())
                                .executes(ctx -> {
                                    String idStr = StringArgumentType.getString(ctx, "cluster_id");
                                    ResourceLocation clusterId = resolveClusterId(idStr);
                                    if (clusterId == null) {
                                        ctx.getSource().sendFailure(Component.literal("Invalid cluster ID: " + idStr));
                                        return 0;
                                    }
                                    Optional<ClusterBlueprint> blueprintOpt = ClusterConfig.getInstance().getBlueprint(clusterId);
                                    if (blueprintOpt.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal("Blueprint not found: " + clusterId));
                                        return 0;
                                    }
                                    ServerLevel level = ctx.getSource().getLevel();
                                    BlockPos origin = ctx.getSource().getPlayer().blockPosition();
                                    placeCluster(blueprintOpt.get(), level, origin);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Placed cluster: " + clusterId), true);
                                    return 1;
                                })
                        )
        );
    }

    private static ResourceLocation resolveClusterId(String idStr) {
        if (idStr.contains(":")) return ResourceLocation.tryParse(idStr);
        return ResourceLocation.fromNamespaceAndPath(WorldGenFw.MODID, idStr);
    }

    private static void placeCluster(ClusterBlueprint blueprint, ServerLevel level, BlockPos origin) {
        StructureTemplateManager templateManager = level.getStructureManager();
        for (var entry : blueprint.getStructures()) {
            ResourceLocation structureId = entry.getResourceLocation();
            Rotation rotation = switch (entry.getRotation().toLowerCase()) {
                case "clockwise_90" -> Rotation.CLOCKWISE_90;
                case "clockwise_180" -> Rotation.CLOCKWISE_180;
                case "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };

            Optional<StructureTemplate> templateOpt = StructureTemplateLoader.loadTemplate(structureId, templateManager);
            if (templateOpt.isEmpty()) {
                LOGGER.error("Template not found for structure: {}", structureId);
                if (entry.isRequired()) {
                    LOGGER.warn("Skipping cluster placement because required structure is missing: {}", structureId);
                    return;
                }
                continue;
            }

            BlockPos targetPos = origin.offset(entry.getOffset()[0], entry.getOffset()[1], entry.getOffset()[2]);

            // 原版村庄模板在自然生成时基座会嵌入地面，因此我们需要向下平移 1 格
            BlockPos adjustedPos = targetPos.below(); // y - 1

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(Mirror.NONE)
                    .setIgnoreEntities(false);

            StructureTemplate template = templateOpt.get();
            template.placeInWorld(level, adjustedPos, adjustedPos, settings, level.getRandom(), 2);
            fillBottomAir(level, adjustedPos, template, settings); // 新增
            // 清理拼图方块时也要使用调整后的坐标
            removeJigsawBlocks(level, template, adjustedPos, settings);

            LOGGER.info("Placed structure {} at {} (adjusted from {})", structureId, adjustedPos, targetPos);
        }
    }

    /**
     * 移除模板放置后残留的拼图方块。
     */
    private static void removeJigsawBlocks(ServerLevel level, StructureTemplate template, BlockPos pos, StructurePlaceSettings settings) {
        Vec3i rawSize = template.getSize(); // 原始尺寸
        // 根据旋转计算实际尺寸（旋转只交换/取反 X 和 Z）
        BlockPos size = rotateSize(rawSize, settings.getRotation());

        BlockPos min = pos;
        BlockPos max = pos.offset(size).offset(-1, -1, -1);

        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(p).is(Blocks.JIGSAW)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /**
     * 将 Vec3i 按照 Rotation 旋转后得到新的 BlockPos。
     * 旋转规则与 BlockPos 的 rotate 方法一致。
     */
    private static BlockPos rotateSize(Vec3i size, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-size.getZ(), size.getY(), size.getX());
            case CLOCKWISE_180 -> new BlockPos(-size.getX(), size.getY(), -size.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(size.getZ(), size.getY(), -size.getX());
            default -> new BlockPos(size);
        };
    }

    /**
     * 填充模板底部一层的空气方块，使用其下方方块的材料，解决“护城河”问题。
     */
    private static void fillBottomAir(ServerLevel level, BlockPos pos, StructureTemplate template, StructurePlaceSettings settings) {
        Vec3i rawSize = template.getSize();
        BlockPos size = rotateSize(rawSize, settings.getRotation());
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                BlockPos check = pos.offset(x, 0, z);
                if (level.getBlockState(check).isAir()) {
                    // 使用正下方方块的材料，如果下方是空气则用泥土
                    BlockState belowState = level.getBlockState(check.below());
                    Block block = belowState.isAir() ? Blocks.DIRT : belowState.getBlock();
                    level.setBlock(check, block.defaultBlockState(), 2);
                }
            }
        }
    }
}