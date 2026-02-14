package com.atom.life.input;

import com.atom.life.world.Chunk;
import com.atom.life.world.World;

/**
 * Movement + collisions (axis-separated), including:
 * - axis order sorting (by abs delta)
 * - shared integer bounds structure (1.5)
 * - fastFloor
 * - grounded detection optional toggle
 */
final class CollisionMover {

    private static final float EPS = 0.001f;

    void moveGround(World world, PlayerCameraController pc, PlayerContext ctx, float dx, float dy, float dz) {
        // ground mode uses grounded detection
        moveAxesSorted(world, pc, ctx, dx, dy, dz, PlayerPhysicsToggles.enableGroundedCheck);
    }

    void moveFly(World world, PlayerCameraController pc, PlayerContext ctx, float dx, float dy, float dz, boolean noClip) {
        if (noClip) {
            pc.feetPos.add(dx, dy, dz);
            pc.grounded = false;
            return;
        }
        // fly mode: no grounded detection even if toggle is on
        moveAxesSorted(world, pc, ctx, dx, dy, dz, false);
    }

    private void moveAxesSorted(World world, PlayerCameraController pc, PlayerContext ctx,
                                float dx, float dy, float dz, boolean enableGroundedCheck) {
        pc.grounded = false;

        // Determine axis order by |delta| descending (stable tie-break X>Z>Y)
        fillAxisOrder(ctx.axisOrder, dx, dy, dz);

        for (int k = 0; k < 3; k++) {
            int axis = ctx.axisOrder[k];
            if (axis == 0 && dx != 0f) {
                float newX = moveAxisX(world, pc, ctx.ranges, pc.feetPos.x, pc.feetPos.y, pc.feetPos.z, dx);
                if (newX != pc.feetPos.x + dx) pc.velocity.x = 0f;
                pc.feetPos.x = newX;
            } else if (axis == 2 && dz != 0f) {
                float newZ = moveAxisZ(world, pc, ctx.ranges, pc.feetPos.x, pc.feetPos.y, pc.feetPos.z, dz);
                if (newZ != pc.feetPos.z + dz) pc.velocity.z = 0f;
                pc.feetPos.z = newZ;
            } else if (axis == 1 && dy != 0f) {
                float newY = moveAxisY(world, pc, ctx.ranges, pc.feetPos.x, pc.feetPos.y, pc.feetPos.z, dy, enableGroundedCheck);
                if (newY != pc.feetPos.y + dy) pc.velocity.y = 0f;
                pc.feetPos.y = newY;
            }
        }
    }

    private void fillAxisOrder(int[] out, float dx, float dy, float dz) {
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        float az = Math.abs(dz);

        // stable sort among 3 values without allocations
        // prefer larger first; ties break X(0) > Z(2) > Y(1) for consistency
        int a0 = 0, a1 = 1, a2 = 2;

        // compare swap helper
        if (greater(ay, az, 1, 2)) { int t=a1; a1=a2; a2=t; float tf=ay; ay=az; az=tf; }
        if (greater(ax, ay, 0, a1)) { int t=a0; a0=a1; a1=t; float tf=ax; ax=ay; ay=tf; }
        if (greater(ay, az, a1, a2)) { int t=a1; a1=a2; a2=t; }

        out[0]=a0; out[1]=a1; out[2]=a2;
    }

    private boolean greater(float va, float vb, int axisA, int axisB) {
        if (va > vb) return true;
        if (va < vb) return false;
        // tie-break priority: X(0) > Z(2) > Y(1)
        return axisPriority(axisA) > axisPriority(axisB);
    }

    private int axisPriority(int axis) {
        if (axis == 0) return 3;
        if (axis == 2) return 2;
        return 1; // Y
    }

