package com.atom.life.input;

/**
 * Reusable integer scan ranges for collision checks (1.5).
 * Avoids recomputing floor/clamp repeatedly inside collides* loops.
 */
final class CollisionRanges {

    // Shared bounds used by different scans
    int y0, y1;
    int x0, x1;
    int z0, z1;

    void setY(int y0, int y1) { this.y0 = y0; this.y1 = y1; }
    void setX(int x0, int x1) { this.x0 = x0; this.x1 = x1; }
    void setZ(int z0, int z1) { this.z0 = z0; this.z1 = z1; }
}
