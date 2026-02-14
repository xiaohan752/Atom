package com.atom.life.data;

import com.badlogic.gdx.math.MathUtils;
import com.atom.life.input.PlayerCameraController;

/**
 * Player save data.
 * x,y,z are EYE (camera) world position for backward compatibility.
 * yawDeg/pitchDeg are the controller's view angles.
 */
public class PlayerState {
    public float x, y, z;
    public float yawDeg, pitchDeg;
    public boolean flyMode;

    public PlayerState() {}

    public static PlayerState fromController(PlayerCameraController ctrl) {
        PlayerState s = new PlayerState();
        s.x = ctrl.feetPos.x;
        s.y = ctrl.feetPos.y + ctrl.eyeHeight;
        s.z = ctrl.feetPos.z;

        s.yawDeg = normalizeYaw(ctrl.yawDeg);
        s.pitchDeg = MathUtils.clamp(ctrl.pitchDeg, -89f, 89f);
        s.flyMode = ctrl.flyMode;
        return s;
    }

    /** Apply loaded save data into controller (single source of truth). */
    public void applyToController(PlayerCameraController ctrl) {
        if (ctrl == null) return;

        // Convert eye pos -> feet pos
        ctrl.feetPos.set(x, y - ctrl.eyeHeight, z);

        ctrl.yawDeg = normalizeYaw(yawDeg);
        ctrl.pitchDeg = MathUtils.clamp(pitchDeg, -89f, 89f);

        ctrl.flyMode = flyMode;

        // Reset motion to avoid weird impulses after load
        ctrl.velocity.setZero();
        ctrl.grounded = false;
    }

    public static float normalizeYaw(float yaw) {
        float y = yaw % 360f;
        if (y < 0) y += 360f;
        return y;
    }
}
