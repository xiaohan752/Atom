package com.atom.life.world.light;

import com.atom.life.world.blocks.BlockDef;
import com.atom.life.world.blocks.BlockRegistry;

public final class LightCaches {

    private final BlockRegistry registry;
    private final int maxLight;

    private final byte[] emissionLvlCache = new byte[256]; // 0..maxLight
    private final boolean[] opaqueCache = new boolean[256];

    public LightCaches(BlockRegistry registry, int maxLight) {
        this.registry = registry;
        this.maxLight = maxLight;
        rebuild();
    }

    public void rebuild() {
        for (int i = 0; i < 256; i++) {
            byte id = (byte) i;

            opaqueCache[i] = registry.isOpaque(id);

            if (id == 0) {
                emissionLvlCache[i] = 0;
                continue;
            }

            BlockDef d = registry.def(id);
            if (d == null) {
                emissionLvlCache[i] = 0;
                continue;
            }

            float e = d.emission;
            if (e <= 0f) {
                emissionLvlCache[i] = 0;
                continue;
            }

            int lvl = (int) (e * maxLight + 0.999f);
            if (lvl < 0) lvl = 0;
            if (lvl > maxLight) lvl = maxLight;
            emissionLvlCache[i] = (byte) lvl;
        }
    }

    public int emissionToLevel(byte id) {
        return emissionLvlCache[id & 0xFF] & 0xFF;
    }

    public boolean isOpaque(byte id) {
        return opaqueCache[id & 0xFF];
    }

    public boolean isSource(byte id) {
        return (emissionLvlCache[id & 0xFF] & 0xFF) > 0;
    }
}
