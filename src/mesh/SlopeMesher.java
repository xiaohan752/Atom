package com.atom.life.mesh;

import com.atom.life.world.blocks.BlockRegistry;
//import com.atom.life.world.World;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
//import com.atom.life.blocks.Blocks;
import com.atom.life.world.Chunk;

import java.util.Set;

/**
 * Slope geometry mesher (keeps your current slope emit logic; only reads tiles/emission from caches).
 */
final class SlopeMesher {

    private final MesherCaches caches;
    private final VertexWriter writer;

    SlopeMesher(MesherCaches caches, VertexWriter writer) {
        this.caches = caches;
        this.writer = writer;
    }

    void emitSlopes(MesherContext ctx, FloatArray vertsO, ShortArray indsO) {
        final Chunk c = ctx.chunk;
        final int sx = Chunk.SX, sy = Chunk.SY, sz = Chunk.SZ;
        final int baseX = ctx.baseX, baseZ = ctx.baseZ;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x0 = 0; x0 < sx; x0++) {
                    byte id = c.getLocal(x0, y, z);
                    if (!ctx.registry.nameOf(id).toLowerCase().contains("slope")) continue;
                    emitSlope(ctx, vertsO, indsO, baseX, baseZ, x0, y, z, id);
                }
            }
        }
    }

    private void emitSlope(MesherContext ctx, FloatArray verts, ShortArray inds,
                           int baseX, int baseZ,
                           int lx, int ly, int lz, byte id) {

        float minX = lx;
        float minY = ly;
        float minZ = lz;
        float maxX = lx + 1f;
        float maxY = ly + 1f;
        float maxZ = lz + 1f;

        int ii = id & 0xFF;

        int tBottom = caches.tileBottom[ii];
        int tSide   = caches.tileSide[ii];
        int tSlope  = caches.tileTop[ii];

        Chunk c = ctx.chunk;

        byte below = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly - 1, lz);
        byte nxN   = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx - 1, ly, lz);
        byte nxP   = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx + 1, ly, lz);
        byte nzN   = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly, lz - 1);
        byte nzP   = ctx.sampler.sample(c, ctx.access, baseX, baseZ, lx, ly, lz + 1);

        // helper: sample light from outside cell and convert to 0..1
        // (slope face uses +Y as a reasonable “outside” sample)
        java.util.function.IntFunction<Float> light01 = (dir) -> {
            int ox = 0, oy = 0, oz = 0;
            // dir: 0:+X 1:-X 2:+Y 3:-Y 4:+Z 5:-Z
            switch (dir) {
                case 0 -> ox = +1;
                case 1 -> ox = -1;
                case 2 -> oy = +1;
                case 3 -> oy = -1;
                case 4 -> oz = +1;
                case 5 -> oz = -1;
            }
            return ctx.light.sample01(c, ctx.mesherLightAccess, baseX, baseZ, lx + ox, ly + oy, lz + oz);
        };

        // bottom (-Y): outside is below cell
        if (!blocksOccludeFaceFast(ctx, below, 1, true)) {
            float emission = light01.apply(3);
            writer.addQuad(verts, inds,
                minX, minY, minZ,
                maxX, minY, minZ,
                maxX, minY, maxZ,
                minX, minY, maxZ,
                0, -1, 0,
                0, 0, 1, 0, 1, 1, 0, 1,
                tBottom,
                emission);
        }

        if (pick(ctx.registry, "*slope_xp", (byte) 0).contains(id)) {
            if (!blocksOccludeFaceFast(ctx, nxN, 0, true)) {
                float emission = light01.apply(1);
                writer.addQuad(verts, inds,
                    minX, minY, minZ,
                    minX, minY, maxZ,
                    minX, maxY, maxZ,
                    minX, maxY, minZ,
                    -1, 0, 0,
                    0, 0, 1, 0, 1, 1, 0, 1,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nzN, 2, true)) {
                float emission = light01.apply(5);
                writer.addTri(verts, inds,
                    minX, minY, minZ,
                    minX, maxY, minZ,
                    maxX, minY, minZ,
                    0, 0, -1,
                    0, 0, 0, 1, 1, 0,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nzP, 2, false)) {
                float emission = light01.apply(4);
                writer.addTri(verts, inds,
                    minX, minY, maxZ,
                    maxX, minY, maxZ,
                    minX, maxY, maxZ,
                    0, 0, 1,
                    0, 0, 1, 0, 0, 1,
                    tSide,
                    emission);
            }

            // slope face: sample from +Y outside
            {
                float emission = light01.apply(2);
                writer.addQuad(verts, inds,
                    minX, maxY, minZ,
                    minX, maxY, maxZ,
                    maxX, minY, maxZ,
                    maxX, minY, minZ,
                    0.70710677f, 0.70710677f, 0f,
                    0, 0, 1, 0, 1, 1, 0, 1,
                    tSlope,
                    emission);
            }

        } else if (pick(ctx.registry, "*slope_xn", (byte) 0).contains(id)) {
            if (!blocksOccludeFaceFast(ctx, nxP, 0, false)) {
                float emission = light01.apply(0);
                writer.addQuad(verts, inds,
                    maxX, minY, minZ,
                    maxX, maxY, minZ,
                    maxX, maxY, maxZ,
                    maxX, minY, maxZ,
                    1, 0, 0,
                    0, 0, 0, 1, 1, 1, 1, 0,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nzN, 2, true)) {
                float emission = light01.apply(5);
                writer.addTri(verts, inds,
                    minX, minY, minZ,
                    maxX, maxY, minZ,
                    maxX, minY, minZ,
                    0, 0, -1,
                    0, 0, 1, 1, 1, 0,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nzP, 2, false)) {
                float emission = light01.apply(4);
                writer.addTri(verts, inds,
                    minX, minY, maxZ,
                    maxX, minY, maxZ,
                    maxX, maxY, maxZ,
                    0, 0, 1,
                    0, 0, 1, 0, 1, 1,
                    tSide,
                    emission);
            }

            {
                float emission = light01.apply(2);
                writer.addQuad(verts, inds,
                    maxX, maxY, minZ,
                    minX, minY, minZ,
                    minX, minY, maxZ,
                    maxX, maxY, maxZ,
                    -0.70710677f, 0.70710677f, 0f,
                    0, 0, 0, 1, 1, 1, 1, 0,
                    tSlope,
                    emission);
            }

        } else if (pick(ctx.registry, "*slope_zp", (byte) 0).contains(id)) {
            if (!blocksOccludeFaceFast(ctx, nzP, 2, false)) {
                float emission = light01.apply(4);
                writer.addQuad(verts, inds,
                    minX, minY, maxZ,
                    maxX, minY, maxZ,
                    maxX, maxY, maxZ,
                    minX, maxY, maxZ,
                    0, 0, 1,
                    0, 0, 1, 0, 1, 1, 0, 1,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nxN, 0, true)) {
                float emission = light01.apply(1);
                writer.addTri(verts, inds,
                    minX, minY, minZ,
                    minX, minY, maxZ,
                    minX, maxY, maxZ,
                    -1, 0, 0,
                    0, 0, 1, 0, 1, 1,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nxP, 0, false)) {
                float emission = light01.apply(0);
                writer.addTri(verts, inds,
                    maxX, minY, minZ,
                    maxX, maxY, maxZ,
                    maxX, minY, maxZ,
                    1, 0, 0,
                    0, 0, 1, 1, 1, 0,
                    tSide,
                    emission);
            }

            {
                float emission = light01.apply(2);
                writer.addQuad(verts, inds,
                    minX, maxY, maxZ,
                    maxX, maxY, maxZ,
                    maxX, minY, minZ,
                    minX, minY, minZ,
                    0f, 0.70710677f, -0.70710677f,
                    0, 1, 1, 1, 1, 0, 0, 0,
                    tSlope,
                    emission);
            }

        } else if (pick(ctx.registry, "*slope_zn", (byte) 0).contains(id)) {
            if (!blocksOccludeFaceFast(ctx, nzN, 2, true)) {
                float emission = light01.apply(5);
                writer.addQuad(verts, inds,
                    minX, minY, minZ,
                    minX, maxY, minZ,
                    maxX, maxY, minZ,
                    maxX, minY, minZ,
                    0, 0, -1,
                    0, 0, 0, 1, 1, 1, 1, 0,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nxN, 0, true)) {
                float emission = light01.apply(1);
                writer.addTri(verts, inds,
                    minX, minY, minZ,
                    minX, minY, maxZ,
                    minX, maxY, minZ,
                    -1, 0, 0,
                    0, 0, 1, 0, 0, 1,
                    tSide,
                    emission);
            }

            if (!blocksOccludeFaceFast(ctx, nxP, 0, false)) {
                float emission = light01.apply(0);
                writer.addTri(verts, inds,
                    maxX, minY, minZ,
                    maxX, maxY, minZ,
                    maxX, minY, maxZ,
                    1, 0, 0,
                    0, 0, 0, 1, 1, 0,
                    tSide,
                    emission);
            }

            {
                float emission = light01.apply(2);
                writer.addQuad(verts, inds,
                    minX, maxY, minZ,
                    minX, minY, maxZ,
                    maxX, minY, maxZ,
                    maxX, maxY, minZ,
                    0f, 0.70710677f, 0.70710677f,
                    0, 0, 0, 1, 1, 1, 1, 0,
                    tSlope,
                    emission);
            }
        }
    }

    private boolean blocksOccludeFaceFast(MesherContext ctx, byte neighborId, int axis, boolean positiveFace) {
        if (neighborId == 0) return false;
        int ni = neighborId & 0xFF;
        if (!caches.isOccluderOpaqueLayer[ni]) return false;
        return ctx.registry.blocksFullFace(neighborId, axis, positiveFace);
    }

    private static Set<Byte> pick(BlockRegistry registry, String expr, byte fallback) {
        return registry.idByExpr(expr, fallback);
    }
}
