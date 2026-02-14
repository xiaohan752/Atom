package com.atom.life.mesh;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.atom.life.world.Chunk;

/**
 * Greedy mesher for OPAQUE cubes only.
 * - ThreadLocal greedy mask
 * - No hot-path allocations in quad emit
 */
final class GreedyOpaqueMesher {

    private final int stride;
    private final MesherCaches caches;
    private final VertexWriter writer;

    // mask cache: per-thread
    private final int maskCapacity;
    private final ThreadLocal<int[]> maskTL;

    final LightSampler light = new LightSampler();

    GreedyOpaqueMesher(int stride, MesherCaches caches, VertexWriter writer) {
        this.stride = stride;
        this.caches = caches;
        this.writer = writer;

        int a = Chunk.SY * Chunk.SZ;
        int b = Chunk.SZ * Chunk.SX;
        int c = Chunk.SX * Chunk.SY;
        this.maskCapacity = Math.max(a, Math.max(b, c));
        this.maskTL = ThreadLocal.withInitial(() -> new int[maskCapacity]);
    }

    void emitOpaqueGreedy(MesherContext ctx, FloatArray vertsO, ShortArray indsO) {
        final Chunk c = ctx.chunk;
        final int sx = Chunk.SX, sy = Chunk.SY, sz = Chunk.SZ;

        final int baseX = ctx.baseX;
        final int baseZ = ctx.baseZ;

        final int[] mask = maskTL.get();

        // Greedy across 3 axes
        for (int d = 0; d < 3; d++) {
            final int u = (d + 1) % 3;
            final int v = (d + 2) % 3;

            final int dimD = (d == 0) ? sx : (d == 1 ? sy : sz);
            final int dimU = (u == 0) ? sx : (u == 1 ? sy : sz);
            final int dimV = (v == 0) ? sx : (v == 1 ? sy : sz);

            final int nu = dimU;
            final int nv = dimV;

            final int qx = (d == 0) ? 1 : 0;
            final int qy = (d == 1) ? 1 : 0;
            final int qz = (d == 2) ? 1 : 0;

            for (int xd = -1; xd < dimD; xd++) {

                // build mask
                int n = 0;
                for (int j = 0; j < nv; j++) {
                    for (int i = 0; i < nu; i++) {

                        int ax = 0, ay = 0, az = 0;
                        int bx = 0, by = 0, bz = 0;

                        if (d == 0) ax = xd;
                        else if (d == 1) ay = xd;
                        else az = xd;

                        if (u == 0) ax = i;
                        else if (u == 1) ay = i;
                        else az = i;

                        if (v == 0) ax = j;
                        else if (v == 1) ay = j;
                        else az = j;

                        bx = ax + qx;
                        by = ay + qy;
                        bz = az + qz;

                        byte aId = ctx.sampler.sample(c, ctx.access, baseX, baseZ, ax, ay, az);
                        byte bId = ctx.sampler.sample(c, ctx.access, baseX, baseZ, bx, by, bz);

                        int ai = aId & 0xFF;
                        int bi = bId & 0xFF;

                        boolean aOpaque = caches.isOpaqueCube[ai];
                        boolean bOpaque = caches.isOpaqueCube[bi];

                        if (aOpaque && bOpaque) {
                            mask[n++] = 0;
                            continue;
                        }

                        // a opaque, b not -> face towards +d, light comes from b-cell
                        if (aOpaque && !bOpaque) {
                            boolean bBlocks = blocksOccludeFaceFast(ctx, bId, d, false);
                            if (!bBlocks) {
                                int tile = caches.tileForFaceFast(ai, d, true);

                                int l = ctx.light.sample(c, ctx.mesherLightAccess, baseX, baseZ, bx, by, bz) & 0xFF;
                                int e8 = lightToE8(l);

                                int packed = packMask(tile, 0, e8) + 1;
                                mask[n++] = packed;
                            } else {
                                mask[n++] = 0;
                            }
                            continue;
                        }

                        // b opaque, a not -> face towards -d, light comes from a-cell
                        if (!aOpaque && bOpaque) {
                            boolean aBlocks = blocksOccludeFaceFast(ctx, aId, d, true);
                            if (!aBlocks) {
                                int tile = caches.tileForFaceFast(bi, d, false);

                                int l = ctx.light.sample(c, ctx.mesherLightAccess, baseX, baseZ, ax, ay, az) & 0xFF;
                                int e8 = lightToE8(l);

                                int packed = packMask(tile, 1, e8) + 1;
                                mask[n++] = packed;
                            } else {
                                mask[n++] = 0;
                            }
                            continue;
                        }

                        mask[n++] = 0;
                    }
                }

                // merge mask
                n = 0;
                for (int j = 0; j < nv; j++) {
                    for (int i = 0; i < nu; ) {
                        int val = mask[n];
                        if (val == 0) {
                            i++; n++;
                            continue;
                        }

                        int w = 1;
                        while (i + w < nu && mask[n + w] == val) w++;

                        int h = 1;
                        outer:
                        while (j + h < nv) {
                            int row = n + h * nu;
                            for (int k = 0; k < w; k++) {
                                if (mask[row + k] != val) break outer;
                            }
                            h++;
                        }

                        int packed = (val - 1);
                        int tile = unpackTile(packed);
                        boolean positive = unpackPositive(packed);
                        float emission = unpackEmission01(packed);

                        emitQuadGreedyNoAlloc(
                            ctx,
                            vertsO, indsO,
                            d, u, v,
                            xd, i, j,
                            w, h,
                            positive,
                            tile,
                            emission
                        );

                        for (int yy = 0; yy < h; yy++) {
                            int row = n + yy * nu;
                            for (int xx = 0; xx < w; xx++) mask[row + xx] = 0;
                        }

                        i += w;
                        n += w;
                    }
                }
            }
        }
    }

