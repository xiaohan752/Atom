package com.atom.life.input;

import com.badlogic.gdx.math.Vector3;

/**
 * Shared reusable temporaries / caches to avoid per-frame allocations.
 * Package-private: only used internally by PlayerCameraController and subsystems.
 */
final class PlayerContext {

    // Cached view direction (unit), and cached XZ basis
    final Vector3 dir = new Vector3(1, 0, 0);
    final Vector3 forwardXZ = new Vector3(1, 0, 0);
    final Vector3 rightXZ = new Vector3(0, 0, 1);

    // Movement wishes (targets)
    final Vector3 wish = new Vector3();    // ground wish (XZ)
    final Vector3 wish3D = new Vector3();  // fly wish (XYZ)

    // Fly-only basis vectors (avoid reusing rightXZ to prevent cross-mode pollution)
    final Vector3 flyRight = new Vector3();
    final Vector3 flyForward = new Vector3();

    // Axis order buffer (0=X, 1=Y, 2=Z)
    final int[] axisOrder = new int[3];

    // Collision scan ranges (reused)
    final CollisionRanges ranges = new CollisionRanges();

    // Small helper vector to avoid Vector3.Zero usage in lerp patterns (if needed)
    final Vector3 tmp = new Vector3();
}
