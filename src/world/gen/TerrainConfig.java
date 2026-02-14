package com.atom.life.world.gen;

/**
 * Tunable parameters for terrain generation.
 * Default values match current ChunkGenerator logic 1:1.
 */
public final class TerrainConfig {

    public final float macroMul   = 0.18f;
    public final float detailMul  = 0.85f;
    public final float ridgeMul   = 0.65f;
    public final float basinMul   = 0.12f;
    public final float plateauMul = 0.10f;

    public final int macroOct   = 4;
    public final int detailOct  = 3;
    public final int ridgeOct   = 4;
    public final int basinOct   = 3;
    public final int plateauOct = 2;

    public final float lacunarity = 2.0f;
    public final float gain       = 0.5f;

    public final float basinA = 0.60f;
    public final float basinB = 0.85f;

    public final float plateauA = 0.55f;
    public final float plateauB = 0.80f;

    public final float mountainRidgeA = 0.55f;
    public final float mountainRidgeB = 0.85f;

    public final float mountainMacroA = 0.35f;
    public final float mountainMacroB = 0.75f;

    public final float macroAmpMul   = 0.75f;
    public final float detailAmpMul  = 0.30f;
    public final float ridgeAmpMul   = 1.20f;
    public final float basinAmpMul   = 0.95f;
    public final float plateauAmpMul = 0.65f;

    public final float terraceStep = 6.0f;

    public final int snowLineOffset = 48;
    public final int snowLineMin = 8;
    public final int snowLineMaxMargin = 8; // snowLine <= sy-8

    public static final int seaLevel = 62;
    public static final int base = 65;
    public static final float freq = 0.008f;
    public static final float amp = 35f;
}
