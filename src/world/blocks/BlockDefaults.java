package com.atom.life.world.blocks;

/**
 * Small utilities / default factories.
 * Package-private, same package (no subpackage).
 */
final class BlockDefaults {

    private BlockDefaults() {}

    static BlockDef makeAirDef(byte id,
                               int baseColorRGBA,
                               boolean jitter,
                               float jitterStrength,
                               float lumaJitter) {
        return new BlockDef(
            id, "air",
            false, false,
            BlockDef.Shape.AIR, BlockDef.RenderLayer.NONE,
            0, 0, 0,
            0f,
            baseColorRGBA, jitter, jitterStrength, lumaJitter,
            false, 0f, 0f, 0f, 0f
        );
    }

    static int parseHexRGBA(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);

        try {
            if (s.length() == 6) {
                // RRGGBB -> RRGGBBFF
                int rgb = (int) Long.parseLong(s, 16);
                return (rgb << 8) | 0xFF;
            }
            if (s.length() == 8) {
                // RRGGBBAA
                return (int) Long.parseLong(s, 16);
            }
        } catch (Throwable ignored) {}
        return fallback;
    }
}
