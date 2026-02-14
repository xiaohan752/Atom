package com.atom.life.input;

import com.badlogic.gdx.math.Vector3;
import com.atom.life.world.Chunk;
import com.atom.life.world.VoxelRaycaster;
import com.atom.life.world.World;

public final class AimSystem {

    private final VoxelRaycaster.Hit hit = new VoxelRaycaster.Hit();
    private final AimState state;

    private boolean hasValidHit = false;

    public AimSystem() {
        this.state = new AimState();
    }

    public void update(World world, Vector3 origin, Vector3 dirNormalized, float reach) {
        if (!VoxelRaycaster.raycast(world, origin, dirNormalized, reach, hit)) {
            hasValidHit = false;
            state.clear();
            return;
        }

        if (hit.y < 0 || hit.y >= Chunk.SY) {
            hasValidHit = false;
            state.clear();
            return;
        }

        hasValidHit = true;

        byte id = world.getBlock(hit.x, hit.y, hit.z);
        state.setHit(hit.x, hit.y, hit.z, hit.nx, hit.ny, hit.nz, id, world);
    }

    public boolean hasValidHit() {
        return hasValidHit;
    }

    public VoxelRaycaster.Hit hit() {
        return hit;
    }

    public AimState state() {
        return state;
    }
}
