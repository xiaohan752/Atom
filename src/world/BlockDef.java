package com.atom.life.world.blocks;

public class BlockDef {

    public enum Shape {
        AIR,
        CUBE,
        SLOPE_XP,
        SLOPE_XN,
        SLOPE_ZP,
        SLOPE_ZN;

        public static Shape fromString(String s) {
            if (s == null) return CUBE;
            switch (s.toLowerCase()) {
                case "air": return AIR;
                case "cube": return CUBE;
                case "slope_xp": return SLOPE_XP;
                case "slope_xn": return SLOPE_XN;
                case "slope_zp": return SLOPE_ZP;
                case "slope_zn": return SLOPE_ZN;
                default: return CUBE;
            }
        }
    }

    public enum RenderLayer {
        NONE,
        OPAQUE,
        ALPHA; // 预留：玻璃/树叶/水 等

        public static RenderLayer fromString(String s) {
            if (s == null) return OPAQUE;
            switch (s.toLowerCase()) {
                case "none": return NONE;
                case "opaque": return OPAQUE;
                case "alpha": return ALPHA;
                default: return OPAQUE;
            }
        }
    }

    public final byte id;
    public final String name;

    /** 是否完全遮挡（用于面剔除 / AO / 等） */
    public final boolean opaque;

    /** 是否实体（用于碰撞/放置规则） */
    public final boolean solid;

    /** 几何形状（cube / slope_* / air） */
    public final Shape shape;

    /** 渲染队列（方案A：opaque / alpha 分队列） */
    public final RenderLayer renderLayer;

    /** atlas tile index */
    public final int tileTop;
    public final int tileSide;
    public final int tileBottom;

    public final float emission;

    // ✅ NEW: procedural texture config
    public final int baseColorRGBA;
    public final boolean jitter;
    public final float jitterStrength;
    public final float lumaJitter;
    public boolean isFluid;
	public float fluidDrag;
	public float fluidBuoyancy;
	public float fluidGravityScale;
	public float fluidMoveScale;

    public BlockDef(byte id, String name, boolean opaque, boolean solid,
                    Shape shape, RenderLayer renderLayer,
                    int tileTop, int tileSide, int tileBottom, float emission,
                    int baseColorRGBA, boolean jitter, float jitterStrength, float lumaJitter, boolean isFluid, float fluidDrag, float fluidBuoyancy, float fluidGravityScale, float fluidMoveScale) {
        this.id = id;
        this.name = (name == null) ? ("id_" + (id & 0xFF)) : name;
        this.opaque = opaque;
        this.solid = solid;
        this.shape = (shape == null) ? Shape.CUBE : shape;
        this.renderLayer = (renderLayer == null) ? RenderLayer.OPAQUE : renderLayer;
        this.tileTop = tileTop;
        this.tileSide = tileSide;
        this.tileBottom = tileBottom;
        this.emission = emission;

        this.baseColorRGBA = baseColorRGBA;
        this.jitter = jitter;
        this.jitterStrength = jitterStrength;
        this.lumaJitter = lumaJitter;

        this.isFluid = isFluid;
        this.fluidDrag = fluidDrag;
        this.fluidBuoyancy = fluidBuoyancy;
        this.fluidGravityScale = fluidGravityScale;
        this.fluidMoveScale = fluidMoveScale;
    }
}
