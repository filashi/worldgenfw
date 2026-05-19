// com/fish/worldgenfw/service/GlobalIndexManager.java
package com.fish.worldgenfw.service;

import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.HashSet;
import java.util.Set;

/**
 * 全局空间索引与去重集合持有者。
 */
public class GlobalIndexManager {
    public static final SpatialIndex INDEX = new SimpleOctree();
    public static final Set<StructurePiece> INSERTED_PIECES = new HashSet<>();

    public static void clear() {
        INDEX.clear();
        INSERTED_PIECES.clear();
    }
}