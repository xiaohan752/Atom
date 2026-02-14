package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.atom.life.time.DayNightCycle;
import com.atom.life.world.Chunk;

import static com.atom.life.GlobalVariables.renderDistance;

public class BlockRenderer {

    private final ShaderProgram shader;
    private final Matrix4 world = new Matrix4();

    private static final String VERT_150 =
        "#version 150\n" +
            "in vec3 a_position;\n" +
            "in vec3 a_normal;\n" +
            "in vec2 a_localUV;\n" +
            "in vec4 a_atlasRect;\n" +
            "in float a_emission;\n" +                 // ✅ NEW
            "\n" +
            "uniform mat4 u_projView;\n" +
            "uniform mat4 u_world;\n" +
            "\n" +
            "out vec3 v_n;\n" +
            "out vec2 v_localUV;\n" +
            "out vec4 v_atlasRect;\n" +
            "out vec3 v_worldPos;\n" +
            "out float v_emission;\n" +               // ✅ NEW
            "\n" +
            "void main(){\n" +
            "  vec4 wpos = u_world * vec4(a_position, 1.0);\n" +
            "  v_worldPos = wpos.xyz;\n" +
            "  v_n = mat3(u_world) * a_normal;\n" +
            "  v_localUV = a_localUV;\n" +
            "  v_atlasRect = a_atlasRect;\n" +
            "  v_emission = a_emission;\n" +          // ✅ NEW
            "  gl_Position = u_projView * wpos;\n" +
            "}\n";

