package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.atom.life.weather.WeatherSystem;

/**
 * 3D world precipitation particles (rain/snow) rendered as billboards.
 *
 * - Spawns around camera in world space.
 * - Update position by velocity.
 * - Render as camera-facing quads (billboard).
 * - Depth test ON (so mountains occlude), depth write OFF.
 * - Blending ON.
 *
 * Updated:
 * - Uses WeatherSystem.getPrecipitationMode() (mutual exclusive).
 * - Uses WeatherSystem.getPrecipitationAlpha() for smooth transitions.
 * - Force respawn when mode switches (avoid rain/snow residual mix).
 */
public class PrecipitationRenderer implements Disposable {

    public float radius = 22f;
    public float heightMin = 6f;
    public float heightMax = 26f;
    public float killBelow = 6f;

    public int count = 900;

    public float rainSpeedMin = 22f;
    public float rainSpeedMax = 34f;
    public float rainLength = 0.55f;
    public float rainWidth  = 0.03f;
    public float rainAlpha  = 0.26f;

    public float snowSpeedMin = 2.0f;
    public float snowSpeedMax = 4.2f;
    public float snowSize = 0.12f;
    public float snowAlpha = 0.28f;

    public float windX = 1.8f;
    public float windZ = 0.8f;

    public boolean distanceFade = true;
    public float fadeNear = 10f;
    public float fadeFar  = 26f;

    // data
    private Vector3[] pos;
    private Vector3[] vel;
    private int capacity = 0;

    // rendering resources (need rebuild if count changes)
    private Mesh mesh;
    private ShaderProgram shader;
    private Texture whiteTex;

    // vertex format: pos(3) + uv(2) + colorPacked(1) = 6 floats
    private static final int STRIDE = 6;

    private float[] vtx;
    private short[] idx;
    private int meshCount = -1; // last built count

    // cached uniforms
    private int u_projView, u_tex;

    // temp
    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpUp = new Vector3();
    private final Vector3 tmpDir = new Vector3();
    private final Vector3 tmpR = new Vector3();
    private final Vector3 tmpU = new Vector3();

    private WeatherSystem.PrecipMode lastMode = WeatherSystem.PrecipMode.NONE;

    // GLSL 150
    private static final String VERT_150 =
            "#version 150\n" +
                    "in vec3 a_pos;\n" +
                    "in vec2 a_uv;\n" +
                    "in vec4 a_col;\n" +
                    "uniform mat4 u_projView;\n" +
                    "out vec2 v_uv;\n" +
                    "out vec4 v_col;\n" +
                    "void main(){\n" +
                    "  v_uv = a_uv;\n" +
                    "  v_col = a_col;\n" +
                    "  gl_Position = u_projView * vec4(a_pos, 1.0);\n" +
                    "}\n";

    private static final String FRAG_150 =
            "#version 150\n" +
                    "uniform sampler2D u_tex;\n" +
                    "in vec2 v_uv;\n" +
                    "in vec4 v_col;\n" +
                    "out vec4 fragColor;\n" +
                    "void main(){\n" +
                    "  vec4 t = texture(u_tex, v_uv);\n" +
                    "  fragColor = v_col * t;\n" +
                    "}\n";

    // GLSL 100
    private static final String VERT_100 =
            "attribute vec3 a_pos;\n" +
                    "attribute vec2 a_uv;\n" +
                    "attribute vec4 a_col;\n" +
                    "uniform mat4 u_projView;\n" +
                    "varying vec2 v_uv;\n" +
                    "varying vec4 v_col;\n" +
                    "void main(){\n" +
                    "  v_uv = a_uv;\n" +
                    "  v_col = a_col;\n" +
                    "  gl_Position = u_projView * vec4(a_pos, 1.0);\n" +
                    "}\n";

    private static final String FRAG_100 =
            "#ifdef GL_ES\n" +
                    "precision mediump float;\n" +
                    "#endif\n" +
                    "uniform sampler2D u_tex;\n" +
                    "varying vec2 v_uv;\n" +
                    "varying vec4 v_col;\n" +
                    "void main(){\n" +
                    "  vec4 t = texture2D(u_tex, v_uv);\n" +
                    "  gl_FragColor = v_col * t;\n" +
                    "}\n";

    public PrecipitationRenderer() {
        buildGraphics(); // build for initial count
        ensureCapacity(count);
        respawnAll(null, false); // will be properly respawned on first update when cam is known
    }

