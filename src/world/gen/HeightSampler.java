package com.atom.life.world.gen;

import static com.atom.life.world.gen.TerrainConfig.*;
import static com.atom.life.world.gen.TerrainMath.*;

/**
 * Samples height + dominant terrain type at world coordinates.
 * Logic matches original ChunkGenerator.computeHeightAndType() 1:1.
 */
public final class HeightSampler {

    public static final class HeightResult {
        public final int h;
        public final TerrainType type;

        public HeightResult(int h, TerrainType type) {
            this.h = h;
            this.type = type;
        }
    }

    private final TerrainConfig cfg;
    private final NoiseField noise;
    private final TerrainClassifier classifier = new TerrainClassifier();

    public HeightSampler(TerrainConfig cfg, NoiseField noise) {
        this.cfg = cfg;
        this.noise = noise;
    }

    public HeightResult sample(int wx, int wz, int seaY, int sy) {

        float fMacro   = freq * cfg.macroMul;
        float fDetail  = freq * cfg.detailMul;
        float fRidge   = freq * cfg.ridgeMul;
        float fBasin   = freq * cfg.basinMul;
        float fPlateau = freq * cfg.plateauMul;

        float nMacro  = noise.fbm(wx * fMacro,   wz * fMacro,   cfg.macroOct,   cfg.lacunarity, cfg.gain);
        float nDetail = noise.fbm(wx * fDetail,  wz * fDetail,  cfg.detailOct,  cfg.lacunarity, cfg.gain);
        float nRidge0 = noise.fbm(wx * fRidge,   wz * fRidge,   cfg.ridgeOct,   cfg.lacunarity, cfg.gain);
        float nBasin0 = noise.fbm(wx * fBasin,   wz * fBasin,   cfg.basinOct,   cfg.lacunarity, cfg.gain);
        float nPlat0  = noise.fbm(wx * fPlateau, wz * fPlateau, cfg.plateauOct, cfg.lacunarity, cfg.gain);

        // normalize to [0,1]
        float macro01 = to01(nMacro);
        float basin01 = to01(nBasin0);
        float plat01  = to01(nPlat0);

        // ridged noise in [0,1] (peaks)
        float ridge01 = ridged01(nRidge0);

        TerrainClassifier.Result prelim = classifier.classify(
            cfg, macro01, basin01, plat01, ridge01, nDetail, /*h*/ 1, seaY
        );

        float basinW   = prelim.w.basinW;
        float plateauW = prelim.w.plateauW;
        float mountainW= prelim.w.mountainW;

        float hBase = base;

        float hMacro = nMacro * (amp * cfg.macroAmpMul);
        float hDetail = nDetail * (amp * cfg.detailAmpMul);
        float hRidge = ridge01 * (amp * cfg.ridgeAmpMul) * mountainW;
        float hBasin = basinW * (amp * cfg.basinAmpMul);
        float hPlateau = plateauW * (amp * cfg.plateauAmpMul);

        float raw = hBase + hMacro + hDetail + hRidge + hPlateau - hBasin;

        // terrace on plateau
        if (plateauW > 0.01f) {
            float terraced = terrace(raw, cfg.terraceStep);
            raw = lerp(raw, terraced, clamp01(plateauW));
        }

        int h = fastFloor(raw);
        h = clampInt(h, 1, sy - 2);

        // Now choose dominant type with final h so the same tweak applies
        TerrainClassifier.Result finalRes = classifier.classify(
            cfg, macro01, basin01, plat01, ridge01, nDetail, h, seaY
        );

        return new HeightResult(h, finalRes.type);
    }
}
