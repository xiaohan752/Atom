package com.atom.life.mesh;

import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.render.BlockAtlas;
import com.atom.life.world.BlockAccess;
import com.atom.life.world.Chunk;
import com.atom.life.world.MesherLightAccess;

/**
 * Per-thread meshing context (ThreadLocal owned).
 * Holds stable refs + per-build mutable fields.
 */
final class MesherContext {

    final BlockRegistry registry;
    final BlockAtlas atlas;
    final MesherCaches caches;
    final BlockSampler sampler;

    final LightSampler light = new LightSampler();
    MesherLightAccess mesherLightAccess;

    // per-build
    Chunk chunk;
    BlockAccess access;
    int baseX;
    int baseZ;

    MesherContext(BlockRegistry registry, BlockAtlas atlas, MesherCaches caches, BlockSampler sampler) {
        this.registry = registry;
        this.atlas = atlas;
        this.caches = caches;
        this.sampler = sampler;
    }

    void begin(Chunk c, BlockAccess access) {
        this.chunk = c;
        this.access = access;
        this.baseX = c.cx * Chunk.SX;
        this.baseZ = c.cz * Chunk.SZ;

        // light access is optional
        if (access instanceof MesherLightAccess) {
            this.mesherLightAccess = (MesherLightAccess) access;
        } else {
            this.mesherLightAccess = null;
        }
    }

    void end() {
        // avoid accidental retention (optional)
        this.chunk = null;
        this.access = null;
        this.mesherLightAccess = null;
        this.baseX = 0;
        this.baseZ = 0;
    }
}