    // light 0..7 -> 0..255
    private static int lightToE8(int l) {
        if (l <= 0) return 0;
        if (l >= 7) return 255;
        return (l * 255 + 3) / 7;
    }

    private boolean blocksOccludeFaceFast(MesherContext ctx, byte neighborId, int axis, boolean positiveFace) {
        if (neighborId == 0) return false;
        int ni = neighborId & 0xFF;
        if (!caches.isOccluderOpaqueLayer[ni]) return false;
        return ctx.registry.blocksFullFace(neighborId, axis, positiveFace);
    }

    // Greedy quad emit (NO ALLOC)
    private void emitQuadGreedyNoAlloc(MesherContext ctx,
                                       FloatArray verts, ShortArray inds,
                                       int d, int u, int v,
                                       int slice,
                                       int i, int j,
                                       int w, int h,
                                       boolean positiveNormal,
                                       int tile,
                                       float emission) {

        int vStart = verts.size / stride;
        if (vStart + 4 >= VertexWriter.VERT_LIMIT) return;

        int px = 0, py = 0, pz = 0;
        int dux = 0, duy = 0, duz = 0;
        int dvx = 0, dvy = 0, dvz = 0;

        int pdVal = slice + 1;
        if (d == 0) px = pdVal;
        else if (d == 1) py = pdVal;
        else pz = pdVal;

        if (u == 0) px = i;
        else if (u == 1) py = i;
        else pz = i;

        if (v == 0) px = j;
        else if (v == 1) py = j;
        else pz = j;

        if (u == 0) dux = w;
        else if (u == 1) duy = w;
        else duz = w;

        if (v == 0) dvx = h;
        else if (v == 1) dvy = h;
        else dvz = h;

        float c0x = px,               c0y = py,               c0z = pz;
        float c1x = px + dux,         c1y = py + duy,         c1z = pz + duz;
        float c2x = px + dux + dvx,   c2y = py + duy + dvy,   c2z = pz + duz + dvz;
        float c3x = px + dvx,         c3y = py + dvy,         c3z = pz + dvz;

        float nx = 0, ny = 0, nz = 0;
        float sgn = positiveNormal ? 1f : -1f;
        if (d == 0) nx = sgn;
        else if (d == 1) ny = sgn;
        else nz = sgn;

        float[] uv = ctx.atlas.uv(tile);
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];

        float lu0, lv0, lu1, lv1, lu2, lv2, lu3, lv3;
        if (d == 0) {
            lu0 = 0f;        lv0 = 0f;
            lu1 = 0f;        lv1 = (float) w;
            lu2 = (float) h; lv2 = (float) w;
            lu3 = (float) h; lv3 = 0f;
        } else {
            lu0 = 0f;        lv0 = 0f;
            lu1 = (float) w; lv1 = 0f;
            lu2 = (float) w; lv2 = (float) h;
            lu3 = 0f;        lv3 = (float) h;
        }

        if (positiveNormal) {
            writer.addVertex(verts, c0x, c0y, c0z, nx, ny, nz, lu0, lv0, u0, v0, u1, v1, emission);
            writer.addVertex(verts, c1x, c1y, c1z, nx, ny, nz, lu1, lv1, u0, v0, u1, v1, emission);
            writer.addVertex(verts, c2x, c2y, c2z, nx, ny, nz, lu2, lv2, u0, v0, u1, v1, emission);
            writer.addVertex(verts, c3x, c3y, c3z, nx, ny, nz, lu3, lv3, u0, v0, u1, v1, emission);
        } else {
            writer.addVertex(verts, c0x, c0y, c0z, nx, ny, nz, lu0, lv0, u0, v0, u1, v1, emission);
            writer.addVertex(verts, c3x, c3y, c3z, nx, ny, nz, lu3, lv3, u0, v0, u1, v1, emission);
            writer.addVertex(verts, c2x, c2y, c2z, nx, ny, nz, lu2, lv2, u0, v0, u1, v1, emission);
            writer.addVertex(verts, c1x, c1y, c1z, nx, ny, nz, lu1, lv1, u0, v0, u1, v1, emission);
        }

        writer.quadIndices(inds, vStart);
    }

    // packed layout:
    // bits  0..15 : tile
    // bit      16 : sign (0=positive, 1=negative)
    // bits 17..24 : emission (0..255)
    private static int packMask(int tile, int signBit, int emission8) {
        return (tile & 0xFFFF) | ((signBit & 1) << 16) | ((emission8 & 0xFF) << 17);
    }

    private static int unpackTile(int packed) {
        return packed & 0xFFFF;
    }

    private static boolean unpackPositive(int packed) {
        int signBit = (packed >> 16) & 1;
        return signBit == 0;
    }

    private static float unpackEmission01(int packed) {
        int e8 = (packed >> 17) & 0xFF;
        return e8 / 255f;
    }
}
