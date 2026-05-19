package com.fish.worldgenfw.service;

import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 空间索引的抽象接口，用于替代原版的 O(n²) 全局扫描。
 * 后续可切换不同算法（八叉树、BVH 等）。
 */
public interface SpatialIndex {
    /**
     * 插入一个结构包围盒。
     */
    void insert(StructureBoundingBox box);

    /**
     * 查询与给定范围相交的所有结构包围盒。
     */
    List<StructureBoundingBox> query(AABB range);

    /**
     * 清空索引。
     */
    void clear();
}