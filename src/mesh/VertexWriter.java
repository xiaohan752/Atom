package com.atom.life.mesh;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.atom.life.render.BlockAtlas;

/**
 * Low-level vertex/index writing helper.
 * Keeps vertex layout consistent across all sub-meshers.
 */
final class VertexWriter {

    static final int VERT_LIMIT = 32760; // avoid short overflow

    private final int stride;
    private final BlockAtlas atlas;

    VertexWriter(int stride, BlockAtlas atlas) {
        this.stride = stride;
        this.atlas = atlas;
    }

    void quadIndices(ShortArray inds, int vStart) {
        inds.add((short) (vStart));
        inds.add((short) (vStart + 1));
        inds.add((short) (vStart + 2));
        inds.add((short) (vStart));
        inds.add((short) (vStart + 2));
        inds.add((short) (vStart + 3));
    }

    void triIndices(ShortArray inds, int vStart) {
        inds.add((short) (vStart));
        inds.add((short) (vStart + 1));
        inds.add((short) (vStart + 2));
    }

    void addVertex(FloatArray verts,
                   float x, float y, float z,
                   float nx, float ny, float nz,
                   float lu, float lv,
                   float u0, float v0, float u1, float v1,
                   float emission) {
        verts.add(x);  verts.add(y);  verts.add(z);
        verts.add(nx); verts.add(ny); verts.add(nz);
        verts.add(lu); verts.add(lv);
        verts.add(u0); verts.add(v0); verts.add(u1); verts.add(v1);
        verts.add(emission);
    }

    void addQuadAlpha(FloatArray verts, ShortArray inds,
                      float ax, float ay, float az,
                      float bx, float by, float bz,
                      float cx, float cy, float cz,
                      float dx, float dy, float dz,
                      float nx, float ny, float nz,
                      int tile,
                      float emission) {

        int vStart = verts.size / stride;
        if (vStart + 4 >= VERT_LIMIT) return;

        float[] uv = atlas.uv(tile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        addVertex(verts, ax, ay, az, nx, ny, nz, 0f, 0f, u0, v0, u1, v1, emission);
        addVertex(verts, bx, by, bz, nx, ny, nz, 1f, 0f, u0, v0, u1, v1, emission);
        addVertex(verts, cx, cy, cz, nx, ny, nz, 1f, 1f, u0, v0, u1, v1, emission);
        addVertex(verts, dx, dy, dz, nx, ny, nz, 0f, 1f, u0, v0, u1, v1, emission);

        quadIndices(inds, vStart);
    }

    void addQuad(FloatArray verts, ShortArray inds,
                 float ax, float ay, float az,
                 float bx, float by, float bz,
                 float cx, float cy, float cz,
                 float dx, float dy, float dz,
                 float nx, float ny, float nz,
                 float lu0, float lv0,
                 float lu1, float lv1,
                 float lu2, float lv2,
                 float lu3, float lv3,
                 int tile,
                 float emission) {

        int vStart = verts.size / stride;
        if (vStart + 4 >= VERT_LIMIT) return;

        float[] uv = atlas.uv(tile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        addVertex(verts, ax, ay, az, nx, ny, nz, lu0, lv0, u0, v0, u1, v1, emission);
        addVertex(verts, bx, by, bz, nx, ny, nz, lu1, lv1, u0, v0, u1, v1, emission);
        addVertex(verts, cx, cy, cz, nx, ny, nz, lu2, lv2, u0, v0, u1, v1, emission);
        addVertex(verts, dx, dy, dz, nx, ny, nz, lu3, lv3, u0, v0, u1, v1, emission);

        quadIndices(inds, vStart);
    }

    void addTri(FloatArray verts, ShortArray inds,
                float ax, float ay, float az,
                float bx, float by, float bz,
                float cx, float cy, float cz,
                float nx, float ny, float nz,
                float lu0, float lv0,
                float lu1, float lv1,
                float lu2, float lv2,
                int tile,
                float emission) {

        int vStart = verts.size / stride;
        if (vStart + 3 >= VERT_LIMIT) return;

        float[] uv = atlas.uv(tile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        addVertex(verts, ax, ay, az, nx, ny, nz, lu0, lv0, u0, v0, u1, v1, emission);
        addVertex(verts, bx, by, bz, nx, ny, nz, lu1, lv1, u0, v0, u1, v1, emission);
        addVertex(verts, cx, cy, cz, nx, ny, nz, lu2, lv2, u0, v0, u1, v1, emission);

        triIndices(inds, vStart);
    }
}
