package com.atom.life.input;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Cache yaw/pitch -> direction, and (forwardXZ/rightXZ) derived basis.
 * Avoids computing sin/cos twice per frame (fly input + applyToCamera).
 */
final class ViewDirectionCache {

    private boolean dirty = true;

    // cached values
    private float lastYawDeg = Float.NaN;
    private float lastPitchDeg = Float.NaN;

    void markDirty() {
        dirty = true;
    }

    /**
     * Ensure ctx.dir/forwardXZ/rightXZ are up-to-date based on controller yaw/pitch.
     * Must be called once per update() after mouse look / yaw-pitch changes.
     */
    void updateIfDirty(PlayerCameraController pc, PlayerContext ctx) {
        if (!dirty && pc.yawDeg == lastYawDeg && pc.pitchDeg == lastPitchDeg) return;

        lastYawDeg = pc.yawDeg;
        lastPitchDeg = pc.pitchDeg;
        dirty = false;

        // direction
        float yawRad = pc.yawDeg * MathUtils.degreesToRadians;
        float pitchRad = pc.pitchDeg * MathUtils.degreesToRadians;
        float cosP = MathUtils.cos(pitchRad);

        ctx.dir.set(
            MathUtils.cos(yawRad) * cosP,
            MathUtils.sin(pitchRad),
            MathUtils.sin(yawRad) * cosP
        ).nor();

        // forwardXZ/rightXZ for ground movement (ignore pitch)
        ctx.forwardXZ.set(MathUtils.cos(yawRad), 0f, MathUtils.sin(yawRad)).nor();
        ctx.rightXZ.set(ctx.forwardXZ).crs(Vector3.Y).nor();
    }

    /**
     * Init yaw/pitch from a direction vector.
     */
    void setYawPitchFromDirection(PlayerCameraController pc, Vector3 d) {
        Vector3 dn = new Vector3(d).nor();
        float yaw = MathUtils.atan2(dn.z, dn.x) * MathUtils.radiansToDegrees;
        if (yaw < 0) yaw += 360f;

        float pitch = MathUtils.asin(MathUtils.clamp(dn.y, -1f, 1f)) * MathUtils.radiansToDegrees;

        pc.yawDeg = yaw;
        pc.pitchDeg = MathUtils.clamp(pitch, -89f, 89f);
        markDirty();
    }
}
