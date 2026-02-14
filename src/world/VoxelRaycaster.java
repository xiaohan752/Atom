package com.atom.life.world;

import com.badlogic.gdx.math.Vector3;
public final class VoxelRaycaster {

    public static final class Hit {
        public int x, y, z;
        public int nx, ny, nz;
        public float t;
        public void set(int x, int y, int z, int nx, int ny, int nz, float t) {
            this.x=x; this.y=y; this.z=z; this.nx=nx; this.ny=ny; this.nz=nz; this.t=t;
        }
    }

    static final float INF = Float.POSITIVE_INFINITY;

    private static final ThreadLocal<DDAState> DDA_TL = ThreadLocal.withInitial(DDAState::new);

    public static boolean raycast(World world, Vector3 origin, Vector3 dirNorm, float maxDist, Hit out) {
        if (world == null || origin == null || dirNorm == null || out == null) return false;
        if (maxDist <= 0f) maxDist = 0f;

        final float ox = origin.x, oy = origin.y, oz = origin.z;
        final float dx = dirNorm.x, dy = dirNorm.y, dz = dirNorm.z;

        DDAState st = DDA_TL.get();
        st.init(ox, oy, oz, dx, dy, dz);

        final int maxSteps = RaycastMath.estimateMaxSteps(maxDist, st.tDeltaX, st.tDeltaY, st.tDeltaZ);

        for (int i = 0; i < maxSteps; i++) {
            if (st.t > maxDist) break;

            final byte id = world.getBlock(st.x, st.y, st.z);
            if (id != 0) {
//                final int ii = id & 0xFF;

                if (!world.getBlockName(id).toLowerCase().contains("slope")) {
                    out.set(st.x, st.y, st.z, st.nx, st.ny, st.nz, st.t);
                    return true;
                }

                final float tEnter = st.t;
                float tExit = st.peekNextBoundaryT();
                if (tExit > maxDist) tExit = maxDist;

                final float rox = ox - st.x;
                final float roy = oy - st.y;
                final float roz = oz - st.z;

                SlopeScratch.SlopeHit sh = SlopeScratch.get();
                sh.reset();

                if (SlopeIntersector.intersectSlopeLocalFast(
                    id,
                    rox, roy, roz,
                    dx, dy, dz,
                    st.invDx, st.invDy, st.invDz,
                    tEnter, tExit,
                    sh, world
                )) {
                    out.set(st.x, st.y, st.z, sh.nx, sh.ny, sh.nz, sh.t);
                    return true;
                }
            }

            if (!st.stepToNext()) break;
        }

        return false;
    }

    private VoxelRaycaster() {}
}
