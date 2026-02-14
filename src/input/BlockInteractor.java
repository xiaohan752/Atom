package com.atom.life.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.atom.life.world.World;

import static com.atom.life.GlobalVariables.reach;

public class BlockInteractor {

    private final World world;
    private final PerspectiveCamera camera;

    private final PlayerCameraController controller;

    private final Vector3 dir = new Vector3();

    private final AimSystem aimSystem = new AimSystem();
    private final CooldownGate cooldowns = new CooldownGate(0.1f, 4f); // interval=0.1, firstMult=4
    private final PlacementPolicy placementPolicy;
    private final BlockActions actions;

    public byte selectedBlock = 0;

    public boolean slopeMode = false;

    @SuppressWarnings("unused")
    private byte placeBlockId = 1;

    public BlockInteractor(World world, PerspectiveCamera camera, PlayerCameraController controller) {
        this.world = world;
        this.camera = camera;
        this.controller = controller;

        this.placementPolicy = new PlacementPolicy(camera, controller);
        this.actions = new BlockActions(placementPolicy, this::pickSlopeByFacing);
    }

    public void update() {
        if (world == null) return;

        float dt = Gdx.graphics.getDeltaTime();
        cooldowns.tick(dt);

        dir.set(camera.direction);

        aimSystem.update(world, camera.position, dir, reach);

        boolean leftDown = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean rightDown = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        boolean middleJustPressed = Gdx.input.isButtonJustPressed(Input.Buttons.MIDDLE);

        if (cooldowns.allowBreak(leftDown)) {
            actions.tryBreak(world, aimSystem);
        }

        if (cooldowns.allowPlace(rightDown)) {
            actions.tryPlace(world, aimSystem, camera.direction, selectedBlock, slopeMode);
        }

        // Pick
        if (middleJustPressed) {
            tryPickBlock();
        }
    }

    private byte pickSlopeByFacing(Vector3 camDir, byte slopeXP) {
        float dx = camDir.x;
        float dz = camDir.z;

        if (Math.abs(dx) < 1e-5f && Math.abs(dz) < 1e-5f) {
            return world.idByName("slope_xp", (byte) 0);
        }

        // Make sure slopeXP is a slope_xp
        String blockName = world.getBlockName(slopeXP);
        byte slopeZP = world.idByName(blockName.replace("xp", "zp"), slopeXP);
        byte slopeXN = world.idByName(blockName.replace("xp", "xn"), slopeXP);
        byte slopeZN = world.idByName(blockName.replace("xp", "zn"), slopeXP);

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0f ? slopeXN : slopeXP;
        } else {
            return dz >= 0f ? slopeZP : slopeZN;
        }
    }

    public boolean hasAim() { return aimSystem.state().hasAim(); }

    public String getAimBlockName() { return aimSystem.state().getAimBlockName(); }

    public byte getAimBlockId() { return aimSystem.state().getAimBlockId(); }

    public int getAimBlockX() { return aimSystem.state().getAimX(); }
    public int getAimBlockY() { return aimSystem.state().getAimY(); }
    public int getAimBlockZ() { return aimSystem.state().getAimZ(); }

    public int getAimNormalX() { return aimSystem.state().getAimNx(); }
    public int getAimNormalY() { return aimSystem.state().getAimNy(); }
    public int getAimNormalZ() { return aimSystem.state().getAimNz(); }

    public String getAimBlockPosString() { return aimSystem.state().getAimPosString(); }

    public String getAimDebugString() { return aimSystem.state().getAimDebugString(); }

    public void setSelectedBlock(byte id) {
        selectedBlock = normalizePickedId(id);
    }

    private void tryPickBlock() {
        if (!aimSystem.hasValidHit()) return;

        int x = aimSystem.hit().x;
        int y = aimSystem.hit().y;
        int z = aimSystem.hit().z;

        byte id = world.getBlock(x, y, z);
        if (id == 0) return;

        if (world.getDefAt(x, y, z) != null && world.getDefAt(x, y, z).shape != null) {
            switch (world.getDefAt(x, y, z).shape) {
                case SLOPE_XP, SLOPE_XN, SLOPE_ZP, SLOPE_ZN -> {
                    slopeMode = true;
                    selectedBlock = normalizePickedId(id);
                    return;
                }
                default -> { /* fallthrough */ }
            }
        }

        slopeMode = false;
        selectedBlock = normalizePickedId(id);
    }

    private byte normalizePickedId(byte id) {
        String name = world.getBlockName(id);
        if (name == null) return id;

        String substring = name.substring(0, name.length() - 3);
        if (name.endsWith("_on")) {
            String offName = substring + "_off"; // remove "_on" add "_off"
            return world.idByName(offName, id);
        } else if (name.endsWith("_xn")) {
            String xpName = substring + "_xp"; // MAKE SURE ALL KINDS OF SLOPES ARE NORMALIZED
            return world.idByName(xpName, id);
        } else if (name.endsWith("_zn")) {
            String zpName = substring + "_xp";
            return world.idByName(zpName, id);
        } else if (name.endsWith("_zp")) {
            String znName = substring + "_xp";
            return world.idByName(znName, id);
        }

        return id;
    }
}
