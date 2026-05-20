package com.fish.worldgenfw.service;

import net.minecraft.world.phys.AABB;

import java.util.List;

public interface SpatialIndex {
    void insert(StructureBoundingBox box);
    List<StructureBoundingBox> query(AABB range);
    List<StructureBoundingBox> getAll();   // 必须有这一行
    void clear();
}