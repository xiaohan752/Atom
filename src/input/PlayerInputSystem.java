package com.atom.life.input;

import com.atom.life.GameSystems;
import com.atom.life.hud.ConsoleOverlay;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Input -> toggles, mouse look, and "wish" vectors.
 * Does not do collisions, gravity, or camera application.
 */
final class PlayerInputSystem {

    void handleModeToggles(PlayerCameraController pc) {
        // F toggle fly mode
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            pc.flyMode = !pc.flyMode;

            if (pc.flyMode) {
                pc.grounded = false;
                pc.velocity.y = 0f;
            } else {
                pc.velocity.y = Math.min(pc.velocity.y, 0f);
            }
        }

        // N toggle noclip in fly mode
        if (pc.flyMode && Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            pc.flyNoClip = !pc.flyNoClip;
        }
    }

    void handleMouseLook(PlayerCameraController pc) {
        if (!Gdx.input.isCursorCatched()) return;

        float dx = Gdx.input.getDeltaX();
        float dy = Gdx.input.getDeltaY();

        pc.yawDeg = (pc.yawDeg + dx * pc.mouseSensitivity) % 360f;
        if (pc.yawDeg < 0) pc.yawDeg += 360f;

        pc.pitchDeg -= dy * pc.mouseSensitivity;
        pc.pitchDeg = MathUtils.clamp(pc.pitchDeg, -89f, 89f);
    }

    /**
     * Ground mode: build wish vector on XZ plane using cached forwardXZ/rightXZ.
     * Then blend into velocity.xz.
     */
    void handleMoveInput(PlayerCameraController pc, PlayerContext ctx, PlayerPhysicsSystem phys, float dt, ConsoleOverlay console) {
        float ix = 0f, iz = 0f;
        if (console == null || !console.isBlockingGameInput()) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) iz += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) iz -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) ix += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) ix -= 1f;
        }

        ctx.wish.setZero();
        if (ix != 0f) ctx.wish.mulAdd(ctx.rightXZ, ix);
        if (iz != 0f) ctx.wish.mulAdd(ctx.forwardXZ, iz);

        float speed = (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) ? pc.sprintSpeed : pc.walkSpeed;

        // fluid move scaling
        if (phys.inFluid()) speed *= phys.fluidMoveScale();

        if (ctx.wish.len2() > 1e-6f) ctx.wish.nor().scl(speed);

        float control = pc.grounded ? 1f : pc.airControl;
        if (phys.inFluid()) control *= 0.85f;

        float ax = pc.accel * control;
        float k = MathUtils.clamp(ax * dt, 0f, 1f);

        pc.velocity.x += (ctx.wish.x - pc.velocity.x) * k;
        pc.velocity.z += (ctx.wish.z - pc.velocity.z) * k;
    }

    /**
     * Fly mode: build wish3D vector using cached direction.
     * Then blend into velocity.xyz.
     */
    void handleFlyInput(PlayerCameraController pc, PlayerContext ctx, float dt, ConsoleOverlay console) {
        // Fly forward/right should be HORIZONTAL only (ignore pitch)
        // forwardXZ = normalize( (dir.x, 0, dir.z) )
        ctx.flyForward.set(ctx.dir.x, 0f, ctx.dir.z);
        if (ctx.flyForward.len2() < 1e-6f) {
            // looking straight up/down: fallback forward
            ctx.flyForward.set(0f, 0f, 1f);
        } else {
            ctx.flyForward.nor();
        }

        // right = forward x up
        ctx.flyRight.set(ctx.flyForward).crs(Vector3.Y).nor();

        float ix = 0f, iz = 0f, iy = 0f;
        if (console == null || !console.isBlockingGameInput()) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) iz += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) iz -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) ix += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) ix -= 1f;

            // vertical control only by SPACE / SHIFT
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) iy += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) iy -= 1f;
        }

        ctx.wish3D.setZero();

        // horizontal from WASD
        if (ix != 0f) ctx.wish3D.mulAdd(ctx.flyRight, ix);
        if (iz != 0f) ctx.wish3D.mulAdd(ctx.flyForward, iz);

        // vertical only from keys
        if (iy != 0f) ctx.wish3D.y = iy;

        float speed = (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) ? pc.flySprintSpeed : pc.flySpeed;

        if (ctx.wish3D.len2() > 1e-6f) ctx.wish3D.nor().scl(speed);

        float k = MathUtils.clamp(pc.flyAccel * dt, 0f, 1f);
        pc.velocity.x += (ctx.wish3D.x - pc.velocity.x) * k;
        pc.velocity.y += (ctx.wish3D.y - pc.velocity.y) * k;
        pc.velocity.z += (ctx.wish3D.z - pc.velocity.z) * k;

        // no-input damping (same behavior)
        if (ctx.wish3D.len2() <= 1e-6f) {
            float damp = MathUtils.clamp(10f * dt, 0f, 1f);
            pc.velocity.x += (0f - pc.velocity.x) * damp;
            pc.velocity.y += (0f - pc.velocity.y) * damp;
            pc.velocity.z += (0f - pc.velocity.z) * damp;
        }
    }
}
