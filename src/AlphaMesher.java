package com.atom.life.mesh;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.atom.life.world.Chunk;

/**
 * Alpha cubes mesher (per-face, no greedy), keeps your current cull rules.
 */
final class AlphaMesher {

    private final MesherCaches caches;
    private final VertexWriter writer;

    AlphaMesher(MesherCaches caches, VertexWriter writer) {
        this.caches = caches;
        this.writer = writer;
    }

    void emitAlphaCubes(MesherContext ctx, FloatArray vertsA, ShortArray indsA) {
        final Chunk c = ctx.chunk;
        final int sx = Chunk.SX, sy = Chunk.SY, sz = Chunk.SZ;
        final int baseX = ctx.baseX, baseZ = ctx.baseZ;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x0 = 0; x0 < sx; x0++) {

                    byte id = c.getLocal(x0, y, z);
                    if (id == 0) continue;

                    int ii = id & 0xFF;
                    if (!caches.isCube[ii]) continue;
                    if (!caches.isAlphaBlock[ii]) continue;

                    emitCubeAlphaFaces(ctx, vertsA, indsA, baseX, baseZ, x0, y, z, id);
                }
            }
        }
    }

    private void emitCubeAlphaFaces(MesherContext ctx,
                                    FloatArray verts, ShortArray inds,
                                    int baseX, int baseZ,
                                    int lx, int ly, int lz, byte id) {

        final Chunk c = ctx.chunk;

        int ii = id & 0xFF;

        // emission now means "block light" in [0..1]
        float emission = ctx.light.sample01(c, ctx.mesherLightAccess, baseX, baseZ, lx, ly, lz);

        byte nxN = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx - 1, ly, lz);
        byte nxP = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx + 1, ly, lz);
        byte nyN = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly - 1, lz);
        byte nyP = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly + 1, lz);
        byte nzN = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly, lz - 1);
        byte nzP = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly, lz + 1);

        float minX = lx, minY = ly, minZ = lz;
        float maxX = lx + 1f, maxY = ly + 1f, maxZ = lz + 1f;

        // +X
        if (shouldEmitAlphaFace(ctx, id, nxP, 0, false)) {
            writer.addQuadAlpha(verts, inds,
                maxX, minY, maxZ,
                maxX, minY, minZ,
                maxX, maxY, minZ,
                maxX, maxY, maxZ,
                1, 0, 0,
                caches.tileForFaceFast(ii, 0, true),
                emission
            );
        }

        // -X
        if (shouldEmitAlphaFace(ctx, id, nxN, 0, true)) {
            writer.addQuadAlpha(verts, inds,
                minX, minY, minZ,
                minX, minY, maxZ,
                minX, maxY, maxZ,
                minX, maxY, minZ,
                -1, 0, 0,
                caches.tileForFaceFast(ii, 0, false),
                emission
            );
        }

        // +Y
        if (shouldEmitAlphaFace(ctx, id, nyP, 1, false)) {
            writer.addQuadAlpha(verts, inds,
                minX, maxY, maxZ,
                maxX, maxY, maxZ,
                maxX, maxY, minZ,
                minX, maxY, minZ,
                0, 1, 0,
                caches.tileForFaceFast(ii, 1, true),
                emission
            );
        }

        // -Y
        if (shouldEmitAlphaFace(ctx, id, nyN, 1, true)) {
            writer.addQuadAlpha(verts, inds,
                minX, minY, minZ,
                maxX, minY, minZ,
                maxX, minY, maxZ,
                minX, minY, maxZ,
                0, -1, 0,
                caches.tileForFaceFast(ii, 1, false),
                emission
            );
        }

        // +Z
        if (shouldEmitAlphaFace(ctx, id, nzP, 2, false)) {
            writer.addQuadAlpha(verts, inds,
                minX, minY, maxZ,
                maxX, minY, maxZ,
                maxX, maxY, maxZ,
                minX, maxY, maxZ,
                0, 0, 1,
                caches.tileForFaceFast(ii, 2, true),
                emission
            );
        }

        // -Z
        if (shouldEmitAlphaFace(ctx, id, nzN, 2, true)) {
            writer.addQuadAlpha(verts, inds,
                maxX, minY, minZ,
                minX, minY, minZ,
                minX, maxY, minZ,
                maxX, maxY, minZ,
                0, 0, -1,
                caches.tileForFaceFast(ii, 2, false),
                emission
            );
        }
    }

    private boolean shouldEmitAlphaFace(MesherContext ctx, byte selfId, byte neighborId, int axis, boolean neighborPositiveFace) {
        if (sameAlphaType(selfId, neighborId)) return false;

        // quick gate: alpha neighbor cannot occlude (only internal-face rule matters)
        if (neighborId != 0) {
            int ni = neighborId & 0xFF;
            if (caches.isAlphaBlock[ni]) return true;
        }

        if (blocksOccludeFaceFast(ctx, neighborId, axis, neighborPositiveFace)) return false;
        return true;
    }

    private boolean sameAlphaType(byte a, byte b) {
        if (a != b) return false;
        int ai = a & 0xFF;
        return caches.isAlphaBlock[ai];
    }

    private boolean blocksOccludeFaceFast(MesherContext ctx, byte neighborId, int axis, boolean positiveFace) {
        if (neighborId == 0) return false;
        int ni = neighborId & 0xFF;
        if (!caches.isOccluderOpaqueLayer[ni]) return false;
        return ctx.registry.blocksFullFace(neighborId, axis, positiveFace);
    }
}
