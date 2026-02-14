package com.atom.life.world.gen;

import static com.atom.life.world.gen.TerrainMath.*;

public final class SurfaceRules {

    public static final class Result {
        public final boolean sandySurface;
        public final boolean snowySurface;
        public final int topSoil;

        public Result(boolean sandySurface, boolean snowySurface, int topSoil) {
            this.sandySurface = sandySurface;
            this.snowySurface = snowySurface;
            this.topSoil = topSoil;
        }
    }

    public Result eval(TerrainType t, int h, int seaY, int slope, int snowLine) {

        boolean isMountain = (t == TerrainType.MOUNTAINS);
        boolean isBasin = (t == TerrainType.BASIN);
        boolean isPlateau = (t == TerrainType.PLATEAU);

        // basin/shore: sand near/under sea level
        boolean sandySurface = (isBasin && h <= seaY + 2) || (h <= seaY && slope <= 1);

        // high altitude: snow for mountains/plateau
        boolean snowySurface = (h >= snowLine) && (isMountain || isPlateau);

        int topSoil = 3;

        // mountains: thinner soil, more exposed stone when steep
        if (isMountain) {
            topSoil = (slope >= 2) ? 1 : 2;
        }

        return new Result(sandySurface, snowySurface, topSoil);
    }
}
