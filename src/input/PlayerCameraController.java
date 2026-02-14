package com.atom.life.input;

import com.atom.life.hud.ConsoleOverlay;
import com.atom.life.data.PlayerState;
import com.atom.life.world.World;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;

/**
 * PlayerCameraController (thin shell):
 * - Public API unchanged
 * - Delegates to subsystems: input, direction cache, physics, collision mover, camera applier
 */
public class PlayerCameraController {

    public float yawDeg = 0f;     // 0..360
    public float pitchDeg = 0f;   // -89..89
    public float mouseSensitivity = 0.15f;
//    public boolean cursorCatched = true;

    public final Vector3 feetPos = new Vector3();
    public final Vector3 velocity = new Vector3();
    public boolean grounded = false;

    public float radius = 0.30f;
    public float height = 1.80f;
    public float eyeHeight = 1.62f;

    public float walkSpeed = 5.0f;
    public float sprintSpeed = 10.0f;
    public float accel = 20.0f;
    public float airControl = 0.35f;

    public float gravity = -25.0f;
    public float jumpVel = 8.0f;

    public boolean flyMode = false;
    public float flySpeed = 8.0f;
    public float flySprintSpeed = 16.0f;
    public float flyAccel = 10.0f;
    public boolean flyNoClip = false;

    // Internal systems
    private final PlayerContext ctx = new PlayerContext();
    private final ViewDirectionCache dirCache = new ViewDirectionCache();
    private final PlayerInputSystem input = new PlayerInputSystem();
    private final PlayerPhysicsSystem physics = new PlayerPhysicsSystem();
    private final CollisionMover mover = new CollisionMover();
    private final CameraApplier cameraApplier = new CameraApplier();

    public PlayerCameraController() {}

    public void initFromCamera(PerspectiveCamera cam) {
        feetPos.set(cam.position.x, cam.position.y - eyeHeight, cam.position.z);
        dirCache.setYawPitchFromDirection(this, cam.direction);
        dirCache.updateIfDirty(this, ctx);
    }

    public void applyToCamera(PerspectiveCamera cam) {
        cameraApplier.apply(this, ctx, cam);
    }

    public void update(World world, PerspectiveCamera cam, float dt, ConsoleOverlay console) {
        if (world == null || cam == null) return;

        if (console == null || !console.isBlockingGameInput()){
            // 0) toggle fly mode
            input.handleModeToggles(this);
        }

        // 1) mouse look
        float oldYaw = yawDeg, oldPitch = pitchDeg;
        input.handleMouseLook(this);
        if (yawDeg != oldYaw || pitchDeg != oldPitch) dirCache.markDirty();

        // 1.1) direction cache update (compute once per frame)
        dirCache.updateIfDirty(this, ctx);

        // 2) fluid state (affects move/gravity/drag)
        physics.updateFluidState(world, this);

        if (flyMode) {
            // 3F) fly input -> velocity (3D)
            input.handleFlyInput(this, ctx, dt, console);

            // 4F) move (noclip or collision)
            mover.moveFly(world, this, ctx, velocity.x * dt, velocity.y * dt, velocity.z * dt, flyNoClip);

        } else {
            // 3G) keyboard input -> velocity.xz
            input.handleMoveInput(this, ctx, physics, dt, console);

            // 4G) jump/gravity
            physics.handleJumpAndGravity(this, dt, console);

            // 5G) move + collisions (axis-separated, sorted)
            mover.moveGround(world, this, ctx, velocity.x * dt, velocity.y * dt, velocity.z * dt);

            // 6G) fluid drag after collision resolution
            physics.applyPostCollisionDrag(this, dt);
        }

        // 7) write back to camera
        applyToCamera(cam);
    }

    public PlayerState toPlayerState() {
        PlayerState s = new PlayerState();
        s.x = feetPos.x;
        s.y = feetPos.y + eyeHeight;
        s.z = feetPos.z;
        s.yawDeg = yawDeg;
        s.pitchDeg = pitchDeg;
        return s;
    }

    public void applyPlayerState(PlayerState s) {
        if (s == null) return;

        feetPos.set(s.x, s.y - eyeHeight, s.z);
        yawDeg = s.yawDeg;
        pitchDeg = s.pitchDeg;
        velocity.setZero();

        // keep consistent clamp + cache refresh
        if (pitchDeg < -89f) pitchDeg = -89f;
        if (pitchDeg > 89f) pitchDeg = 89f;
        dirCache.markDirty();
        dirCache.updateIfDirty(this, ctx);
    }
}
