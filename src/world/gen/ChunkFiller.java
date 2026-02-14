package com.atom.life.world.gen;

import static com.atom.life.world.gen.TerrainMath.*;

/**
 * Fills chunk blocks from a HeightMap and cached ids.
 * Logic matches original ChunkGenerator.generateChunkBlocks() 1:1.
 */
public final class ChunkFiller {

    private final SurfaceRules surfaceRules = new SurfaceRules();

    public void fill(byte[] blocks,
                     int sx, int sy, int sz,
                     int seaY,
                     TerrainConfig cfg,
                     HeightMap hm,
                     BlockIdResolver ids) {

        int snowLine = clampInt(seaY + cfg.snowLineOffset, cfg.snowLineMin, sy - cfg.snowLineMaxMargin);

        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {

                int h = hm.heightAt(x, z);
                TerrainType t = hm.typeAt(x, z);

                // slope estimation: max delta to right/down (uses 1-cell border)
                int hR = hm.heightAt(x + 1, z);
                int hD = hm.heightAt(x, z + 1);
                int slope = absInt(h - hR);
                int slope2 = absInt(h - hD);
                if (slope2 > slope) slope = slope2;

                SurfaceRules.Result sr = surfaceRules.eval(t, h, seaY, slope, snowLine);

                boolean sandySurface = sr.sandySurface && ids.sand() != ids.air();
                boolean snowySurface = sr.snowySurface && ids.snow() != ids.air();

                for (int y = 0; y < sy; y++) {
                    int idx = (y * sz + z) * sx + x;

                    if (y == 0) {
                        blocks[idx] = ids.bedrock();
                        continue;
                    }

                    if (y > h) {
                        if (y <= seaY && ids.water() != ids.air()) blocks[idx] = ids.water();
                        else blocks[idx] = ids.air();
                        continue;
                    }

                    if (y == h) {
                        if (snowySurface) {
                            blocks[idx] = ids.snow();
                        } else if (sandySurface) {
                            blocks[idx] = ids.sand();
                        } else {
                            blocks[idx] = ids.grass();
                        }
                        continue;
                    }

                    // below surface
                    if (sandySurface) {
                        if (y >= h - 3) blocks[idx] = ids.sand();
                        else blocks[idx] = ids.stone();
                        continue;
                    }

                    if (y >= h - sr.topSoil) blocks[idx] = ids.dirt();
                    else blocks[idx] = ids.stone();
                }
            }
        }
    }
}
