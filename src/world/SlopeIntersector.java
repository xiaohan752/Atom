package com.atom.life.world;

import java.util.Set;

import static com.atom.life.world.VoxelRaycaster.INF;

final class SlopeIntersector {

    private static final ThreadLocal<RaycastMath.Interval> INTERVAL_TL =
        ThreadLocal.withInitial(RaycastMath.Interval::new);

    private enum SlopeKind { XP, XN, ZP, ZN }

    static boolean intersectSlopeLocalFast(byte id,
                                           float ox, float oy, float oz,
                                           float dx, float dy, float dz,
                                           float invDx, float invDy, float invDz,
                                           float tEnter, float tExit,
                                           SlopeScratch.SlopeHit outHit, World world) {

        RaycastMath.Interval itv = INTERVAL_TL.get();
        if (!RaycastMath.intersectAABB01Inv(
            ox, oy, oz,
            dx, dy, dz,
            invDx, invDy, invDz,
            tEnter, tExit,
            itv
        )) {
            return false;
        }

        float tMin = itv.t0;
        float tMax = itv.t1;

        // bottom y=0
        testPlaneY0(outHit, ox, oy, oz, dx, dy, dz, invDy, tMin, tMax);

        if (pick(world, "*slope_xp", (byte) 0).contains(id)) {
            testPlaneX(outHit, 0f, -1, ox, oy, oz, dx, dy, dz, invDx, tMin, tMax);
            testTriOnZ(outHit, 0f, 0, 0, -1, SlopeKind.XP, ox, oy, oz, dx, dy, dz, invDz, tMin, tMax);
            testTriOnZ(outHit, 1f, 0, 0,  1, SlopeKind.XP, ox, oy, oz, dx, dy, dz, invDz, tMin, tMax);
            testSlopePlaneXP(outHit, ox, oy, oz, dx, dy, dz, tMin, tMax);

        } else if (pick(world, "*slope_xn", (byte) 0).contains(id)) {
            testPlaneX(outHit, 1f,  1, ox, oy, oz, dx, dy, dz, invDx, tMin, tMax);
            testTriOnZ(outHit, 0f, 0, 0, -1, SlopeKind.XN, ox, oy, oz, dx, dy, dz, invDz, tMin, tMax);
            testTriOnZ(outHit, 1f, 0, 0,  1, SlopeKind.XN, ox, oy, oz, dx, dy, dz, invDz, tMin, tMax);
            testSlopePlaneXN(outHit, ox, oy, oz, dx, dy, dz, tMin, tMax);

        } else if (pick(world, "*slope_zp", (byte) 0).contains(id)) {
            testPlaneZ(outHit, 1f, 0, 0, 1, ox, oy, oz, dx, dy, dz, invDz, tMin, tMax);
            testTriOnX(outHit, 0f, -1, 0, 0, SlopeKind.ZP, ox, oy, oz, dx, dy, dz, invDx, tMin, tMax);
            testTriOnX(outHit, 1f,  1, 0, 0, SlopeKind.ZP, ox, oy, oz, dx, dy, dz, invDx, tMin, tMax);
            testSlopePlaneZP(outHit, ox, oy, oz, dx, dy, dz, tMin, tMax);

        } else if (pick(world, "*slope_zn", (byte) 0).contains(id)) {
            testPlaneZ(outHit, 0f, 0, 0, -1, ox, oy, oz, dx, dy, dz, invDz, tMin, tMax);
            testTriOnX(outHit, 0f, -1, 0, 0, SlopeKind.ZN, ox, oy, oz, dx, dy, dz, invDx, tMin, tMax);
            testTriOnX(outHit, 1f,  1, 0, 0, SlopeKind.ZN, ox, oy, oz, dx, dy, dz, invDx, tMin, tMax);
            testSlopePlaneZN(outHit, ox, oy, oz, dx, dy, dz, tMin, tMax);
        }

        return outHit.hit;
    }

    private static void testPlaneY0(SlopeScratch.SlopeHit best,
                                    float ox, float oy, float oz,
                                    float dx, float dy, float dz,
                                    float invDy,
                                    float tMin, float tMax) {
        // dy == 0 => invDy == INF
        if (invDy == INF) return;

        float t = (0f - oy) * invDy; // ✅ was /dy
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float z = oz + dz * t;
        if (x >= 0f && x <= 1f && z >= 0f && z <= 1f) {
            best.updateIfCloser(t, 0, -1, 0);
        }
    }

    private static void testPlaneX(SlopeScratch.SlopeHit best,
                                   float xPlane, int nx,
                                   float ox, float oy, float oz,
                                   float dx, float dy, float dz,
                                   float invDx,
                                   float tMin, float tMax) {
        if (invDx == INF) return;

        float t = (xPlane - ox) * invDx; // ✅ was /dx
        if (t < tMin || t > tMax) return;

        float y = oy + dy * t;
        float z = oz + dz * t;
        if (y >= 0f && y <= 1f && z >= 0f && z <= 1f) {
            best.updateIfCloser(t, nx, 0, 0);
        }
    }

