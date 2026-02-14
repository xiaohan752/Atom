package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.atom.life.time.DayNightCycle;

public class SkyBodyRenderer implements Disposable {

    // One quad mesh reused for sun/moon (4 vertices, 6 indices)
    private final Mesh quad;
    private final ShaderProgram shader;

    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();
    private final Vector3 center = new Vector3();

    private final float[] verts = new float[4 * 5]; // pos(3) + uv(2)
    private final short[] inds = new short[]{0,1,2, 0,2,3};

    private final Texture sunTex;
    private final Texture moonTex;

    // uniforms
    private int u_projView, u_tex, u_color;

    // GLSL 150
    private static final String VERT_150 =
        "#version 150\n" +
            "in vec3 a_pos;\n" +
            "in vec2 a_uv;\n" +
            "uniform mat4 u_projView;\n" +
            "out vec2 v_uv;\n" +
            "void main(){\n" +
            "  v_uv = a_uv;\n" +
            "  gl_Position = u_projView * vec4(a_pos, 1.0);\n" +
            "}\n";

    private static final String FRAG_150 =
        "#version 150\n" +
            "uniform sampler2D u_tex;\n" +
            "uniform vec4 u_color;\n" +
            "in vec2 v_uv;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  vec4 t = texture(u_tex, v_uv);\n" +
            "  fragColor = t * u_color;\n" +
            "}\n";

    // GLSL 100
    private static final String VERT_100 =
        "attribute vec3 a_pos;\n" +
            "attribute vec2 a_uv;\n" +
            "uniform mat4 u_projView;\n" +
            "varying vec2 v_uv;\n" +
            "void main(){\n" +
            "  v_uv = a_uv;\n" +
            "  gl_Position = u_projView * vec4(a_pos, 1.0);\n" +
            "}\n";

    private static final String FRAG_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform sampler2D u_tex;\n" +
            "uniform vec4 u_color;\n" +
            "varying vec2 v_uv;\n" +
            "void main(){\n" +
            "  vec4 t = texture2D(u_tex, v_uv);\n" +
            "  gl_FragColor = t * u_color;\n" +
            "}\n";

    public SkyBodyRenderer() {
        ShaderProgram.pedantic = false;
        boolean gl30 = Gdx.graphics.isGL30Available();
        shader = new ShaderProgram(gl30 ? VERT_150 : VERT_100, gl30 ? FRAG_150 : FRAG_100);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("SkyBody shader compile error:\n" + shader.getLog());
        }

        u_projView = shader.getUniformLocation("u_projView");
        u_tex = shader.getUniformLocation("u_tex");
        u_color = shader.getUniformLocation("u_color");

        quad = new Mesh(true, 4, 6,
            new VertexAttributes(
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_pos"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_uv")
            )
        );
        quad.setIndices(inds);

        // Create simple textures procedurally (no assets needed)
        sunTex = SkyTextures.makeSunTexture(128);
        moonTex = SkyTextures.makeMoonTexture(128);
    }

    /**
     * Render sun & moon as billboards around camera.
     * Call after world rendering (or before), but usually after clear.
     */
    public void render(PerspectiveCamera cam, DayNightCycle cycle) {
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        if (cam == null || cycle == null) return;

        // build orthonormal basis from camera direction + worldUp
        right.set(cam.direction).crs(Vector3.Y).nor();
        up.set(right).crs(cam.direction).nor();

        // put bodies at fixed distance from camera (so they always look far away)
        float dist = cam.far * 0.85f;

        // draw order: sun then moon
        // sizes in world units (tune)
        float sunSize = 100f;
        float moonSize = 80f;

        // sun visibility
        float sunA = cycle.getSunIntensity();
        // moon visibility (stronger at night). you can bias by (1 - sunIntensity)
        float moonA = Math.min(1f, cycle.getMoonIntensity() * (1f - cycle.getSunIntensity() + 0.25f));

        // Disable depth so they always appear in sky
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniformMatrix(u_projView, cam.combined);
        shader.setUniformi(u_tex, 0);

        if (sunA > 0.01f) {
            center.set(cam.position).mulAdd(cycle.getSunDir(), dist);
            drawBody(center, sunSize, sunTex, 1f, 1f, 1f, sunA);
        }

        if (moonA > 0.01f) {
            center.set(cam.position).mulAdd(cycle.getMoonDir(), dist);
            // slight blue tint at night looks nicer
            drawBody(center, moonSize, moonTex, 0.95f, 0.97f, 1.0f, moonA);
        }

        // restore states
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    private void drawBody(Vector3 c, float size, Texture tex, float r, float g, float b, float a) {
        float hs = size * 0.5f;

        // billboard corners
        // A(-right + up), B(+right + up), C(+right - up), D(-right - up)
        Vector3 rx = tmpV().set(right).scl(hs);
        Vector3 uy = tmpV2().set(up).scl(hs);

        // Vertex order must be CCW for default culling; we will not enable culling here anyway.
        // A
        putVertex(0, c.x - rx.x + uy.x, c.y - rx.y + uy.y, c.z - rx.z + uy.z, 0f, 1f);
        // B
        putVertex(1, c.x + rx.x + uy.x, c.y + rx.y + uy.y, c.z + rx.z + uy.z, 1f, 1f);
        // C
        putVertex(2, c.x + rx.x - uy.x, c.y + rx.y - uy.y, c.z + rx.z - uy.z, 1f, 0f);
        // D
        putVertex(3, c.x - rx.x - uy.x, c.y - rx.y - uy.y, c.z - rx.z - uy.z, 0f, 0f);

        quad.setVertices(verts);

        tex.bind(0);
        shader.setUniformf(u_color, r, g, b, a);
        quad.render(shader, GL20.GL_TRIANGLES, 0, 6);
    }

    private void putVertex(int idx, float x, float y, float z, float u, float v) {
        int o = idx * 5;
        verts[o] = x;
        verts[o + 1] = y;
        verts[o + 2] = z;
        verts[o + 3] = u;
        verts[o + 4] = v;
    }

    // small temp vectors to avoid allocations
    private static final Vector3 TMP1 = new Vector3();
    private static final Vector3 TMP2 = new Vector3();
    private static Vector3 tmpV() { return TMP1; }
    private static Vector3 tmpV2() { return TMP2; }

    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
        sunTex.dispose();
        moonTex.dispose();
    }
}
