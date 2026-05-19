package com.fish.worldgenfw.util;

import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 极简八叉树，用于原型验证结构碰撞检测。
 * 注意：本实现仅用于验证，未做线程安全与内存优化，世界生成完成后需调用 clear()。
 */
public class SimpleOctree {
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldGenFramework/Octree");

    // 覆盖整个世界范围（-30M ~ +30M），确保任何结构都能插入
    private static final double WORLD_MIN = -30_000_000.0;
    private static final double WORLD_MAX = 30_000_000.0;
    private static final int MAX_DEPTH = 6;      // 最大深度，控制节点数

    private final Node root;

    public SimpleOctree() {
        this.root = new Node(WORLD_MIN, WORLD_MIN, WORLD_MIN,
                WORLD_MAX, WORLD_MAX, WORLD_MAX, 0);
    }

    /**
     * 插入一个结构边界框。
     */
    public void insert(AABB box) {
        root.insert(box);
    }

    /**
     * 查询与给定范围相交的所有边界框。
     */
    public List<AABB> query(AABB range) {
        List<AABB> results = new ArrayList<>();
        root.query(range, results);
        return results;
    }

    /**
     * 清空八叉树（世界生成完成后调用）。
     */
    public void clear() {
        root.clear();
    }

    /**
     * 内部节点类。
     */
    private static class Node {
        private final double minX, minY, minZ, maxX, maxY, maxZ;
        private final int depth;
        private final List<AABB> items = new ArrayList<>();
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

        void insert(AABB box) {
            // 如果已达最大深度，直接存于当前节点
            if (depth >= MAX_DEPTH) {
                items.add(box);
                return;
            }

            // 否则尝试放入子节点，若不能完全放入任一子节点则存于当前节点
            if (children == null) {
                split();
            }

            int childIndex = getChildIndex(box);
            if (childIndex != -1) {
                children[childIndex].insert(box);
            } else {
                items.add(box); // 跨多个子节点，留在当前节点
            }
        }

        void query(AABB range, List<AABB> results) {
            // 检查当前节点存储的项
            for (AABB item : items) {
                if (item.intersects(range)) {
                    results.add(item);
                }
            }
            // 如果有子节点，递归查询相交的子节点
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

        /**
         * 将当前节点分割为8个子节点。
         */
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

        /**
         * 获取完全包含给定 AABB 的子节点索引，若不能完全包含则返回 -1。
         */
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

            // 必须恰好在一个子区域内
            if (left && bottom && near)  return 0;
            if (right && bottom && near) return 1;
            if (left && top && near)     return 2;
            if (right && top && near)    return 3;
            if (left && bottom && far)   return 4;
            if (right && bottom && far)  return 5;
            if (left && top && far)      return 6;
            if (right && top && far)     return 7;

            return -1; // 跨边界
        }

        /**
         * 检查当前节点区域是否与给定范围相交。
         */
        boolean intersects(AABB range) {
            return range.minX < maxX && range.maxX > minX &&
                    range.minY < maxY && range.maxY > minY &&
                    range.minZ < maxZ && range.maxZ > minZ;
        }
    }
}