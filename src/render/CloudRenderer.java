package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.atom.life.time.DayNightCycle;

/**
 * Procedural clouds on a large horizontal quad (follows camera).
 * - depth soft-intersection (soft particles) using scene depth texture.
 * - distance fog (linear), now adaptive to night (fogColor not black + denser at night).
 */
public class CloudRenderer implements Disposable {

    // ----- tunables -----
    public float cloudHeight = 300f;
    public float halfSize = 10000f;

    public boolean autoFitToFar = false;
    public float minHalfSize = 500f;
    public float farSafety = 0.90f;

    public float speed = 0.8f;
    public float scale = 0.0035f;
    public float coverage = 0.45f;
    public float softness = 0.12f;

    // soft intersection distance in linear depth units
    public float softDist = 22f;

    // distance fog
    public boolean fogEnabled = true;
    public float fogFarClamp = 0.90f; // fogFar <= cam.far * clamp

    private final Mesh quad;
    private final ShaderProgram shader;

    private int u_projView;
    private int u_centerXZ;
    private int u_time;
    private int u_scale;
    private int u_coverage;
    private int u_softness;
    private int u_lightDir;
    private int u_ambient;
    private int u_dirI;

    // soft intersection uniforms
    private int u_depthTex;
    private int u_invViewport;
    private int u_near;
    private int u_far;
    private int u_softDist;

    // fog uniforms
    private int u_fogEnabled;
    private int u_camPos;
    private int u_fogColor;
    private int u_fogNear;
    private int u_fogFar;

    private float timeSec = 0f;

    private final float[] verts = new float[4 * 3];
    private final short[] inds = new short[]{0, 1, 2, 0, 2, 3};

    private float lastBuiltHalfSize = Float.NaN;
    private float lastBuiltHeight = Float.NaN;

    // temp for adaptive fog
    private final Vector3 tmpSky = new Vector3();
    private final Vector3 tmpDayFog = new Vector3();
    private final Vector3 tmpNightFog = new Vector3(0.08f, 0.10f, 0.14f);
    private final Vector3 tmpFogColor = new Vector3();
    private float tmpFogNear = 600f;
    private float tmpFogFar = 900f;

    private static final String VERT_150 =
        "#version 150\n" +
            "in vec3 a_pos;\n" +
            "uniform mat4 u_projView;\n" +
            "uniform vec2 u_centerXZ;\n" +
            "out vec2 v_wxz;\n" +
            "out vec3 v_worldPos;\n" +
            "void main(){\n" +
            "  vec3 wp = a_pos;\n" +
            "  wp.xz += u_centerXZ;\n" +
            "  v_wxz = wp.xz;\n" +
            "  v_worldPos = wp;\n" +
            "  gl_Position = u_projView * vec4(wp, 1.0);\n" +
            "}\n";

