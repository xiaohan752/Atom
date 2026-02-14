package com.atom.life.world.blocks;

/**
 * Stores BlockDef[256] only. No policy decisions here.
 * Package-private on purpose (no subpackage requested).
 */
final class BlockStore {

    private final BlockDef[] defs = new BlockDef[256];

    BlockDef get(int idx) {
        return defs[idx & 0xFF];
    }

    void set(int idx, BlockDef def) {
        defs[idx & 0xFF] = def;
    }

    BlockDef[] rawArray() {
        return defs;
    }
}
