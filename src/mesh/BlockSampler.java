package com.atom.life.mesh;

import com.atom.life.world.BlockAccess;
import com.atom.life.world.Chunk;

/**
 * Unified sampling (chunk local + neighbor via BlockAccess).
 */
final class BlockSampler {

    byte sample(Chunk c, BlockAccess access, int baseX, int baseZ, int lx, int ly, int lz) {
        if (ly < 0 || ly >= Chunk.SY) return 0;

        if (lx >= 0 && lx < Chunk.SX && lz >= 0 && lz < Chunk.SZ) {
            return c.getLocal(lx, ly, lz);
        }
        return access.getBlock(baseX + lx, ly, baseZ + lz);
    }
}