    private void buildGraphics() {
        // dispose old if any
        if (mesh != null) mesh.dispose();
        if (shader != null) shader.dispose();
        if (whiteTex != null) whiteTex.dispose();

        // 1x1 white texture
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fill();
        whiteTex = new Texture(pm);
        pm.dispose();

        ShaderProgram.pedantic = false;
        boolean gl30 = Gdx.graphics.isGL30Available();
        shader = new ShaderProgram(gl30 ? VERT_150 : VERT_100, gl30 ? FRAG_150 : FRAG_100);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Precipitation3D shader compile error:\n" + shader.getLog());
        }

        u_projView = shader.getUniformLocation("u_projView");
        u_tex = shader.getUniformLocation("u_tex");

        meshCount = count;

        // mesh: dynamic vertices, static indices
        int maxVerts = meshCount * 4;
        int maxIdx = meshCount * 6;

        mesh = new Mesh(true, maxVerts, maxIdx,
                new VertexAttributes(
                        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_pos"),
                        new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_uv"),
                        new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_col")
                )
        );

        rebuildIndices();
    }

    private void rebuildIndices() {
        int maxIdx = meshCount * 6;
        idx = new short[maxIdx];

        int vi = 0;
        int ii = 0;
        for (int i = 0; i < meshCount; i++) {
            idx[ii++] = (short) (vi);
            idx[ii++] = (short) (vi + 1);
            idx[ii++] = (short) (vi + 2);
            idx[ii++] = (short) (vi);
            idx[ii++] = (short) (vi + 2);
            idx[ii++] = (short) (vi + 3);
            vi += 4;
        }
        mesh.setIndices(idx);
    }

    private void ensureCapacity(int n) {
        if (n <= 0) n = 1;

        // if count changed, rebuild GPU resources
        if (meshCount != n) {
            count = n;
            buildGraphics();
        }

        if (n <= capacity && pos != null && vel != null) return;

        capacity = n;
        pos = new Vector3[capacity];
        vel = new Vector3[capacity];

        for (int i = 0; i < capacity; i++) {
            pos[i] = new Vector3();
            vel[i] = new Vector3();
        }

        vtx = new float[capacity * 4 * STRIDE];
    }

    private float randRadius() {
        return (float) Math.sqrt(MathUtils.random()) * radius;
    }

    private void respawn(int i, PerspectiveCamera cam, boolean snow) {
        if (cam == null) return;

        float ang = MathUtils.random(0f, MathUtils.PI2);
        float r = randRadius();

        float px = cam.position.x + MathUtils.cos(ang) * r;
        float pz = cam.position.z + MathUtils.sin(ang) * r;
        float py = cam.position.y + MathUtils.random(heightMin, heightMax);

        pos[i].set(px, py, pz);

        float sp = MathUtils.lerp(
                snow ? snowSpeedMin : rainSpeedMin,
                snow ? snowSpeedMax : rainSpeedMax,
                ((i * 97) % 100) / 100f
        );

        vel[i].set(windX, -sp, windZ);
        if (snow) {
            vel[i].x *= 0.35f;
            vel[i].z *= 0.35f;
        }
    }

    private void respawnAll(PerspectiveCamera cam, boolean snow) {
        if (cam == null) return;
        for (int i = 0; i < count; i++) {
            respawn(i, cam, snow);
        }
    }

    /** Update particle simulation in world space. */
    public void update(PerspectiveCamera cam, WeatherSystem weather, float dt) {
        if (cam == null || weather == null) return;

        ensureCapacity(count);

        WeatherSystem.PrecipMode mode = weather.getPrecipitationMode();
        float alphaW = weather.getPrecipitationAlpha();

        // nothing to simulate
        if (mode == WeatherSystem.PrecipMode.NONE || alphaW <= 0.0005f) {
            lastMode = mode;
            return;
        }

        boolean snow = (mode == WeatherSystem.PrecipMode.SNOW);

        if (mode != lastMode) {
            respawnAll(cam, snow);
            lastMode = mode;
        }

        float step = MathUtils.clamp(dt, 0f, 0.05f);

        float t = (Gdx.graphics.getFrameId() & 1023) * 0.015f;

        float r2Max = radius * radius * 1.25f;

        for (int i = 0; i < count; i++) {
            Vector3 p = pos[i];
            Vector3 v = vel[i];

            if (snow) {
                float wig = MathUtils.sin(t + i * 0.37f) * 0.6f;
                p.x += (v.x + wig) * step;
                p.z += (v.z + wig * 0.6f) * step;
                p.y += v.y * step;
            } else {
                p.mulAdd(v, step);
            }

            float dx = p.x - cam.position.x;
            float dz = p.z - cam.position.z;
            float dist2 = dx * dx + dz * dz;

            if (p.y < cam.position.y - killBelow || dist2 > r2Max) {
                respawn(i, cam, snow);
            }
        }
    }

    /** Render billboards in world space. Call after blocks (so depth works). */
    public void render(PerspectiveCamera cam, WeatherSystem weather) {
        if (cam == null || weather == null) return;

        WeatherSystem.PrecipMode mode = weather.getPrecipitationMode();
        float alphaW = weather.getPrecipitationAlpha();
        if (mode == WeatherSystem.PrecipMode.NONE || alphaW <= 0.0005f) return;

        boolean snow = (mode == WeatherSystem.PrecipMode.SNOW);

        // camera basis
        tmpRight.set(cam.direction).crs(cam.up).nor();
        tmpUp.set(tmpRight).crs(cam.direction).nor();

        int out = 0;

        float len = snow ? snowSize : rainLength;
        float wid = snow ? snowSize : rainWidth;

        float a0 = (snow ? snowAlpha : rainAlpha) * alphaW;

        // thunder: slightly more visible (only affects RAIN mode in your system)
        if ("THUNDER".equals(weather.getType().name()) && !snow) a0 *= 1.15f;

        for (int i = 0; i < count; i++) {
            Vector3 p = pos[i];
            Vector3 v = vel[i];

            float alpha = a0;

            if (distanceFade) {
                float d = p.dst(cam.position);
                float fogF = (fadeFar - d) / Math.max(0.0001f, (fadeFar - fadeNear));
                fogF = MathUtils.clamp(fogF, 0f, 1f);
                alpha *= fogF;
                if (alpha <= 0.001f) continue;
            }

            tmpDir.set(v).nor();

            tmpR.set(cam.direction).crs(tmpDir);
            if (tmpR.len2() < 1e-6f) tmpR.set(tmpRight);
            else tmpR.nor();

            tmpU.set(tmpDir).crs(tmpR).nor();

            Vector3 axisLong = tmpDir;
            Vector3 axisWide = tmpR;

            float hw = wid * 0.5f;

            float xTop = p.x, yTop = p.y, zTop = p.z;
            float xBot = p.x + axisLong.x * len;
            float yBot = p.y + axisLong.y * len;
            float zBot = p.z + axisLong.z * len;

            float rx = axisWide.x * hw;
            float ry = axisWide.y * hw;
            float rz = axisWide.z * hw;

            float rCol = snow ? 0.95f : 0.80f;
            float gCol = snow ? 0.98f : 0.85f;
            float bCol = snow ? 1.00f : 0.95f;
            float packed = com.badlogic.gdx.graphics.Color.toFloatBits(rCol, gCol, bCol, alpha);

            out = put(out, xTop - rx, yTop - ry, zTop - rz, 0f, 1f, packed);
            out = put(out, xTop + rx, yTop + ry, zTop + rz, 1f, 1f, packed);
            out = put(out, xBot + rx, yBot + ry, zBot + rz, 1f, 0f, packed);
            out = put(out, xBot - rx, yBot - ry, zBot - rz, 0f, 0f, packed);
        }

        int floatsUsed = out;
        int vertsUsed = floatsUsed / STRIDE;
        int quadsUsed = vertsUsed / 4;
        if (quadsUsed <= 0) return;

        mesh.setVertices(vtx, 0, floatsUsed);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix(u_projView, cam.combined);

        whiteTex.bind(0);
        shader.setUniformi(u_tex, 0);

        int idxCount = quadsUsed * 6;
        mesh.render(shader, GL20.GL_TRIANGLES, 0, idxCount);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private int put(int o, float x, float y, float z, float u, float v, float colPacked) {
        vtx[o++] = x;
        vtx[o++] = y;
        vtx[o++] = z;
        vtx[o++] = u;
        vtx[o++] = v;
        vtx[o++] = colPacked;
        return o;
    }

    @Override
    public void dispose() {
        if (mesh != null) mesh.dispose();
        if (shader != null) shader.dispose();
        if (whiteTex != null) whiteTex.dispose();
    }
}
