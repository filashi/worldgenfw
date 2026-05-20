package com.fish.worldgenfw.service;

import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalIndexManager {
    public static final SpatialIndex INDEX = new SimpleOctree();
    public static final Set<StructurePiece> INSERTED_PIECES = ConcurrentHashMap.newKeySet();

    public static void clear() {
        INDEX.clear();
        INSERTED_PIECES.clear();
    }
}