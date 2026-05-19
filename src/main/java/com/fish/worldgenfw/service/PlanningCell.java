// com/fish/worldgenfw/service/PlanningCell.java
package com.fish.worldgenfw.service;

import net.minecraft.world.level.ChunkPos;

/**
 * 规划单元，代表世界生成中的一个区块网格单元。
 * 将世界按感知半径划分为多个规划单元，每个单元内的结构将一起规划。
 */
public record PlanningCell(int cellX, int cellZ, int radiusInChunks) {

    /**
     * 根据区块坐标计算所属的规划单元。
     */
    public static PlanningCell from(ChunkPos pos, int radiusInChunks) {
        int cx = pos.x / radiusInChunks;
        int cz = pos.z / radiusInChunks;
        return new PlanningCell(cx, cz, radiusInChunks);
    }

    /**
     * 获取该规划单元覆盖的区块范围（近似）。
     */
    public int minChunkX() {
        return cellX * radiusInChunks;
    }

    public int maxChunkX() {
        return (cellX + 1) * radiusInChunks - 1;
    }

    public int minChunkZ() {
        return cellZ * radiusInChunks;
    }

    public int maxChunkZ() {
        return (cellZ + 1) * radiusInChunks - 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanningCell that)) return false;
        return cellX == that.cellX && cellZ == that.cellZ;
    }

    @Override
    public int hashCode() {
        return cellX * 31 + cellZ;
    }
}