    private static final String FRAG_150 =
        "#version 150\n" +
            "uniform sampler2D u_tex;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform float u_ambient;\n" +
            "uniform float u_dirI;\n" +
            "\n" +
            "uniform vec3 u_camPos;\n" +
            "uniform vec3 u_fogColor;\n" +
            "uniform float u_fogNear;\n" +
            "uniform float u_fogFar;\n" +
            "\n" +
            "in vec3 v_n;\n" +
            "in vec2 v_localUV;\n" +
            "in vec4 v_atlasRect;\n" +
            "in vec3 v_worldPos;\n" +
            "in float v_emission;\n" +
            "\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "float fogFactorLinear(float dist, float nearD, float farD){\n" +
            "  float denom = max(farD - nearD, 0.0001);\n" +
            "  float f = clamp((farD - dist) / denom, 0.0, 1.0);\n" +
            "  return f * f * (3.0 - 2.0 * f);\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "  vec3 n = normalize(v_n);\n" +
            "  float ndl = max(dot(n, normalize(u_lightDir)), 0.0);\n" +
            "  float light = u_ambient + (1.0 - u_ambient) * ndl * u_dirI;\n" +
            "\n" +
            "  vec2 fuv = fract(v_localUV);\n" +
            "  vec2 uv = mix(v_atlasRect.xy, v_atlasRect.zw, fuv);\n" +
            "  vec4 albedo = texture(u_tex, uv);\n" +
            "\n" +
            "  // emissive\n" +
            "  float e = clamp(v_emission, 0.0, 1.0);\n" +
            "  vec3 lit = albedo.rgb * light;\n" +
            "  vec3 emissive = albedo.rgb * e;\n" +
            "  vec3 shaded = lit + emissive;\n" +
            "  shaded = clamp(shaded, 0.0, 1.0);\n" +
            "\n" +
            "  float dist = distance(v_worldPos, u_camPos);\n" +
            "  float fogF = fogFactorLinear(dist, u_fogNear, u_fogFar);\n" +
            "  vec3 col = mix(u_fogColor, shaded, fogF);\n" +
            "\n" +
            "  fragColor = vec4(col, albedo.a);\n" +
            "}\n";

    // ===== GLSL 100 (GL20 / ES2 style) =====
    private static final String VERT_100 =
        "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec2 a_localUV;\n" +
            "attribute vec4 a_atlasRect;\n" +
            "attribute float a_emission;\n" +
            "\n" +
            "uniform mat4 u_projView;\n" +
            "uniform mat4 u_world;\n" +
            "\n" +
            "varying vec3 v_n;\n" +
            "varying vec2 v_localUV;\n" +
            "varying vec4 v_atlasRect;\n" +
            "varying vec3 v_worldPos;\n" +
            "varying float v_emission;\n" +
            "\n" +
            "void main(){\n" +
            "  vec4 wpos = u_world * vec4(a_position, 1.0);\n" +
            "  v_worldPos = wpos.xyz;\n" +
            "  v_n = mat3(u_world) * a_normal;\n" +
            "  v_localUV = a_localUV;\n" +
            "  v_atlasRect = a_atlasRect;\n" +
            "  v_emission = a_emission;\n" +
            "  gl_Position = u_projView * wpos;\n" +
            "}\n";

    private static final String FRAG_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "\n" +
            "uniform sampler2D u_tex;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform float u_ambient;\n" +
            "uniform float u_dirI;\n" +
            "\n" +
            "uniform vec3 u_camPos;\n" +
            "uniform vec3 u_fogColor;\n" +
            "uniform float u_fogNear;\n" +
            "uniform float u_fogFar;\n" +
            "\n" +
            "varying vec3 v_n;\n" +
            "varying vec2 v_localUV;\n" +
            "varying vec4 v_atlasRect;\n" +
            "varying vec3 v_worldPos;\n" +
            "varying float v_emission;\n" +
            "\n" +
            "float fogFactorLinear(float dist, float nearD, float farD){\n" +
            "  float denom = max(farD - nearD, 0.0001);\n" +
            "  float f = clamp((farD - dist) / denom, 0.0, 1.0);\n" +
            "  return f * f * (3.0 - 2.0 * f);\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "  vec3 n = normalize(v_n);\n" +
            "  float ndl = max(dot(n, normalize(u_lightDir)), 0.0);\n" +
            "  float light = u_ambient + (1.0 - u_ambient) * ndl * u_dirI;\n" +
            "\n" +
            "  vec2 fuv = fract(v_localUV);\n" +
            "  vec2 uv = mix(v_atlasRect.xy, v_atlasRect.zw, fuv);\n" +
            "  vec4 albedo = texture2D(u_tex, uv);\n" +
            "\n" +
            "  float e = clamp(v_emission, 0.0, 1.0);\n" +
            "  vec3 lit = albedo.rgb * light;\n" +
            "  vec3 emissive = albedo.rgb * e;\n" +
            "  vec3 shaded = clamp(lit + emissive, 0.0, 1.0);\n" +
            "\n" +
            "  float dist = distance(v_worldPos, u_camPos);\n" +
            "  float fogF = fogFactorLinear(dist, u_fogNear, u_fogFar);\n" +
            "  vec3 col = mix(u_fogColor, shaded, fogF);\n" +
            "\n" +
            "  gl_FragColor = vec4(col, albedo.a);\n" +
            "}\n";

    // cached uniforms
    private int u_projView, u_world, u_tex, u_lightDir, u_ambient, u_dirI;
    private int u_camPos, u_fogColor, u_fogNear, u_fogFar;

    // defaults
    private final Vector3 tmpLightDir = new Vector3(0.3f, 1.0f, 0.2f);
    private float tmpAmbient = 0.35f;
    private float tmpDirI = 1.0f;

    // fog defaults
    private final Vector3 tmpFogColor = new Vector3(0.65f, 0.75f, 0.90f);
    private float tmpFogNear = 80f;
    private float tmpFogFar = 350f;

    private final Vector3 tmpCamPos = new Vector3();

    // temp for adaptive fog
    private final Vector3 tmpSky = new Vector3();
    private final Vector3 tmpDayFog = new Vector3();
    private final Vector3 tmpNightFog = new Vector3(0.08f, 0.10f, 0.14f);

    public BlockRenderer() {
        ShaderProgram.pedantic = false;

        boolean gl30 = Gdx.graphics.isGL30Available();
        String vert = gl30 ? VERT_150 : VERT_100;
        String frag = gl30 ? FRAG_150 : FRAG_100;

        shader = new ShaderProgram(vert, frag);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Shader compile error:\n" + shader.getLog());
        }

        u_projView = shader.getUniformLocation("u_projView");
        u_world    = shader.getUniformLocation("u_world");
        u_tex      = shader.getUniformLocation("u_tex");
        u_lightDir = shader.getUniformLocation("u_lightDir");
        u_ambient  = shader.getUniformLocation("u_ambient");
        u_dirI     = shader.getUniformLocation("u_dirI");

        u_camPos   = shader.getUniformLocation("u_camPos");
        u_fogColor = shader.getUniformLocation("u_fogColor");
        u_fogNear  = shader.getUniformLocation("u_fogNear");
        u_fogFar   = shader.getUniformLocation("u_fogFar");
    }

