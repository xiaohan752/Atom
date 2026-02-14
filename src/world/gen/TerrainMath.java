package com.atom.life.world.gen;

/**
 * Pure math helpers used by terrain generation.
 */
public final class TerrainMath {

    private TerrainMath() {}

    /** Faster than Math.floor for float. */
    public static int fastFloor(float v) {
        int i = (int) v;
        return v < i ? (i - 1) : i;
    }

    public static int clampInt(int v, int lo, int hi) {
        return (v < lo) ? lo : ((v > hi) ? hi : v);
    }

    public static int absInt(int v) { return v < 0 ? -v : v; }

    public static float absf(float v) { return v < 0f ? -v : v; }

    public static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    /** [-1,1] -> [0,1] */
    public static float to01(float n) {
        return clamp01(n * 0.5f + 0.5f);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3f - 2f * t);
    }

    /**
     * Ridged transform:
     * n in [-1,1] -> ridge in [0,1]
     * high near 0, lower near +/-1
     */
    public static float ridged01(float n) {
        float r = 1.0f - absf(n); // [0,1]
        return r * r;             // sharpen peaks
    }

    /**
     * Terrace function to create plateau steps.
     * step: e.g. 6.0f makes 6-block height steps.
     */
    public static float terrace(float h, float step) {
        if (step <= 1f) return h;
        float inv = 1.0f / step;
        float k = (float) Math.floor(h * inv);
        float base = k * step;
        float frac = (h - base) * inv; // [0,1)
        // keep some slope inside each step (avoid perfectly flat everywhere)
        float shaped = frac * 0.35f;
        return base + shaped * step;
    }
}
