package com.fish.worldgenfw.api;

import net.minecraft.resources.ResourceLocation;

public interface StructureMetaRegistry {
    void registerProfile(ResourceLocation structureId, StructureClusterProfile profile);
    StructureClusterProfile getProfile(ResourceLocation structureId);
}