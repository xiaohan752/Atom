package com.atom.life.input;

import com.badlogic.gdx.graphics.PerspectiveCamera;

public final class PlacementPolicy {

    private static final float EPS = 0.001f;

    private final PerspectiveCamera camera;
    private final PlayerCameraController controller; // nullable

    public PlacementPolicy(PerspectiveCamera camera, PlayerCameraController controller) {
        this.camera = camera;
        this.controller = controller;
    }

    public boolean canPlaceAt(int bx, int by, int bz) {

        final float bMinX = bx;
        final float bMaxX = bx + 1f;
        final float bMinY = by;
        final float bMaxY = by + 1f;
        final float bMinZ = bz;
        final float bMaxZ = bz + 1f;

        if (controller != null) {
            final float r = controller.radius;
            final float h = controller.height;

            final float fx = controller.feetPos.x;
            final float fy = controller.feetPos.y;
            final float fz = controller.feetPos.z;

            float pMinX = fx - r;
            float pMaxX = fx + r;
            float pMinY = fy;
            float pMaxY = fy + h;
            float pMinZ = fz - r;
            float pMaxZ = fz + r;

            pMinX += EPS; pMinY += EPS; pMinZ += EPS;
            pMaxX -= EPS; pMaxY -= EPS; pMaxZ -= EPS;

            return !aabbOverlap(
                pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ,
                bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ
            );
        }

        // fallback
        final int ex = fastFloor(camera.position.x);
        final int ey = fastFloor(camera.position.y);
        final int ez = fastFloor(camera.position.z);
        return !(bx == ex && by == ey && bz == ez);
    }

    private static boolean aabbOverlap(
        float aMinX, float aMinY, float aMinZ,
        float aMaxX, float aMaxY, float aMaxZ,
        float bMinX, float bMinY, float bMinZ,
        float bMaxX, float bMaxY, float bMaxZ
    ) {
        return (aMinX < bMaxX && aMaxX > bMinX) &&
            (aMinY < bMaxY && aMaxY > bMinY) &&
            (aMinZ < bMaxZ && aMaxZ > bMinZ);
    }

    /**
     * fastFloor
     */
    public static int fastFloor(float v) {
        int i = (int) v;          // trunc toward 0
        return (v < i) ? (i - 1) : i;
    }
}
