package com.atom.life.input;

import com.atom.life.world.World;

public final class AimState {

    private boolean hasAim = false;

    private int aimX, aimY, aimZ;
    private int aimNx, aimNy, aimNz;
    private byte aimBlockId = 0;

    // cache
    private String aimBlockName = "(none)";
    private byte lastNameId = (byte) 0x7F;

    private String aimPosString = "(none)";
    private int lastAimX = Integer.MIN_VALUE;
    private int lastAimY = Integer.MIN_VALUE;
    private int lastAimZ = Integer.MIN_VALUE;

    AimState() {}

    void clear() {
        hasAim = false;

        aimBlockId = 0;
        aimBlockName = "(none)";
        aimPosString = "(none)";

        lastNameId = (byte) 0x7F;
        lastAimX = Integer.MIN_VALUE;
        lastAimY = Integer.MIN_VALUE;
        lastAimZ = Integer.MIN_VALUE;
    }

    void setHit(int x, int y, int z, int nx, int ny, int nz, byte blockId, World world) {
        hasAim = true;

        aimX = x; aimY = y; aimZ = z;
        aimNx = nx; aimNy = ny; aimNz = nz;

        aimBlockId = blockId;

        if (blockId != lastNameId) {
            aimBlockName = world.getBlockName(blockId);
            lastNameId = blockId;
        }

        if (aimX != lastAimX || aimY != lastAimY || aimZ != lastAimZ) {
            aimPosString = "(" + aimX + ", " + aimY + ", " + aimZ + ")";
            lastAimX = aimX;
            lastAimY = aimY;
            lastAimZ = aimZ;
        }
    }

    public boolean hasAim() { return hasAim; }

    public String getAimBlockName() { return aimBlockName; }

    public byte getAimBlockId() { return aimBlockId; }

    public int getAimX() { return aimX; }
    public int getAimY() { return aimY; }
    public int getAimZ() { return aimZ; }

    public int getAimNx() { return aimNx; }
    public int getAimNy() { return aimNy; }
    public int getAimNz() { return aimNz; }

    public String getAimPosString() { return hasAim ? aimPosString : "(none)"; }

    public String getAimDebugString() {
        if (!hasAim) return "(none)";
        return aimBlockName + " " + aimPosString;
    }
}
