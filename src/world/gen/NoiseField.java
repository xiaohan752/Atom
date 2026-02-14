package com.atom.life.world.gen;

import com.atom.life.world.PerlinNoise;

import static com.atom.life.world.gen.TerrainMath.ridged01;

/**
 * Wrapper around PerlinNoise to provide the exact sampling patterns used by terrain gen.
 */
public final class NoiseField {

    private final PerlinNoise perlin;

    public NoiseField(PerlinNoise perlin) {
        this.perlin = perlin;
    }

    /** Returns roughly [-1,1] */
    public float fbm(float x, float z, int octaves, float lacunarity, float gain) {
        return perlin.fbm(x, z, octaves, lacunarity, gain);
    }

    /** n in [-1,1] -> ridge in [0,1] (same transform as original) */
    public float ridged01FromNoise(float n) {
        return ridged01(n);
    }
}
