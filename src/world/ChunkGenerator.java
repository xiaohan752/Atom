package com.atom.life.world;

import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.world.gen.BlockIdResolver;
import com.atom.life.world.gen.ChunkFiller;
import com.atom.life.world.gen.HeightMap;
import com.atom.life.world.gen.HeightSampler;
import com.atom.life.world.gen.NoiseField;
import com.atom.life.world.gen.TerrainConfig;

import static com.atom.life.world.gen.TerrainConfig.seaLevel;
import static com.atom.life.world.gen.TerrainMath.clampInt;

/**
 * Chunk generator with worldMode:
 * - normal: original 5-terrain generation (seamless) + sea fill using blue_water
 * - flat: superflat (no water)
 * - single: only one lime_block_jitter, else air (no water)
 */
public class ChunkGenerator {

    private final String worldMode;

    private final PerlinNoise perlin;
    private final BlockRegistry registry;

    // gen modules (normal)
    private final TerrainConfig cfg = new TerrainConfig();
    private final BlockIdResolver ids = new BlockIdResolver();
    private final HeightSampler sampler;
    private final ChunkFiller filler = new ChunkFiller();

    public ChunkGenerator(long seed, String worldMode, BlockRegistry registry) {
        this.worldMode = (worldMode == null) ? "normal" : worldMode.trim().toLowerCase();
        this.perlin = new PerlinNoise(seed);
        this.registry = registry;

        NoiseField noise = new NoiseField(perlin);
        this.sampler = new HeightSampler(cfg, noise);
    }

    public byte[] generateChunkBlocks(int cx, int cz, int sx, int sy, int sz) {
        ids.resolveOnce(registry);

        switch (worldMode) {
            case "flat":
                return generateFlat(cx, cz, sx, sy, sz);
            case "single":
                return generateSingle(cx, cz, sx, sy, sz);
            case "normal":
            default:
                return generateNormal(cx, cz, sx, sy, sz);
        }
    }

    private byte[] generateNormal(int cx, int cz, int sx, int sy, int sz) {
        byte[] blocks = new byte[sx * sy * sz];

        int worldX0 = cx * sx;
        int worldZ0 = cz * sz;

        int seaY = clampInt(seaLevel, 1, sy - 2);

        int hmW = sx + 1;
        int hmH = sz + 1;
        HeightMap hm = new HeightMap(hmW, hmH);

        for (int z = 0; z <= sz; z++) {
            for (int x = 0; x <= sx; x++) {
                int wx = worldX0 + x;
                int wz = worldZ0 + z;

                HeightSampler.HeightResult hr = sampler.sample(wx, wz, seaY, sy);
                hm.set(x, z, hr.h, hr.type);
            }
        }

        filler.fill(blocks, sx, sy, sz, seaY, cfg, hm, ids);
        return blocks;
    }

    private byte[] generateFlat(int cx, int cz, int sx, int sy, int sz) {
        byte[] blocks = new byte[sx * sy * sz];

        // Superflat layers (tunable):
        // y=0: bedrock
        // y=1..3: stone
        // y=4..6: dirt
        // y=7: grass
        // above: air
        final int yStoneTop = 3;
        final int yDirtTop  = 6;
        final int yGrass    = 7;

        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    int idx = (y * sz + z) * sx + x;

                    if (y == 0) {
                        blocks[idx] = ids.bedrock();
                    } else if (y <= yStoneTop) {
                        blocks[idx] = ids.stone();
                    } else if (y <= yDirtTop) {
                        blocks[idx] = ids.dirt();
                    } else if (y == yGrass) {
                        blocks[idx] = ids.grass();
                    } else {
                        blocks[idx] = ids.air();
                    }
                }
            }
        }

        return blocks;
    }

    private byte[] generateSingle(int cx, int cz, int sx, int sy, int sz) {
        byte[] blocks = new byte[sx * sy * sz];

        // default all air
        for (int i = 0; i < blocks.length; i++) blocks[i] = ids.air();

        // place ONE lime_block_jitter at world
        final int targetWx = 0;
        final int targetWy = 64;
        final int targetWz = 0;

        if (targetWy >= 0 && targetWy < sy) {
            // chunk range check
            int worldX0 = cx * sx;
            int worldZ0 = cz * sz;

            if (targetWx >= worldX0 && targetWx < worldX0 + sx
                && targetWz >= worldZ0 && targetWz < worldZ0 + sz) {

                int lx = targetWx - worldX0;
                int lz = targetWz - worldZ0;

                int idx = (targetWy * sz + lz) * sx + lx;
                blocks[idx] = ids.grass(); // lime_block_jitter
            }
        }

        return blocks;
    }
}
