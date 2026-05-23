package com.fish.worldgenfw.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/**
 * 描述一个待放置的模板请求。
 */
public class PlacementRequest {
    private final ResourceLocation templateId;
    private final BlockPos targetPos;
    private final Rotation rotation;

    public PlacementRequest(ResourceLocation templateId, BlockPos targetPos, Rotation rotation) {
        this.templateId = templateId;
        this.targetPos = targetPos;
        this.rotation = rotation;
    }

    public ResourceLocation getTemplateId() {
        return templateId;
    }

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public Rotation getRotation() {
        return rotation;
    }
}