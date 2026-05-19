package com.fish.worldgenfw.api;

import net.minecraft.resources.ResourceLocation;

public interface StructureClusterProfile {
    boolean allowsClustering();
    int getMinDistanceToOther(ResourceLocation otherStructureId);
    int getMaxClusterDistance();
    boolean canBeClusterCenter();
    int getCenterPriority();
}