    private float moveAxisX(World world, PlayerCameraController pc, CollisionRanges r,
                            float x, float y, float z, float dx) {

        float nx = x + dx;

        // Precompute shared integer ranges for Y and Z (1.5)
        float minY = y;
        float maxY = y + pc.height;
        float minZ = z - pc.radius;
        float maxZ = z + pc.radius;

        int y0 = FastMath.clampY(FastMath.fastFloor(minY + EPS));
        int y1 = FastMath.clampY(FastMath.fastFloor(maxY - EPS));
        int z0 = FastMath.fastFloor(minZ + EPS);
        int z1 = FastMath.fastFloor(maxZ - EPS);

        r.setY(y0, y1);
        r.setZ(z0, z1);

        if (dx > 0f) {
            float maxX = nx + pc.radius;
            int bx = FastMath.fastFloor(maxX - EPS);
            if (collidesColumnX(world, bx, r)) return bx - pc.radius - EPS;
        } else {
            float minX = nx - pc.radius;
            int bx = FastMath.fastFloor(minX + EPS);
            if (collidesColumnX(world, bx, r)) return (bx + 1) + pc.radius + EPS;
        }
        return nx;
    }

    private float moveAxisZ(World world, PlayerCameraController pc, CollisionRanges r,
                            float x, float y, float z, float dz) {

        float nz = z + dz;

        // Precompute shared integer ranges for Y and X (1.5)
        float minY = y;
        float maxY = y + pc.height;
        float minX = x - pc.radius;
        float maxX = x + pc.radius;

        int y0 = FastMath.clampY(FastMath.fastFloor(minY + EPS));
        int y1 = FastMath.clampY(FastMath.fastFloor(maxY - EPS));
        int x0 = FastMath.fastFloor(minX + EPS);
        int x1 = FastMath.fastFloor(maxX - EPS);

        r.setY(y0, y1);
        r.setX(x0, x1);

        if (dz > 0f) {
            float maxZ = nz + pc.radius;
            int bz = FastMath.fastFloor(maxZ - EPS);
            if (collidesColumnZ(world, bz, r)) return bz - pc.radius - EPS;
        } else {
            float minZ = nz - pc.radius;
            int bz = FastMath.fastFloor(minZ + EPS);
            if (collidesColumnZ(world, bz, r)) return (bz + 1) + pc.radius + EPS;
        }
        return nz;
    }

    private float moveAxisY(World world, PlayerCameraController pc, CollisionRanges r,
                            float x, float y, float z, float dy, boolean enableGroundedCheck) {

        float ny = y + dy;

        // Precompute shared integer ranges for X and Z (1.5)
        float minX = x - pc.radius;
        float maxX = x + pc.radius;
        float minZ = z - pc.radius;
        float maxZ = z + pc.radius;

        int x0 = FastMath.fastFloor(minX + EPS);
        int x1 = FastMath.fastFloor(maxX - EPS);
        int z0 = FastMath.fastFloor(minZ + EPS);
        int z1 = FastMath.fastFloor(maxZ - EPS);

        r.setX(x0, x1);
        r.setZ(z0, z1);

        if (dy > 0f) {
            float maxY = ny + pc.height;
            int by = FastMath.fastFloor(maxY - EPS);
            if (collidesLayerY(world, by, r)) return by - pc.height - EPS;
        } else {
            float minY = ny;
            int by = FastMath.fastFloor(minY + EPS);
            if (collidesLayerY(world, by, r)) {
                if (enableGroundedCheck) pc.grounded = true;
                return (by + 1) + EPS;
            }
        }

        return ny;
    }

    private boolean collidesColumnX(World world, int bx, CollisionRanges r) {
        int y0 = r.y0, y1 = r.y1;
        int z0 = r.z0, z1 = r.z1;

        for (int by = y0; by <= y1; by++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (world.isSolidAt(bx, by, bz)) return true;
            }
        }
        return false;
    }

    private boolean collidesColumnZ(World world, int bz, CollisionRanges r) {
        int y0 = r.y0, y1 = r.y1;
        int x0 = r.x0, x1 = r.x1;

        for (int by = y0; by <= y1; by++) {
            for (int bx = x0; bx <= x1; bx++) {
                if (world.isSolidAt(bx, by, bz)) return true;
            }
        }
        return false;
    }

    private boolean collidesLayerY(World world, int by, CollisionRanges r) {
        if (by < 0 || by >= Chunk.SY) return false;

        int x0 = r.x0, x1 = r.x1;
        int z0 = r.z0, z1 = r.z1;

        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (world.isSolidAt(bx, by, bz)) return true;
            }
        }
        return false;
    }
}
