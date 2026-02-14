package com.atom.life.world;

/**
 * Optional light access for mesher (no change to BlockAccess public API).
 */
public interface MesherLightAccess {
    /** returns block light level in [0..7] */
    byte getBlockLight(int wx, int wy, int wz);
}
