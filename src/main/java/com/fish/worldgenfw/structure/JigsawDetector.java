package com.fish.worldgenfw.structure;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.*;

public class JigsawDetector {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 拼图方块信息
     */
    public record JigsawInfo(BlockPos pos, Direction facing, String targetPool, String name, String jointType) {}

    /**
     * 从 NBT 文件中检测所有拼图方块
     */
    public static List<JigsawInfo> detectFromNBT(ResourceLocation templateId, net.minecraft.server.MinecraftServer server) {
        List<JigsawInfo> jigsaws = new ArrayList<>();
        try {
            // 构造 NBT 文件路径：data/<namespace>/structure/<path>.nbt
            ResourceLocation nbtLocation = ResourceLocation.fromNamespaceAndPath(
                    templateId.getNamespace(),
                    "structure/" + templateId.getPath() + ".nbt"
            );
            Optional<Resource> resourceOpt = server.getResourceManager().getResource(nbtLocation);
            if (resourceOpt.isEmpty()) {
                LOGGER.warn("NBT file not found for template: {}", nbtLocation);
                return jigsaws;
            }

            try (InputStream in = resourceOpt.get().open()) {
                CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                // 获取调色板
                ListTag palettes = root.getList("palettes", 10);
                if (palettes.isEmpty()) {
                    // 旧格式：单个 palette
                    ListTag palette = root.getList("palette", 10);
                    if (!palette.isEmpty()) {
                        palettes = new ListTag();
                        palettes.add(palette);
                    }
                }
                // 获取方块列表
                ListTag blocks = root.getList("blocks", 10);
                if (blocks.isEmpty() || palettes.isEmpty()) {
                    return jigsaws;
                }

                // 解析调色板为 BlockState 列表
                List<List<BlockState>> paletteList = new ArrayList<>();
                for (int p = 0; p < palettes.size(); p++) {
                    List<BlockState> states = new ArrayList<>();
                    ListTag palette = palettes.getList(p);
                    for (int i = 0; i < palette.size(); i++) {
                        CompoundTag stateTag = palette.getCompound(i);
                        BlockState state = NbtUtils.readBlockState(
                                server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                                stateTag
                        );
                        states.add(state);
                    }
                    paletteList.add(states);
                }

                // 遍历方块
                for (int i = 0; i < blocks.size(); i++) {
                    CompoundTag blockTag = blocks.getCompound(i);
                    int paletteIndex = 0; // 默认使用第一个调色板
                    // 实际上，标准格式没有 palette 索引，我们假设只有一个调色板，或者可以从 blockTag 中读取 palette 索引，但原版未提供。通常只有一个 palette。
                    List<BlockState> states = paletteList.get(Math.min(paletteIndex, paletteList.size() - 1));
                    int stateId = blockTag.getInt("state");
                    if (stateId < 0 || stateId >= states.size()) continue;
                    BlockState state = states.get(stateId);

                    if (state.is(Blocks.JIGSAW)) {
                        ListTag posList = blockTag.getList("pos", 3);
                        BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));
                        CompoundTag nbt = blockTag.contains("nbt") ? blockTag.getCompound("nbt") : new CompoundTag();
                        // 从 final_state 获取朝向
                        Direction facing = Direction.NORTH;
                        if (nbt.contains("final_state")) {
                            CompoundTag finalState = nbt.getCompound("final_state");
                            String facingStr = finalState.getString("orientation"); // 1.21 使用 orientation 字符串
                            if (facingStr.isEmpty()) {
                                // 兼容旧格式
                                facingStr = finalState.getString("facing");
                            }
                            facing = Direction.byName(facingStr);
                            if (facing == null) facing = Direction.NORTH;
                        }
                        String targetPool = nbt.getString("target");
                        String name = nbt.getString("name");
                        String joint = nbt.getString("joint");
                        jigsaws.add(new JigsawInfo(pos, facing, targetPool, name, joint));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse NBT for jigsaw detection: {}", templateId, e);
        }
        return jigsaws;
    }

    /**
     * 检测模板中是否存在门方块（基于 NBT）
     */
    public static boolean hasDoors(ResourceLocation templateId, net.minecraft.server.MinecraftServer server) {
        try {
            ResourceLocation nbtLocation = ResourceLocation.fromNamespaceAndPath(
                    templateId.getNamespace(),
                    "structure/" + templateId.getPath() + ".nbt"
            );
            Optional<Resource> resourceOpt = server.getResourceManager().getResource(nbtLocation);
            if (resourceOpt.isEmpty()) return false;
            try (InputStream in = resourceOpt.get().open()) {
                CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                ListTag palettes = root.getList("palettes", 10);
                if (palettes.isEmpty()) {
                    ListTag palette = root.getList("palette", 10);
                    if (!palette.isEmpty()) {
                        palettes = new ListTag();
                        palettes.add(palette);
                    }
                }
                List<List<BlockState>> paletteList = new ArrayList<>();
                for (int p = 0; p < palettes.size(); p++) {
                    List<BlockState> states = new ArrayList<>();
                    ListTag palette = palettes.getList(p);
                    for (int i = 0; i < palette.size(); i++) {
                        states.add(NbtUtils.readBlockState(
                                server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                                palette.getCompound(i)
                        ));
                    }
                    paletteList.add(states);
                }
                ListTag blocks = root.getList("blocks", 10);
                for (int i = 0; i < blocks.size(); i++) {
                    CompoundTag blockTag = blocks.getCompound(i);
                    List<BlockState> states = paletteList.get(0);
                    int stateId = blockTag.getInt("state");
                    if (stateId >= 0 && stateId < states.size()) {
                        BlockState state = states.get(stateId);
                        if (state.is(net.minecraft.tags.BlockTags.DOORS)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check doors in NBT: {}", templateId, e);
        }
        return false;
    }

    /**
     * 选择最佳连接点：如果有门，选择距离最近门的拼图方块；否则随机选一个。
     */
    public static JigsawInfo selectConnectionPoint(List<JigsawInfo> jigsaws, ResourceLocation templateId, net.minecraft.server.MinecraftServer server) {
        if (jigsaws.isEmpty()) return null;
        // 检测门的位置
        List<BlockPos> doorPositions = new ArrayList<>();
        try {
            ResourceLocation nbtLocation = ResourceLocation.fromNamespaceAndPath(
                    templateId.getNamespace(),
                    "structure/" + templateId.getPath() + ".nbt"
            );
            Optional<Resource> resourceOpt = server.getResourceManager().getResource(nbtLocation);
            if (resourceOpt.isPresent()) {
                try (InputStream in = resourceOpt.get().open()) {
                    CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    ListTag palettes = root.getList("palettes", 10);
                    if (palettes.isEmpty()) {
                        ListTag palette = root.getList("palette", 10);
                        if (!palette.isEmpty()) {
                            palettes = new ListTag();
                            palettes.add(palette);
                        }
                    }
                    List<List<BlockState>> paletteList = new ArrayList<>();
                    for (int p = 0; p < palettes.size(); p++) {
                        List<BlockState> states = new ArrayList<>();
                        ListTag palette = palettes.getList(p);
                        for (int i = 0; i < palette.size(); i++) {
                            states.add(NbtUtils.readBlockState(
                                    server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                                    palette.getCompound(i)
                            ));
                        }
                        paletteList.add(states);
                    }
                    ListTag blocks = root.getList("blocks", 10);
                    for (int i = 0; i < blocks.size(); i++) {
                        CompoundTag blockTag = blocks.getCompound(i);
                        List<BlockState> states = paletteList.get(0);
                        int stateId = blockTag.getInt("state");
                        if (stateId >= 0 && stateId < states.size()) {
                            BlockState state = states.get(stateId);
                            if (state.is(net.minecraft.tags.BlockTags.DOORS)) {
                                ListTag posList = blockTag.getList("pos", 3);
                                doorPositions.add(new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2)));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to detect doors for connection selection: {}", templateId, e);
        }

        if (!doorPositions.isEmpty()) {
            return jigsaws.stream()
                    .min(Comparator.comparingDouble(jigsaw ->
                            doorPositions.stream()
                                    .mapToDouble(door -> door.distSqr(jigsaw.pos()))
                                    .min()
                                    .orElse(Double.MAX_VALUE)))
                    .orElse(jigsaws.get(0));
        } else {
            // 无门，随机选一个
            return jigsaws.get(new Random().nextInt(jigsaws.size()));
        }
    }

    /**
     * 根据模板旋转，将局部朝向转换为世界朝向
     */
    public static Direction getWorldDirection(Direction facing, Rotation rotation) {
        Direction worldFacing = facing;
        switch (rotation) {
            case CLOCKWISE_90 -> worldFacing = worldFacing.getClockWise();
            case CLOCKWISE_180 -> worldFacing = worldFacing.getOpposite();
            case COUNTERCLOCKWISE_90 -> worldFacing = worldFacing.getCounterClockWise();
        }
        return worldFacing;
    }
}