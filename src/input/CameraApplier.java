package com.atom.life.input;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

/**
 * Apply feet position + cached view direction to camera.
 */
final class CameraApplier {

    void apply(PlayerCameraController pc, PlayerContext ctx, PerspectiveCamera cam) {
        cam.position.set(pc.feetPos.x, pc.feetPos.y + pc.eyeHeight, pc.feetPos.z);
        cam.direction.set(ctx.dir);
        cam.up.set(Vector3.Y);
        cam.update(true);
    }
}
