package com.atom.life.world.util;

public final class LongIntHashSet {

    private long[] keys;
    private int[] vals;
    private int[] stamps;
    private int stamp = 1;
    private int size;
    private int mask;
    private int threshold;

    public LongIntHashSet(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;
        keys = new long[cap];
        vals = new int[cap];
        stamps = new int[cap];
        mask = cap - 1;
        threshold = (int) (cap * 0.65f);
    }

    public void reset() {
        stamp++;
        size = 0;
        if (stamp == 0) {
            for (int i = 0; i < stamps.length; i++) stamps[i] = 0;
            stamp = 1;
        }
    }

    public boolean add(long k, int v) {
        if (size >= threshold) resize();

        int idx = mixPair(k, v) & mask;

        while (true) {
            if (stamps[idx] != stamp) {
                stamps[idx] = stamp;
                keys[idx] = k;
                vals[idx] = v;
                size++;
                return true;
            }
            if (keys[idx] == k && vals[idx] == v) return false;
            idx = (idx + 1) & mask;
        }
    }

    private void resize() {
        long[] oldK = keys;
        int[] oldV = vals;
        int[] oldS = stamps;
        int oldStamp = stamp;

        int newCap = oldK.length << 1;
        keys = new long[newCap];
        vals = new int[newCap];
        stamps = new int[newCap];
        mask = newCap - 1;
        threshold = (int) (newCap * 0.65f);

        stamp++;
        size = 0;
        if (stamp == 0) stamp = 1;

        for (int i = 0; i < oldK.length; i++) {
            if (oldS[i] == oldStamp) {
                add(oldK[i], oldV[i]);
            }
        }
    }

    private static int mixPair(long k, int v) {
        int a = (int) (k ^ (k >>> 32));
        int x = a * 0x9E3779B9 ^ v * 0x85EBCA6B;
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }
}
