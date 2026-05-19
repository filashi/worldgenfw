package com.fish.worldgenfw.service;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于八叉树的空间索引实现，存储 StructureBoundingBox。
 */
public class SimpleOctree implements SpatialIndex {

    private static final double WORLD_MIN = -30_000_000.0;
    private static final double WORLD_MAX = 30_000_000.0;
    private static final int MAX_DEPTH = 6;

    private final Node root;

    public SimpleOctree() {
        this.root = new Node(WORLD_MIN, WORLD_MIN, WORLD_MIN,
                WORLD_MAX, WORLD_MAX, WORLD_MAX, 0);
    }

    @Override
    public void insert(StructureBoundingBox box) {
        root.insert(box);
    }

    @Override
    public List<StructureBoundingBox> query(AABB range) {
        List<StructureBoundingBox> results = new ArrayList<>();
        root.query(range, results);
        return results;
    }

    @Override
    public List<StructureBoundingBox> getAll() {
        List<StructureBoundingBox> all = new ArrayList<>();
        root.collectAll(all);
        return all;
    }


    @Override
    public void clear() {
        root.clear();
    }

    // ---------- 内部节点类 ----------
    private static class Node {
        private final double minX, minY, minZ, maxX, maxY, maxZ;
        private final int depth;
        private final List<StructureBoundingBox> items = new ArrayList<>();
        private Node[] children;

        Node(double minX, double minY, double minZ,
             double maxX, double maxY, double maxZ, int depth) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.depth = depth;
        }

        void insert(StructureBoundingBox box) {
            if (depth >= MAX_DEPTH) {
                items.add(box);
                return;
            }
            if (children == null) {
                split();
            }
            int childIndex = getChildIndex(box.box());
            if (childIndex != -1) {
                children[childIndex].insert(box);
            } else {
                items.add(box);
            }
        }

        void query(AABB range, List<StructureBoundingBox> results) {
            for (StructureBoundingBox item : items) {
                if (item.box().intersects(range)) {
                    results.add(item);
                }
            }
            if (children != null) {
                for (Node child : children) {
                    if (child.intersects(range)) {
                        child.query(range, results);
                    }
                }
            }
        }

        void clear() {
            items.clear();
            if (children != null) {
                for (Node child : children) {
                    child.clear();
                }
                children = null;
            }
        }

        private void split() {
            double midX = (minX + maxX) / 2.0;
            double midY = (minY + maxY) / 2.0;
            double midZ = (minZ + maxZ) / 2.0;
            children = new Node[8];
            children[0] = new Node(minX, minY, minZ, midX, midY, midZ, depth + 1);
            children[1] = new Node(midX, minY, minZ, maxX, midY, midZ, depth + 1);
            children[2] = new Node(minX, midY, minZ, midX, maxY, midZ, depth + 1);
            children[3] = new Node(midX, midY, minZ, maxX, maxY, midZ, depth + 1);
            children[4] = new Node(minX, minY, midZ, midX, midY, maxZ, depth + 1);
            children[5] = new Node(midX, minY, midZ, maxX, midY, maxZ, depth + 1);
            children[6] = new Node(minX, midY, midZ, midX, maxY, maxZ, depth + 1);
            children[7] = new Node(midX, midY, midZ, maxX, maxY, maxZ, depth + 1);
        }

        private int getChildIndex(AABB box) {
            double midX = (minX + maxX) / 2.0;
            double midY = (minY + maxY) / 2.0;
            double midZ = (minZ + maxZ) / 2.0;

            boolean left   = box.maxX <= midX;
            boolean right  = box.minX >= midX;
            boolean bottom = box.maxY <= midY;
            boolean top    = box.minY >= midY;
            boolean near   = box.maxZ <= midZ;
            boolean far    = box.minZ >= midZ;

            if (left && bottom && near)  return 0;
            if (right && bottom && near) return 1;
            if (left && top && near)     return 2;
            if (right && top && near)    return 3;
            if (left && bottom && far)   return 4;
            if (right && bottom && far)  return 5;
            if (left && top && far)      return 6;
            if (right && top && far)     return 7;

            return -1;
        }

        boolean intersects(AABB range) {
            return range.minX < maxX && range.maxX > minX &&
                    range.minY < maxY && range.maxY > minY &&
                    range.minZ < maxZ && range.maxZ > minZ;
        }

        // 在 Node 内部类中添加方法：
        void collectAll(List<StructureBoundingBox> collector) {
            collector.addAll(items);
            if (children != null) {
                for (Node child : children) {
                    child.collectAll(collector);
                }
            }
        }
    }
}