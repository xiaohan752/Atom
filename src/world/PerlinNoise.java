package com.atom.life.world;

import java.util.Random;

public class PerlinNoise {
    private final int[] p = new int[512];

    public PerlinNoise(long seed) {
        int[] perm = new int[256];
        for (int i = 0; i < 256; i++) perm[i] = i;

        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }

        for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
    }

    private static float fade(float t) {
        // 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static float grad(int hash, float x, float y) {
        // 8 directions
        int h = hash & 7;
        float u = (h < 4) ? x : y;
        float v = (h < 4) ? y : x;
        return (((h & 1) == 0) ? u : -u) + (((h & 2) == 0) ? v : -v);
    }

    // returns roughly [-1,1]
    public float noise(float x, float y) {
        int X = ((int) Math.floor(x)) & 255;
        int Y = ((int) Math.floor(y)) & 255;

        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);

        float u = fade(xf);
        float v = fade(yf);

        int aa = p[p[X] + Y];
        int ab = p[p[X] + Y + 1];
        int ba = p[p[X + 1] + Y];
        int bb = p[p[X + 1] + Y + 1];

        float x1 = lerp(grad(aa, xf, yf),     grad(ba, xf - 1, yf),     u);
        float x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);

        return lerp(x1, x2, v);
    }

    // Fractal Brownian Motion
    public float fbm(float x, float y, int octaves, float lacunarity, float gain) {
        float amp = 1f;
        float freq = 1f;
        float sum = 0f;
        float norm = 0f;

        for (int i = 0; i < octaves; i++) {
            sum += noise(x * freq, y * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm; // normalize to [-1,1] roughly
    }
}