    private static final String FRAG_150 =
        "#version 150\n" +
            "uniform float u_time;\n" +
            "uniform float u_scale;\n" +
            "uniform float u_coverage;\n" +
            "uniform float u_softness;\n" +
            "uniform vec3  u_lightDir;\n" +
            "uniform float u_ambient;\n" +
            "uniform float u_dirI;\n" +
            "\n" +
            "uniform sampler2D u_depthTex;\n" +
            "uniform vec2  u_invViewport;\n" +
            "uniform float u_near;\n" +
            "uniform float u_far;\n" +
            "uniform float u_softDist;\n" +
            "\n" +
            "uniform int   u_fogEnabled;\n" +
            "uniform vec3  u_camPos;\n" +
            "uniform vec3  u_fogColor;\n" +
            "uniform float u_fogNear;\n" +
            "uniform float u_fogFar;\n" +
            "\n" +
            "in vec2 v_wxz;\n" +
            "in vec3 v_worldPos;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float hash(vec2 p){\n" +
            "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);\n" +
            "}\n" +
            "float noise(vec2 p){\n" +
            "  vec2 i = floor(p);\n" +
            "  vec2 f = fract(p);\n" +
            "  float a = hash(i);\n" +
            "  float b = hash(i + vec2(1.0, 0.0));\n" +
            "  float c = hash(i + vec2(0.0, 1.0));\n" +
            "  float d = hash(i + vec2(1.0, 1.0));\n" +
            "  vec2 u = f * f * (3.0 - 2.0 * f);\n" +
            "  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n" +
            "}\n" +
            "float fbm(vec2 p){\n" +
            "  float v = 0.0;\n" +
            "  float a = 0.5;\n" +
            "  for(int i=0;i<5;i++){\n" +
            "    v += a * noise(p);\n" +
            "    p *= 2.0;\n" +
            "    a *= 0.5;\n" +
            "  }\n" +
            "  return v;\n" +
            "}\n" +
            "\n" +
            "float linearizeDepth(float d01){\n" +
            "  float z = d01 * 2.0 - 1.0;\n" +
            "  return (2.0 * u_near * u_far) / (u_far + u_near - z * (u_far - u_near));\n" +
            "}\n" +
            "\n" +
            "float fogFactorLinear(float dist, float nearD, float farD){\n" +
            "  float denom = max(farD - nearD, 0.0001);\n" +
            "  float f = clamp((farD - dist) / denom, 0.0, 1.0);\n" +
            "  return f * f * (3.0 - 2.0 * f);\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "  // ===== Noise clouds =====\n" +
            "  vec2 p = v_wxz * u_scale + vec2(u_time * 0.03, u_time * 0.018);\n" +
            "  float n = fbm(p);\n" +
            "  float d = smoothstep(u_coverage - u_softness, u_coverage + u_softness, n);\n" +
            "\n" +
            "  float e = 0.6;\n" +
            "  float nx = fbm(p + vec2(e, 0.0)) - fbm(p - vec2(e, 0.0));\n" +
            "  float nz = fbm(p + vec2(0.0, e)) - fbm(p - vec2(0.0, e));\n" +
            "  vec3 normal = normalize(vec3(-nx, 1.4, -nz));\n" +
            "\n" +
            "  float ndl = max(dot(normal, normalize(u_lightDir)), 0.0);\n" +
            "  float light = u_ambient + (1.0 - u_ambient) * ndl * u_dirI;\n" +
            "\n" +
            "  vec3 dayCol = vec3(1.0, 1.0, 1.0);\n" +
            "  vec3 nightCol = vec3(0.55, 0.60, 0.70);\n" +
            "  float dayness = clamp(u_dirI * 1.2 + 0.1, 0.0, 1.0);\n" +
            "  vec3 col = mix(nightCol, dayCol, dayness) * light;\n" +
            "\n" +
            "  float a = d * 0.75;\n" +
            "\n" +
            "  // ===== Soft intersection with scene depth =====\n" +
            "  vec2 suv = gl_FragCoord.xy * u_invViewport;\n" +
            "  float sceneD01 = texture(u_depthTex, suv).r;\n" +
            "  float cloudD01 = gl_FragCoord.z;\n" +
            "  float sceneLin = linearizeDepth(sceneD01);\n" +
            "  float cloudLin = linearizeDepth(cloudD01);\n" +
            "  float diff = sceneLin - cloudLin;\n" +
            "  float fade = clamp(diff / max(u_softDist, 0.0001), 0.0, 1.0);\n" +
            "  fade = fade * fade * (3.0 - 2.0 * fade);\n" +
            "  a *= fade;\n" +
            "\n" +
            "  // ===== Distance fog =====\n" +
            "  if (u_fogEnabled != 0) {\n" +
            "    float dist = distance(v_worldPos, u_camPos);\n" +
            "    float fogF = fogFactorLinear(dist, u_fogNear, u_fogFar);\n" +
            "    col = mix(u_fogColor, col, fogF);\n" +
            "    // do NOT kill alpha too hard; keep clouds visible at night\n" +
            "    a *= mix(1.0, fogF, 0.35);\n" +
            "  }\n" +
            "\n" +
            "  if (a <= 0.001) discard;\n" +
            "  fragColor = vec4(col, a);\n" +
            "}\n";

    private static final String VERT_100 =
        "attribute vec3 a_pos;\n" +
            "uniform mat4 u_projView;\n" +
            "uniform vec2 u_centerXZ;\n" +
            "varying vec2 v_wxz;\n" +
            "varying vec3 v_worldPos;\n" +
            "void main(){\n" +
            "  vec3 wp = a_pos;\n" +
            "  wp.xz += u_centerXZ;\n" +
            "  v_wxz = wp.xz;\n" +
            "  v_worldPos = wp;\n" +
            "  gl_Position = u_projView * vec4(wp, 1.0);\n" +
            "}\n";

