package com.atom.life.world.gen;

import static com.atom.life.world.gen.TerrainMath.*;

/**
 * Computes terrain weights and chooses dominant TerrainType.
 * Logic matches original ChunkGenerator 1:1.
 */
public final class TerrainClassifier {

    public static final class Weights {
        public final float plainsW;
        public final float plateauW;
        public final float hillsW;
        public final float basinW;
        public final float mountainW;

        public Weights(float plainsW, float plateauW, float hillsW, float basinW, float mountainW) {
            this.plainsW = plainsW;
            this.plateauW = plateauW;
            this.hillsW = hillsW;
            this.basinW = basinW;
            this.mountainW = mountainW;
        }
    }

    public static final class Result {
        public final TerrainType type;
        public final Weights w;

        public Result(TerrainType type, Weights w) {
            this.type = type;
            this.w = w;
        }
    }

    public Result classify(TerrainConfig cfg,
                           float macro01,
                           float basin01,
                           float plat01,
                           float ridge01,
                           float nDetail,
                           int h,
                           int seaY) {

        // basin stronger where basin01 is high
        float basinW = smoothstep(cfg.basinA, cfg.basinB, basin01);

        // plateau where plat01 is high, but avoid deep basins
        float plateauW = smoothstep(cfg.plateauA, cfg.plateauB, plat01) * (1.0f - basinW);

        // mountains where ridge is strong and macro is not too low
        float mountainW = smoothstep(cfg.mountainRidgeA, cfg.mountainRidgeB, ridge01)
            * smoothstep(cfg.mountainMacroA, cfg.mountainMacroB, macro01);

        // hills: detail strength
        float hillsW = clamp01(absf(nDetail) * 1.2f) * (1.0f - mountainW);

        // plains as leftover
        float plainsW = clamp01(1.0f - (basinW + plateauW + mountainW + hillsW));

        // Decide type (dominant weight)
        TerrainType type = TerrainType.PLAINS;
        float best = plainsW;

        if (plateauW > best) { best = plateauW; type = TerrainType.PLATEAU; }
        if (hillsW   > best) { best = hillsW;   type = TerrainType.HILLS; }
        if (basinW   > best) { best = basinW;   type = TerrainType.BASIN; }
        if (mountainW> best) { best = mountainW;type = TerrainType.MOUNTAINS; }

        // small tweak: if very low and close to sea, prefer basin for shore shaping
        if (h <= seaY + 2 && basinW > 0.35f) type = TerrainType.BASIN;

        return new Result(type, new Weights(plainsW, plateauW, hillsW, basinW, mountainW));
    }
}