    public void begin(PerspectiveCamera cam, Texture atlasTexture) {
        shader.bind();
        shader.setUniformMatrix(u_projView, cam.combined);

        // lighting defaults
        shader.setUniformf(u_lightDir, tmpLightDir);
        shader.setUniformf(u_ambient, tmpAmbient);
        shader.setUniformf(u_dirI, tmpDirI);

        // fog defaults
        tmpCamPos.set(cam.position);
        shader.setUniformf(u_camPos, tmpCamPos);
        shader.setUniformf(u_fogColor, tmpFogColor);
        shader.setUniformf(u_fogNear, tmpFogNear);
        shader.setUniformf(u_fogFar, tmpFogFar);

        atlasTexture.bind(0);
        shader.setUniformi(u_tex, 0);
    }

    /**
     * Call once per frame after begin().
     * Uses sun by day, moon by night; scales directional light intensity.
     */
    public void setDayNight(DayNightCycle cycle) {
        if (cycle == null) return;

        float sunI = cycle.getSunIntensity();
        float moonI = cycle.getMoonIntensity();

        if (sunI > 0.02f) {
            tmpLightDir.set(cycle.getSunDir());
            tmpDirI = sunI;
        } else {
            tmpLightDir.set(cycle.getMoonDir());
            tmpDirI = moonI * 0.35f; // tune 0.2~0.5
        }

        tmpAmbient = cycle.getAmbient();

        shader.setUniformf(u_lightDir, tmpLightDir);
        shader.setUniformf(u_ambient, tmpAmbient);
        shader.setUniformf(u_dirI, tmpDirI);
    }

    /**
     * ✅ Fog setter (manual)
     */
    private void setFog(Vector3 camPos, Vector3 fogColor, float fogNear, float fogFar) {
        if (camPos != null) tmpCamPos.set(camPos);
        if (fogColor != null) tmpFogColor.set(fogColor);
        tmpFogNear = fogNear;
        tmpFogFar = fogFar;

        shader.setUniformf(u_camPos, tmpCamPos);
        shader.setUniformf(u_fogColor, tmpFogColor);
        shader.setUniformf(u_fogNear, tmpFogNear);
        shader.setUniformf(u_fogFar, tmpFogFar);
    }

    public void setFogFromDayNightAdaptive(PerspectiveCamera cam, DayNightCycle cycle, float fogK) {
        if (cam == null) return;

        // dayness from sun intensity (0..1)
        float sunI = (cycle != null) ? cycle.getSunIntensity() : 1f;
        float dayness = MathUtils.clamp(sunI * 1.15f, 0f, 1f);

        // color: day fog from sky (slightly brighter), night fog = blue-grey (not black)
        if (cycle != null) tmpSky.set(cycle.getSkyColor());
        else tmpSky.set(tmpFogColor);

        tmpDayFog.set(tmpSky).scl(0.85f).add(0.15f, 0.15f, 0.15f);

        tmpFogColor.set(tmpNightFog).lerp(tmpDayFog, dayness);

        // minimum luminance lift (prevents pure black fog at night)
        float minLum = 0.06f;
        tmpFogColor.x = Math.max(tmpFogColor.x, minLum);
        tmpFogColor.y = Math.max(tmpFogColor.y, minLum);
        tmpFogColor.z = Math.max(tmpFogColor.z, minLum);

        // distance: use camera.far as reference, clamp inside far plane
        float vis = renderDistance * Chunk.SX;     // 你的可见距离（8区块）
        float base = vis * 1.8f;       // 稍微放大一点当作雾尺度

        float dayNear = base * 0.35f;
        float dayFar  = base;
        float nightNear = base * 0.15f;
        float nightFar  = base * 0.55f;

        float maxFar = cam.far * 0.90f;

        tmpFogNear = MathUtils.lerp(nightNear, dayNear, dayness);
        tmpFogFar  = MathUtils.lerp(nightFar,  dayFar,  dayness);

        // clamp + safety: far must be > near
        tmpFogFar = Math.min(tmpFogFar, maxFar);
        tmpFogNear = Math.min(tmpFogNear, tmpFogFar - 1f);

        if (fogK != 0 ) {
            tmpFogNear = tmpFogNear * fogK;
            tmpFogFar = tmpFogFar * fogK;
        }

        // apply
        setFog(cam.position, tmpFogColor, tmpFogNear, tmpFogFar);
    }

    public void setChunkTranslation(Vector3 chunkWorldOrigin) {
        world.idt().translate(chunkWorldOrigin);
        shader.setUniformMatrix(u_world, world);
    }

    public ShaderProgram program() {
        return shader;
    }

    public void end() { }

    public void dispose() {
        shader.dispose();
    }
}