    private static final String FRAG_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform float u_time;\n" +
            "uniform float u_scale;\n" +
            "uniform float u_coverage;\n" +
            "uniform float u_softness;\n" +
            "uniform vec3  u_lightDir;\n" +
            "uniform float u_ambient;\n" +
            "uniform float u_dirI;\n" +
            "\n" +
            "uniform sampler2D u_depthTex;\n" +
            "uniform vec2  u_invViewport;\n" +
            "uniform float u_near;\n" +
            "uniform float u_far;\n" +
            "uniform float u_softDist;\n" +
            "\n" +
            "uniform int   u_fogEnabled;\n" +
            "uniform vec3  u_camPos;\n" +
            "uniform vec3  u_fogColor;\n" +
            "uniform float u_fogNear;\n" +
            "uniform float u_fogFar;\n" +
            "\n" +
            "varying vec2 v_wxz;\n" +
            "varying vec3 v_worldPos;\n" +
            "\n" +
            "float hash(vec2 p){\n" +
            "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);\n" +
            "}\n" +
            "float noise(vec2 p){\n" +
            "  vec2 i = floor(p);\n" +
            "  vec2 f = fract(p);\n" +
            "  float a = hash(i);\n" +
            "  float b = hash(i + vec2(1.0, 0.0));\n" +
            "  float c = hash(i + vec2(0.0, 1.0));\n" +
            "  float d = hash(i + vec2(1.0, 1.0));\n" +
            "  vec2 u = f * f * (3.0 - 2.0 * f);\n" +
            "  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n" +
            "}\n" +
            "float fbm(vec2 p){\n" +
            "  float v = 0.0;\n" +
            "  float a = 0.5;\n" +
            "  for(int i=0;i<5;i++){\n" +
            "    v += a * noise(p);\n" +
            "    p *= 2.0;\n" +
            "    a *= 0.5;\n" +
            "  }\n" +
            "  return v;\n" +
            "}\n" +
            "\n" +
            "float linearizeDepth(float d01){\n" +
            "  float z = d01 * 2.0 - 1.0;\n" +
            "  return (2.0 * u_near * u_far) / (u_far + u_near - z * (u_far - u_near));\n" +
            "}\n" +
            "\n" +
            "float fogFactorLinear(float dist, float nearD, float farD){\n" +
            "  float denom = max(farD - nearD, 0.0001);\n" +
            "  float f = clamp((farD - dist) / denom, 0.0, 1.0);\n" +
            "  return f * f * (3.0 - 2.0 * f);\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "  vec2 p = v_wxz * u_scale + vec2(u_time * 0.03, u_time * 0.018);\n" +
            "  float n = fbm(p);\n" +
            "  float d = smoothstep(u_coverage - u_softness, u_coverage + u_softness, n);\n" +
            "\n" +
            "  float e = 0.6;\n" +
            "  float nx = fbm(p + vec2(e, 0.0)) - fbm(p - vec2(e, 0.0));\n" +
            "  float nz = fbm(p + vec2(0.0, e)) - fbm(p - vec2(0.0, e));\n" +
            "  vec3 normal = normalize(vec3(-nx, 1.4, -nz));\n" +
            "\n" +
            "  float ndl = max(dot(normal, normalize(u_lightDir)), 0.0);\n" +
            "  float light = u_ambient + (1.0 - u_ambient) * ndl * u_dirI;\n" +
            "\n" +
            "  vec3 dayCol = vec3(1.0, 1.0, 1.0);\n" +
            "  vec3 nightCol = vec3(0.55, 0.60, 0.70);\n" +
            "  float dayness = clamp(u_dirI * 1.2 + 0.1, 0.0, 1.0);\n" +
            "  vec3 col = mix(nightCol, dayCol, dayness) * light;\n" +
            "\n" +
            "  float a = d * 0.75;\n" +
            "\n" +
            "  vec2 suv = gl_FragCoord.xy * u_invViewport;\n" +
            "  float sceneD01 = texture2D(u_depthTex, suv).r;\n" +
            "  float cloudD01 = gl_FragCoord.z;\n" +
            "  float sceneLin = linearizeDepth(sceneD01);\n" +
            "  float cloudLin = linearizeDepth(cloudD01);\n" +
            "  float diff = sceneLin - cloudLin;\n" +
            "  float fade = clamp(diff / max(u_softDist, 0.0001), 0.0, 1.0);\n" +
            "  fade = fade * fade * (3.0 - 2.0 * fade);\n" +
            "  a *= fade;\n" +
            "\n" +
            "  if (u_fogEnabled != 0) {\n" +
            "    float dist = distance(v_worldPos, u_camPos);\n" +
            "    float fogF = fogFactorLinear(dist, u_fogNear, u_fogFar);\n" +
            "    col = mix(u_fogColor, col, fogF);\n" +
            "    a *= mix(1.0, fogF, 0.35);\n" +
            "  }\n" +
            "\n" +
            "  if (a <= 0.001) discard;\n" +
            "  gl_FragColor = vec4(col, a);\n" +
            "}\n";

