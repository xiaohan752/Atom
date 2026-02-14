package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.atom.life.time.DayNightCycle;

/**
 * Procedural skybox cube (no texture).
 * - Draws a cube around camera using view matrix WITHOUT translation.
 * - Color/brightness comes from DayNightCycle.
 */
public class SkyboxRenderer implements Disposable {

    private final Mesh cube;
    private final ShaderProgram shader;

    private final Matrix4 pvNoTrans = new Matrix4();
    private final Matrix4 viewNoTrans = new Matrix4();

    private int u_projView;
    private int u_skyColor;
    private int u_brightness;

    // GLSL 150
    private static final String VERT_150 =
        "#version 150\n" +
            "in vec3 a_pos;\n" +
            "uniform mat4 u_projView;\n" +
            "out vec3 v_dir;\n" +
            "void main(){\n" +
            "  v_dir = a_pos;\n" +
            "  gl_Position = u_projView * vec4(a_pos, 1.0);\n" +
            "}\n";

    private static final String FRAG_150 =
        "#version 150\n" +
            "uniform vec3 u_skyColor;\n" +
            "uniform float u_brightness;\n" +
            "in vec3 v_dir;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  vec3 d = normalize(v_dir);\n" +
            "  float t = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);\n" +
            "  vec3 horizon = u_skyColor * 0.75;\n" +
            "  vec3 zenith  = u_skyColor * 1.10;\n" +
            "  vec3 col = mix(horizon, zenith, t) * u_brightness;\n" +
            "  fragColor = vec4(col, 1.0);\n" +
            "}\n";

    // GLSL 100
    private static final String VERT_100 =
        "attribute vec3 a_pos;\n" +
            "uniform mat4 u_projView;\n" +
            "varying vec3 v_dir;\n" +
            "void main(){\n" +
            "  v_dir = a_pos;\n" +
            "  gl_Position = u_projView * vec4(a_pos, 1.0);\n" +
            "}\n";

    private static final String FRAG_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform vec3 u_skyColor;\n" +
            "uniform float u_brightness;\n" +
            "varying vec3 v_dir;\n" +
            "void main(){\n" +
            "  vec3 d = normalize(v_dir);\n" +
            "  float t = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);\n" +
            "  vec3 horizon = u_skyColor * 0.75;\n" +
            "  vec3 zenith  = u_skyColor * 1.10;\n" +
            "  vec3 col = mix(horizon, zenith, t) * u_brightness;\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    public SkyboxRenderer() {
        ShaderProgram.pedantic = false;

        boolean gl30 = Gdx.graphics.isGL30Available();
        shader = new ShaderProgram(gl30 ? VERT_150 : VERT_100, gl30 ? FRAG_150 : FRAG_100);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Skybox shader compile error:\n" + shader.getLog());
        }

        u_projView   = shader.getUniformLocation("u_projView");
        u_skyColor   = shader.getUniformLocation("u_skyColor");
        u_brightness = shader.getUniformLocation("u_brightness");

        // Unit cube (inside-out not required because we'll disable cull face)
        // Big enough in clip space after projection * view(no translation).
        float s = 1f;
        float[] v = new float[]{
            -s, -s, -s,
            s, -s, -s,
            s,  s, -s,
            -s,  s, -s,

            -s, -s,  s,
            s, -s,  s,
            s,  s,  s,
            -s,  s,  s
        };

        short[] idx = new short[]{
            // back
            0,1,2, 0,2,3,
            // front
            4,6,5, 4,7,6,
            // left
            0,3,7, 0,7,4,
            // right
            1,5,6, 1,6,2,
            // bottom
            0,4,5, 0,5,1,
            // top
            3,2,6, 3,6,7
        };

        cube = new Mesh(true, 8, idx.length,
            new VertexAttributes(new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_pos"))
        );
        cube.setVertices(v);
        cube.setIndices(idx);
    }

    public void render(PerspectiveCamera cam, DayNightCycle cycle) {
        if (cam == null || cycle == null) return;

        // Build proj * view(without translation) so cube stays centered on camera
        viewNoTrans.set(cam.view);
        viewNoTrans.val[Matrix4.M03] = 0f;
        viewNoTrans.val[Matrix4.M13] = 0f;
        viewNoTrans.val[Matrix4.M23] = 0f;

        pvNoTrans.set(cam.projection).mul(viewNoTrans);

        // brightness mapping: night -> darker, day -> brighter
        float sunI = cycle.getSunIntensity();
        float brightness = 0.12f + 0.88f * sunI;

        Vector3 sky = cycle.getSkyColor();

        // Render state: draw behind everything
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        shader.bind();
        shader.setUniformMatrix(u_projView, pvNoTrans);
        shader.setUniformf(u_skyColor, sky.x, sky.y, sky.z);
        shader.setUniformf(u_brightness, brightness);

        cube.render(shader, GL20.GL_TRIANGLES);

        // restore (optional)
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    @Override
    public void dispose() {
        cube.dispose();
        shader.dispose();
    }
}
