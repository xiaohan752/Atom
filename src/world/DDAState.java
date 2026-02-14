package com.atom.life.world;

import static com.atom.life.world.VoxelRaycaster.INF;

final class DDAState {

    int x, y, z;

    int stepX, stepY, stepZ;

    float tMaxX, tMaxY, tMaxZ;
    float tDeltaX, tDeltaY, tDeltaZ;

    float t;

    int nx, ny, nz;

    float invDx, invDy, invDz;

    void init(float ox, float oy, float oz, float dx, float dy, float dz) {

        x = RaycastMath.fastFloor(ox);
        y = RaycastMath.fastFloor(oy);
        z = RaycastMath.fastFloor(oz);

        stepX = (dx > 0f) ? 1 : (dx < 0f ? -1 : 0);
        stepY = (dy > 0f) ? 1 : (dy < 0f ? -1 : 0);
        stepZ = (dz > 0f) ? 1 : (dz < 0f ? -1 : 0);

        invDx = (stepX != 0) ? (1f / dx) : INF;
        invDy = (stepY != 0) ? (1f / dy) : INF;
        invDz = (stepZ != 0) ? (1f / dz) : INF;

        tDeltaX = (stepX != 0) ? RaycastMath.abs(invDx) : INF;
        tDeltaY = (stepY != 0) ? RaycastMath.abs(invDy) : INF;
        tDeltaZ = (stepZ != 0) ? RaycastMath.abs(invDz) : INF;

        tMaxX = (stepX != 0) ? RaycastMath.intBoundFast(ox, invDx, stepX, x) : INF;
        tMaxY = (stepY != 0) ? RaycastMath.intBoundFast(oy, invDy, stepY, y) : INF;
        tMaxZ = (stepZ != 0) ? RaycastMath.intBoundFast(oz, invDz, stepZ, z) : INF;

        t = 0f;
        nx = ny = nz = 0;
    }

    float peekNextBoundaryT() {
        return RaycastMath.min3(tMaxX, tMaxY, tMaxZ);
    }

    boolean stepToNext() {
        if (tMaxX < tMaxY) {
            if (tMaxX < tMaxZ) {
                x += stepX;
                t = tMaxX;
                tMaxX += tDeltaX;
                nx = -stepX; ny = 0; nz = 0;
                return true;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                nx = 0; ny = 0; nz = -stepZ;
                return true;
            }
        } else {
            if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
                nx = 0; ny = -stepY; nz = 0;
                return true;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
                nx = 0; ny = 0; nz = -stepZ;
                return true;
            }
        }
    }
}