    public CloudRenderer() {
        ShaderProgram.pedantic = false;
        boolean gl30 = Gdx.graphics.isGL30Available();

        shader = new ShaderProgram(gl30 ? VERT_150 : VERT_100, gl30 ? FRAG_150 : FRAG_100);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Cloud shader compile error:\n" + shader.getLog());
        }

        u_projView = shader.getUniformLocation("u_projView");
        u_centerXZ = shader.getUniformLocation("u_centerXZ");
        u_time = shader.getUniformLocation("u_time");
        u_scale = shader.getUniformLocation("u_scale");
        u_coverage = shader.getUniformLocation("u_coverage");
        u_softness = shader.getUniformLocation("u_softness");
        u_lightDir = shader.getUniformLocation("u_lightDir");
        u_ambient = shader.getUniformLocation("u_ambient");
        u_dirI = shader.getUniformLocation("u_dirI");

        u_depthTex = shader.getUniformLocation("u_depthTex");
        u_invViewport = shader.getUniformLocation("u_invViewport");
        u_near = shader.getUniformLocation("u_near");
        u_far = shader.getUniformLocation("u_far");
        u_softDist = shader.getUniformLocation("u_softDist");

        u_fogEnabled = shader.getUniformLocation("u_fogEnabled");
        u_camPos = shader.getUniformLocation("u_camPos");
        u_fogColor = shader.getUniformLocation("u_fogColor");
        u_fogNear = shader.getUniformLocation("u_fogNear");
        u_fogFar = shader.getUniformLocation("u_fogFar");

        quad = new Mesh(true, 4, 6,
            new VertexAttributes(new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_pos"))
        );
        quad.setIndices(inds);

        rebuildQuadVertsIfNeeded();
    }

    private float computeSafeHalfSize(PerspectiveCamera cam) {
        float dy = Math.abs(cloudHeight - cam.position.y);
        float safeFar = cam.far * farSafety;

        float v = (safeFar * safeFar - dy * dy) * 0.5f;
        if (v <= 0f) return minHalfSize;

        float s = (float) Math.sqrt(v);
        if (Float.isNaN(s) || Float.isInfinite(s)) return minHalfSize;
        return Math.max(minHalfSize, s);
    }

    private void rebuildQuadVertsIfNeeded() {
        if (cloudHeight == lastBuiltHeight && halfSize == lastBuiltHalfSize) return;

        float s = halfSize;
        putV(0, -s, cloudHeight, -s);
        putV(1,  s, cloudHeight, -s);
        putV(2,  s, cloudHeight,  s);
        putV(3, -s, cloudHeight,  s);

        quad.setVertices(verts);

        lastBuiltHeight = cloudHeight;
        lastBuiltHalfSize = halfSize;
    }

    private void putV(int idx, float x, float y, float z) {
        int o = idx * 3;
        verts[o] = x;
        verts[o + 1] = y;
        verts[o + 2] = z;
    }

    /**
     * Compute adaptive fog for night: fogColor not black + denser at night.
     * Writes result into tmpFogColor/tmpFogNear/tmpFogFar.
     */
    private void computeAdaptiveFog(PerspectiveCamera cam, DayNightCycle cycle) {
        float sunI = (cycle != null) ? cycle.getSunIntensity() : 1f;
        float dayness = MathUtils.clamp(sunI * 1.15f, 0f, 1f);

        if (cycle != null) tmpSky.set(cycle.getSkyColor());
        else tmpSky.set(0.65f, 0.75f, 0.90f);

        tmpDayFog.set(tmpSky).scl(0.85f).add(0.15f, 0.15f, 0.15f);

        tmpFogColor.set(tmpNightFog).lerp(tmpDayFog, dayness);

        float minLum = 0.06f;
        tmpFogColor.x = Math.max(tmpFogColor.x, minLum);
        tmpFogColor.y = Math.max(tmpFogColor.y, minLum);
        tmpFogColor.z = Math.max(tmpFogColor.z, minLum);

        float maxFar = cam.far * fogFarClamp;

        // clouds usually can use farther fog range than blocks, but keep consistent:
        float dayNear = cam.far * 0.22f;
        float dayFar  = cam.far * 0.85f;
        float nightNear = cam.far * 0.10f;
        float nightFar  = cam.far * 0.35f;

        tmpFogNear = MathUtils.lerp(nightNear, dayNear, dayness);
        tmpFogFar  = MathUtils.lerp(nightFar,  dayFar,  dayness);

        tmpFogFar = Math.min(tmpFogFar, maxFar);
        tmpFogNear = Math.min(tmpFogNear, tmpFogFar - 1f);
    }

