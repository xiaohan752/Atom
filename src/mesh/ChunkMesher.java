package com.atom.life.mesh;

import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.render.BlockAtlas;
import com.atom.life.world.BlockAccess;
import com.atom.life.world.Chunk;

/**
 * ChunkMesher optimized (Plan A) - refactored into sub-meshers:
 * - Public API unchanged
 * - ThreadLocal MesherContext to avoid cross-thread contamination
 */
public class ChunkMesher {

    private static final int STRIDE = 13;

    private final BlockRegistry registry;
    private final BlockAtlas atlas;

    private final MesherCaches caches;
    private final BlockSampler sampler;

    private final VertexWriter writer;
    private final GreedyOpaqueMesher greedy;
    private final SlopeMesher slope;
    private final AlphaMesher alpha;

    // ThreadLocal context (important: ChunkMesher is used by multiple mesh-worker threads)
    private final ThreadLocal<MesherContext> ctxTL;

    public ChunkMesher(BlockRegistry registry, BlockAtlas atlas) {
        this.registry = registry;
        this.atlas = atlas;

        this.caches = new MesherCaches(registry);
        this.sampler = new BlockSampler();

        this.writer = new VertexWriter(STRIDE, atlas);
        this.greedy = new GreedyOpaqueMesher(STRIDE, caches, writer);
        this.slope  = new SlopeMesher(caches, writer);
        this.alpha  = new AlphaMesher(caches, writer);

        this.ctxTL = ThreadLocal.withInitial(() -> new MesherContext(this.registry, this.atlas, this.caches, this.sampler));
    }

    public ChunkMeshData buildMesh(Chunk c, BlockAccess access) {
        if (c == null || !c.isReady()) {
            MeshData empty = new MeshData(new float[0], 0, new short[0], 0, STRIDE);
            return new ChunkMeshData(empty, empty);
        }

        MesherContext ctx = ctxTL.get();
        ctx.begin(c, access);

        try {
            // outputs
            FloatArray vertsO = new FloatArray(8192);
            ShortArray indsO  = new ShortArray(8192);

            FloatArray vertsA = new FloatArray(4096);
            ShortArray indsA  = new ShortArray(4096);

            // 1) greedy opaque cubes
            greedy.emitOpaqueGreedy(ctx, vertsO, indsO);

            // 2) slopes (opaque)
            slope.emitSlopes(ctx, vertsO, indsO);

            // 3) alpha cubes
            alpha.emitAlphaCubes(ctx, vertsA, indsA);

            MeshData opaque = new MeshData(vertsO.toArray(), vertsO.size, indsO.toArray(), indsO.size, STRIDE);
            MeshData alphaM = new MeshData(vertsA.toArray(), vertsA.size, indsA.toArray(), indsA.size, STRIDE);
            return new ChunkMeshData(opaque, alphaM);

        } finally {
            ctx.end();
        }
    }

    /**
     * Optional: call this to refresh caches.
     */
    public void rebuildCaches() {
        caches.rebuild();
    }
}
