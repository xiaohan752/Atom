package com.atom.life.input;

import com.atom.life.hud.ConsoleOverlay;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.atom.life.world.blocks.BlockDef;
import com.atom.life.world.World;

/**
 * Gravity/jump + fluid state + post-collision drag.
 * Keeps behavior aligned with your current controller.
 */
final class PlayerPhysicsSystem {

    private boolean inFluid;
    private float fluidDrag;
    private float fluidBuoyancy;
    private float fluidGravityScale;
    private float fluidMoveScale;

    boolean inFluid() { return inFluid; }
    float fluidMoveScale() { return fluidMoveScale; }

    void updateFluidState(World world, PlayerCameraController pc) {
        inFluid = false;
        fluidDrag = 0f;
        fluidBuoyancy = 0f;
        fluidGravityScale = 1f;
        fluidMoveScale = 1f;

        float sx = pc.feetPos.x;
        float sz = pc.feetPos.z;

        int wx = FastMath.fastFloor(sx);
        int wz = FastMath.fastFloor(sz);

        int yFeet = FastMath.clampY(FastMath.fastFloor(pc.feetPos.y + 0.10f));
        int yMid  = FastMath.clampY(FastMath.fastFloor(pc.feetPos.y + pc.height * 0.55f));

        BlockDef d0 = world.getDefAt(wx, yFeet, wz);
        BlockDef d1 = world.getDefAt(wx, yMid,  wz);

        BlockDef fd = null;
        if (d0 != null && d0.isFluid) fd = d0;
        else if (d1 != null && d1.isFluid) fd = d1;

        if (fd == null) return;

        inFluid = true;
        fluidDrag = (fd.fluidDrag > 0f) ? fd.fluidDrag : 6.0f;
        fluidBuoyancy = fd.fluidBuoyancy;
        fluidGravityScale = (fd.fluidGravityScale > 0f) ? fd.fluidGravityScale : 0.25f;
        fluidMoveScale = (fd.fluidMoveScale > 0f) ? fd.fluidMoveScale : 0.60f;
    }

    void handleJumpAndGravity(PlayerCameraController pc, float dt, ConsoleOverlay console) {
        if (inFluid) {
            // swim up
            if (console == null || !console.isBlockingGameInput()) {
                if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
                    float targetUp = pc.jumpVel * 0.55f;
                    if (pc.velocity.y < targetUp) pc.velocity.y = targetUp;
                }
            }

            pc.velocity.y += pc.gravity * fluidGravityScale * dt;
            pc.velocity.y += fluidBuoyancy * dt;
            return;
        }

        if (console == null || !console.isBlockingGameInput()) {
            if (pc.grounded && Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
                pc.velocity.y = pc.jumpVel;
                pc.grounded = false;
            }
        }

        pc.velocity.y += pc.gravity * dt;
    }

    void applyPostCollisionDrag(PlayerCameraController pc, float dt) {
        if (!inFluid) return;

        float k = MathUtils.clamp(fluidDrag * dt, 0f, 0.98f);
        float mul = 1f - k;

        pc.velocity.x *= mul;
        pc.velocity.z *= mul;
        pc.velocity.y *= (1f - k * 0.6f);
    }
}
