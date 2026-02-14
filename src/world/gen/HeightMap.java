package com.atom.life.world.gen;

/**
 * Heightmap + type map for one chunk, with 1-cell border:
 * width = sx+1, height = sz+1.
 */
public final class HeightMap {

    public final int w;
    public final int h;

    private final int[] heights;
    private final TerrainType[] types;

    public HeightMap(int w, int h) {
        this.w = w;
        this.h = h;
        this.heights = new int[w * h];
        this.types = new TerrainType[w * h];
    }

    private int idx(int x, int z) {
        return z * w + x;
    }

    public void set(int x, int z, int height, TerrainType type) {
        int i = idx(x, z);
        heights[i] = height;
        types[i] = type;
    }

    public int heightAt(int x, int z) {
        return heights[idx(x, z)];
    }

    public TerrainType typeAt(int x, int z) {
        return types[idx(x, z)];
    }
}
