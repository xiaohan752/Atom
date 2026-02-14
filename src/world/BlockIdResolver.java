package com.atom.life.world.gen;

import com.atom.life.world.blocks.BlockRegistry;

/**
 * Resolves block ids by name once and caches them for generation.
 * Logic matches original ChunkGenerator.resolveIdsOnce() 1:1.
 */
public final class BlockIdResolver {

    private boolean resolved = false;

    private byte idAir;
    private byte idGrass;
    private byte idDirt;
    private byte idStone;
    private byte idWater;
    private byte idBedrock;

    private byte idSand;
    private byte idSnow;

    public void resolveOnce(BlockRegistry registry) {
        if (resolved) return;

        idAir    = registry.idByName("air", (byte) 0);
        idGrass  = registry.idByName("lime_block_jitter", (byte) 1);
        idDirt   = registry.idByName("brown_block_jitter", (byte) 2);
        idStone  = registry.idByName("light_gray_block_jitter", (byte) 3);

        idWater  = registry.idByName("blue_water", idAir);
        idBedrock= registry.idByName("black_block_jitter", idStone);

        // optional surface blocks; fallbacks exactly as your latest code
        idSand   = registry.idByName("yellow_block_jitter", idDirt);
        idSnow   = registry.idByName("white_block_jitter", idGrass);

        resolved = true;
    }

    public byte air() { return idAir; }
    public byte grass() { return idGrass; }
    public byte dirt() { return idDirt; }
    public byte stone() { return idStone; }
    public byte water() { return idWater; }
    public byte bedrock() { return idBedrock; }
    public byte sand() { return idSand; }
    public byte snow() { return idSnow; }
}
