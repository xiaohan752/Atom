package com.atom.life.world;

import static com.atom.life.world.VoxelRaycaster.INF;

final class RaycastMath {

    static int fastFloor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    static float abs(float v) {
        return v < 0f ? -v : v;
    }

    static float min3(float a, float b, float c) {
        float m = (a < b) ? a : b;
        return (m < c) ? m : c;
    }

    static float intBoundFast(float s, float invDs, int step, int cell) {
        final float boundary = (step > 0) ? (cell + 1) : (cell);
        float t = (boundary - s) * invDs;
        if (t < 0f) t = 0f;
        return t;
    }

    static int estimateMaxSteps(float maxDist, float tDeltaX, float tDeltaY, float tDeltaZ) {
        float min = min3(tDeltaX, tDeltaY, tDeltaZ);
        if (min == INF || min <= 0f) return 64;

        int est = (int) (maxDist / min) + 4;
        if (est < 16) est = 16;
        if (est > 2048) est = 2048;
        return est;
    }

    static final class Interval {
        float t0, t1;
    }

    /**
     * Slab AABB intersection for local voxel [0..1]^3,
     * but uses invDx/invDy/invDz passed in (no repeated divisions).
     *
     * inv == INF means axis is parallel (dir == 0)
     */
    static boolean intersectAABB01Inv(float ox, float oy, float oz,
                                      float dx, float dy, float dz,
                                      float invDx, float invDy, float invDz,
                                      float tMin, float tMax,
                                      Interval out) {

        float t0 = tMin;
        float t1 = tMax;

        // X
        if (invDx == INF || dx == 0f) {
            if (ox < 0f || ox > 1f) return false;
        } else {
            float tx0 = (0f - ox) * invDx;
            float tx1 = (1f - ox) * invDx;
            if (tx0 > tx1) { float tmp = tx0; tx0 = tx1; tx1 = tmp; }
            if (tx0 > t0) t0 = tx0;
            if (tx1 < t1) t1 = tx1;
            if (t0 > t1) return false;
        }

        // Y
        if (invDy == INF || dy == 0f) {
            if (oy < 0f || oy > 1f) return false;
        } else {
            float ty0 = (0f - oy) * invDy;
            float ty1 = (1f - oy) * invDy;
            if (ty0 > ty1) { float tmp = ty0; ty0 = ty1; ty1 = tmp; }
            if (ty0 > t0) t0 = ty0;
            if (ty1 < t1) t1 = ty1;
            if (t0 > t1) return false;
        }

        // Z
        if (invDz == INF || dz == 0f) {
            if (oz < 0f || oz > 1f) return false;
        } else {
            float tz0 = (0f - oz) * invDz;
            float tz1 = (1f - oz) * invDz;
            if (tz0 > tz1) { float tmp = tz0; tz0 = tz1; tz1 = tmp; }
            if (tz0 > t0) t0 = tz0;
            if (tz1 < t1) t1 = tz1;
            if (t0 > t1) return false;
        }

        out.t0 = t0;
        out.t1 = t1;
        return true;
    }

    private RaycastMath() {}
}
