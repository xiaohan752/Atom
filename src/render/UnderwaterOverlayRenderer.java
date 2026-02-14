package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.atom.life.world.blocks.BlockDef;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.atom.life.world.World;

/**
 * Water overlay when camera is underwater (GL3 core-safe, no ShapeRenderer/SpriteBatch).
 */
public class UnderwaterOverlayRenderer implements Disposable {

    private Mesh quad;
    private ShaderProgram shader;
    private int u_color = -1;

    // smoothed alpha (avoid flicker around water surface)
    private float alpha = 0f;

    // tuning
    public float overlayAlpha = 0.35f;     // target alpha underwater (0.25~0.55)
    public float fadeSpeed = 10f;          // how fast alpha approaches target (bigger = faster)
    public float darken = 0.72f;           // underwater color multiplier (0.6~0.85)
    public float sampleYOffset = 0.05f;    // sample point slightly below camera.y

    // fallback water tint if block has no color
    public int fallbackRGBA = 0x2E6DFFFF;  // RRGGBBAA

    private static final String VSH_150 =
        "#version 150\n" +
            "in vec2 a_pos;\n" +
            "void main(){\n" +
            "  gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String FSH_150 =
        "#version 150\n" +
            "uniform vec4 u_color;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  fragColor = u_color;\n" +
            "}\n";

    private static final String VSH_100 =
        "attribute vec2 a_pos;\n" +
            "void main(){\n" +
            "  gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String FSH_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform vec4 u_color;\n" +
            "void main(){\n" +
            "  gl_FragColor = u_color;\n" +
            "}\n";

    public UnderwaterOverlayRenderer() {
        ensure();
    }

    private void ensure() {
        if (quad == null) {
            // fullscreen quad in clip space
            float[] v = new float[] {
                -1f, -1f,
                1f, -1f,
                1f,  1f,
                -1f,  1f
            };
            short[] i = new short[] {0,1,2, 0,2,3};

            quad = new Mesh(true, 4, 6,
                new VertexAttributes(
                    new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_pos")
                )
            );
            quad.setVertices(v);
            quad.setIndices(i);
        }

        if (shader == null) {
            ShaderProgram.pedantic = false;
            boolean gl30 = Gdx.graphics.isGL30Available();

            shader = new ShaderProgram(gl30 ? VSH_150 : VSH_100,
                gl30 ? FSH_150 : FSH_100);
            if (!shader.isCompiled()) {
                throw new IllegalStateException("UnderwaterOverlay shader compile error:\n" + shader.getLog());
            }
            u_color = shader.getUniformLocation("u_color");
        }
    }

    /**
     * Call after you've blitted the world FBO to screen (2D pass),
     * before UI is drawn.
     */
    public void render(World world, PerspectiveCamera cam, float dt) {
        if (world == null || cam == null) return;
        ensure();

        // Sample block at slightly below camera (prevents surface jitter)
        int wx = (int) Math.floor(cam.position.x);
        int wy = (int) Math.floor(cam.position.y - sampleYOffset);
        int wz = (int) Math.floor(cam.position.z);

        BlockDef def = world.getDefAt(wx, wy, wz);
        boolean underwater = (def != null && def.isFluid);

        float target = underwater ? overlayAlpha : 0f;
        float k = MathUtils.clamp(fadeSpeed * dt, 0f, 1f);
        alpha += (target - alpha) * k;

        if (alpha <= 0.001f) return;

        int rgba = (def != null && def.baseColorRGBA != 0) ? def.baseColorRGBA : fallbackRGBA;

        float r = ((rgba >> 24) & 0xFF) / 255f;
        float g = ((rgba >> 16) & 0xFF) / 255f;
        float b = ((rgba >> 8)  & 0xFF) / 255f;

        r *= darken;
        g *= darken;
        b *= darken;

        // draw overlay
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniformf(u_color, r, g, b, alpha);
        quad.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    @Override
    public void dispose() {
        if (quad != null) {
            quad.dispose();
            quad = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
    }
}
