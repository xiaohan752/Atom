package com.atom.life.input;

import com.badlogic.gdx.math.Vector3;
import com.atom.life.world.Chunk;
import com.atom.life.world.World;

/**
 * 只做动作：tryBreak / tryPlace
 * - 不再 raycast：完全依赖 AimSystem 本帧缓存
 */
public final class BlockActions {

    public interface SlopePicker {
        byte pick(Vector3 camDir, byte selectedBlock);
    }

    private final PlacementPolicy placementPolicy;
    private final SlopePicker slopePicker;

    public BlockActions(PlacementPolicy placementPolicy, SlopePicker slopePicker) {
        this.placementPolicy = placementPolicy;
        this.slopePicker = slopePicker;
    }

    public void tryBreak(World world, AimSystem aim) {
        if (!aim.hasValidHit()) return;

        int x = aim.hit().x;
        int y = aim.hit().y;
        int z = aim.hit().z;

        if (y < 0 || y >= Chunk.SY) return;

        world.setBlock(x, y, z, (byte) 0);
    }

    public void tryPlace(World world,
                         AimSystem aim,
                         Vector3 cameraDirection,
                         byte selectedBlock,
                         boolean slopeMode) {

        if (!aim.hasValidHit()) return;

        int hx = aim.hit().x;
        int hy = aim.hit().y;
        int hz = aim.hit().z;
        if (world.interactCircuit(hx, hy, hz)) return;

        int px = aim.hit().x + aim.hit().nx;
        int py = aim.hit().y + aim.hit().ny;
        int pz = aim.hit().z + aim.hit().nz;

        if (py < 0 || py >= Chunk.SY) return;

        if (world.getBlock(px, py, pz) != 0) return;

        if (!placementPolicy.canPlaceAt(px, py, pz)) return;

        byte placeId = slopeMode ? slopePicker.pick(cameraDirection, selectedBlock) : selectedBlock;

        world.setBlock(px, py, pz, placeId);
    }
}
