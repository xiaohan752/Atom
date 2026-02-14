package com.atom.life.mesh;

import com.atom.life.world.Chunk;
import com.atom.life.world.MesherLightAccess;

final class LightSampler {

    byte sample(Chunk chunk, MesherLightAccess access, int baseX, int baseZ, int lx, int ly, int lz) {
        if (ly < 0 || ly >= Chunk.SY) return 0;

        if (lx >= 0 && lx < Chunk.SX && lz >= 0 && lz < Chunk.SZ) {
            return chunk.blockLight[chunk.idx(lx, ly, lz)];
        }

        if (access == null) return 0;

        int wx = baseX + lx;
        int wz = baseZ + lz;
        return access.getBlockLight(wx, ly, wz);
    }

    float sample01(Chunk chunk, MesherLightAccess access, int baseX, int baseZ, int lx, int ly, int lz) {
        int l = sample(chunk, access, baseX, baseZ, lx, ly, lz) & 0xFF;
        return l / 7f;
    }
}