    /**
     * Render clouds with depth soft-intersection + distance fog.
     * IMPORTANT: depthTex must be the depth texture from the SAME FBO you rendered the scene into.
     */
    public void render(PerspectiveCamera cam, DayNightCycle cycle, float dt, Texture depthTex, int vpW, int vpH) {
        if (cam == null || cycle == null || depthTex == null) return;

        float desired = halfSize;
        if (autoFitToFar) {
            float safe = computeSafeHalfSize(cam);
            halfSize = Math.min(desired, safe);
        }

        timeSec += MathUtils.clamp(dt, 0f, 0.05f) * speed;
        rebuildQuadVertsIfNeeded();

        // states
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix(u_projView, cam.combined);
        shader.setUniformf(u_centerXZ, cam.position.x, cam.position.z);
        shader.setUniformf(u_time, timeSec);
        shader.setUniformf(u_scale, scale);
        shader.setUniformf(u_coverage, coverage);
        shader.setUniformf(u_softness, softness);

        // lighting
        float sunI = cycle.getSunIntensity();
        float moonI = cycle.getMoonIntensity();
        Vector3 lightDir = (sunI > 0.02f) ? cycle.getSunDir() : cycle.getMoonDir();
        float dirI = (sunI > 0.02f) ? sunI : (moonI * 0.35f);

        shader.setUniformf(u_lightDir, lightDir);
        shader.setUniformf(u_ambient, cycle.getAmbient());
        shader.setUniformf(u_dirI, dirI);

        // depth uniforms
        depthTex.bind(1);
        shader.setUniformi(u_depthTex, 1);
        shader.setUniformf(u_invViewport, 1f / Math.max(1, vpW), 1f / Math.max(1, vpH));
        shader.setUniformf(u_near, cam.near);
        shader.setUniformf(u_far, cam.far);
        shader.setUniformf(u_softDist, Math.max(0.001f, softDist));

        // fog uniforms (adaptive)
        int fogOn = fogEnabled ? 1 : 0;
        shader.setUniformi(u_fogEnabled, fogOn);
        if (fogOn != 0) {
            computeAdaptiveFog(cam, cycle);
            shader.setUniformf(u_camPos, cam.position);
            shader.setUniformf(u_fogColor, tmpFogColor);
            shader.setUniformf(u_fogNear, tmpFogNear);
            shader.setUniformf(u_fogFar, tmpFogFar);
        }

        quad.render(shader, GL20.GL_TRIANGLES, 0, 6);

        // restore
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        if (autoFitToFar) halfSize = desired;
    }

    /**
     * Backward compatible: no depth softening (still has adaptive fog if enabled).
     */
    public void render(PerspectiveCamera cam, DayNightCycle cycle, float dt) {
        if (cam == null || cycle == null) return;

        float desired = halfSize;
        if (autoFitToFar) {
            float safe = computeSafeHalfSize(cam);
            halfSize = Math.min(desired, safe);
        }

        timeSec += MathUtils.clamp(dt, 0f, 0.05f) * speed;
        rebuildQuadVertsIfNeeded();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        shader.bind();
        shader.setUniformMatrix(u_projView, cam.combined);
        shader.setUniformf(u_centerXZ, cam.position.x, cam.position.z);
        shader.setUniformf(u_time, timeSec);
        shader.setUniformf(u_scale, scale);
        shader.setUniformf(u_coverage, coverage);
        shader.setUniformf(u_softness, softness);

        float sunI = cycle.getSunIntensity();
        float moonI = cycle.getMoonIntensity();
        Vector3 lightDir = (sunI > 0.02f) ? cycle.getSunDir() : cycle.getMoonDir();
        float dirI = (sunI > 0.02f) ? sunI : (moonI * 0.35f);

        shader.setUniformf(u_lightDir, lightDir);
        shader.setUniformf(u_ambient, cycle.getAmbient());
        shader.setUniformf(u_dirI, dirI);

        int fogOn = fogEnabled ? 1 : 0;
        shader.setUniformi(u_fogEnabled, fogOn);
        if (fogOn != 0) {
            computeAdaptiveFog(cam, cycle);
            shader.setUniformf(u_camPos, cam.position);
            shader.setUniformf(u_fogColor, tmpFogColor);
            shader.setUniformf(u_fogNear, tmpFogNear);
            shader.setUniformf(u_fogFar, tmpFogFar);
        }

        quad.render(shader, GL20.GL_TRIANGLES, 0, 6);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        if (autoFitToFar) halfSize = desired;
    }

    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
    }
}
