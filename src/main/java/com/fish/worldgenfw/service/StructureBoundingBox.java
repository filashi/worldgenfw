// com/fish/worldgenfw/service/StructureBoundingBox.java
package com.fish.worldgenfw.service;

import net.minecraft.world.phys.AABB;

public record StructureBoundingBox(AABB box, String structureId, int instanceId) {
}