    private static void testPlaneZ(SlopeScratch.SlopeHit best,
                                   float zPlane, int nx, int ny, int nz,
                                   float ox, float oy, float oz,
                                   float dx, float dy, float dz,
                                   float invDz,
                                   float tMin, float tMax) {
        if (invDz == INF) return;

        float t = (zPlane - oz) * invDz; // ✅ was /dz
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float y = oy + dy * t;
        if (x >= 0f && x <= 1f && y >= 0f && y <= 1f) {
            best.updateIfCloser(t, nx, ny, nz);
        }
    }

    private static void testTriOnZ(SlopeScratch.SlopeHit best,
                                   float zPlane,
                                   int nx, int ny, int nz,
                                   SlopeKind kind,
                                   float ox, float oy, float oz,
                                   float dx, float dy, float dz,
                                   float invDz,
                                   float tMin, float tMax) {
        if (invDz == INF) return;

        float t = (zPlane - oz) * invDz;
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float y = oy + dy * t;
        if (x < 0f || x > 1f || y < 0f || y > 1f) return;

        boolean inside;
        if (kind == SlopeKind.XP) {
            inside = (y <= (1f - x) + 1e-6f);
        } else {
            inside = (y <= x + 1e-6f);
        }
        if (inside) best.updateIfCloser(t, nx, ny, nz);
    }

    private static void testTriOnX(SlopeScratch.SlopeHit best,
                                   float xPlane,
                                   int nx, int ny, int nz,
                                   SlopeKind kind,
                                   float ox, float oy, float oz,
                                   float dx, float dy, float dz,
                                   float invDx,
                                   float tMin, float tMax) {
        if (invDx == INF) return;

        float t = (xPlane - ox) * invDx;
        if (t < tMin || t > tMax) return;

        float z = oz + dz * t;
        float y = oy + dy * t;
        if (z < 0f || z > 1f || y < 0f || y > 1f) return;

        boolean inside;
        if (kind == SlopeKind.ZP) {
            inside = (y <= z + 1e-6f);
        } else {
            inside = (y <= (1f - z) + 1e-6f);
        }
        if (inside) best.updateIfCloser(t, nx, ny, nz);
    }

    private static void testSlopePlaneXP(SlopeScratch.SlopeHit best,
                                         float ox, float oy, float oz,
                                         float dx, float dy, float dz,
                                         float tMin, float tMax) {
        float denom = dx + dy;
        if (denom == 0f) return;
        float t = (1f - (ox + oy)) / denom;
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float y = oy + dy * t;
        float z = oz + dz * t;
        if (x >= 0f && x <= 1f && y >= 0f && y <= 1f && z >= 0f && z <= 1f) {
            best.updateIfCloser(t, 0, 1, 0);
        }
    }

    private static void testSlopePlaneXN(SlopeScratch.SlopeHit best,
                                         float ox, float oy, float oz,
                                         float dx, float dy, float dz,
                                         float tMin, float tMax) {
        float denom = (dy - dx);
        if (denom == 0f) return;
        float t = (ox - oy) / denom;
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float y = oy + dy * t;
        float z = oz + dz * t;
        if (x >= 0f && x <= 1f && y >= 0f && y <= 1f && z >= 0f && z <= 1f) {
            best.updateIfCloser(t, 0, 1, 0);
        }
    }

    private static void testSlopePlaneZP(SlopeScratch.SlopeHit best,
                                         float ox, float oy, float oz,
                                         float dx, float dy, float dz,
                                         float tMin, float tMax) {
        float denom = (dy - dz);
        if (denom == 0f) return;
        float t = (oz - oy) / denom;
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float y = oy + dy * t;
        float z = oz + dz * t;
        if (x >= 0f && x <= 1f && y >= 0f && y <= 1f && z >= 0f && z <= 1f) {
            best.updateIfCloser(t, 0, 1, 0);
        }
    }

    private static void testSlopePlaneZN(SlopeScratch.SlopeHit best,
                                         float ox, float oy, float oz,
                                         float dx, float dy, float dz,
                                         float tMin, float tMax) {
        float denom = (dy + dz);
        if (denom == 0f) return;
        float t = (1f - (oy + oz)) / denom;
        if (t < tMin || t > tMax) return;

        float x = ox + dx * t;
        float y = oy + dy * t;
        float z = oz + dz * t;
        if (x >= 0f && x <= 1f && y >= 0f && y <= 1f && z >= 0f && z <= 1f) {
            best.updateIfCloser(t, 0, 1, 0);
        }
    }

    private static Set<Byte> pick(World world, String expr, byte fallback) {
        return world.idByExpr(expr, fallback);
    }

    private SlopeIntersector() {}
}
