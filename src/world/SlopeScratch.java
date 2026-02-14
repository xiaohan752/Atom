package com.atom.life.world;

final class SlopeScratch {

    static final class SlopeHit {
        boolean hit;
        float t;
        int nx, ny, nz;

        void reset() {
            hit = false;
            t = VoxelRaycaster.INF;
            nx = ny = nz = 0;
        }

        boolean updateIfCloser(float t, int nx, int ny, int nz) {
            if (t < this.t) {
                this.hit = true;
                this.t = t;
                this.nx = nx;
                this.ny = ny;
                this.nz = nz;
                return true;
            }
            return false;
        }
    }

    private static final ThreadLocal<SlopeHit> TL = ThreadLocal.withInitial(SlopeHit::new);

    static SlopeHit get() {
        return TL.get();
    }

    private SlopeScratch() {}
}
