package com.atom.life.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.atom.life.input.PlayerCameraController;
import com.atom.life.time.DayNightCycle;
import com.atom.life.weather.WeatherSystem;
import com.atom.life.world.Chunk;
import com.atom.life.world.World;

import static com.atom.life.GlobalVariables.seed;
import static com.atom.life.GlobalVariables.gameName;
import static com.atom.life.data.PlayerState.normalizeYaw;

public class DebugOverlay {

    private boolean enabled = false;

    private final OrthographicCamera uiCam = new OrthographicCamera();
    private final BitmapFont font = new BitmapFont();

    private final ShaderProgram spriteShader;
    private final SpriteBatch batch;

    private final Vector3 tmpDir = new Vector3();

    private String version = "0.1.0";

    //GLSL 150 for OpenGL 3.2 Core
    private static final String SPRITE_VERT_150 =
        "#version 150\n" +
            "in vec4 a_position;\n" +
            "in vec4 a_color;\n" +
            "in vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "out vec4 v_color;\n" +
            "out vec2 v_texCoords;\n" +
            "void main(){\n" +
            "  v_color = a_color;\n" +
            "  v_texCoords = a_texCoord0;\n" +
            "  gl_Position = u_projTrans * a_position;\n" +
            "}\n";

    private static final String SPRITE_FRAG_150 =
        "#version 150\n" +
            "uniform sampler2D u_texture;\n" +
            "in vec4 v_color;\n" +
            "in vec2 v_texCoords;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  fragColor = v_color * texture(u_texture, v_texCoords);\n" +
            "}\n";

    private static final String SPRITE_VERT_100 =
        "attribute vec4 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "void main(){\n" +
            "  v_color = a_color;\n" +
            "  v_texCoords = a_texCoord0;\n" +
            "  gl_Position = u_projTrans * a_position;\n" +
            "}\n";

    private static final String SPRITE_FRAG_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform sampler2D u_texture;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_texCoords;\n" +
            "void main(){\n" +
            "  gl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n" +
            "}\n";

    public DebugOverlay() {
        font.setColor(Color.WHITE);

        ShaderProgram.pedantic = false;

        boolean gl30 = Gdx.graphics.isGL30Available();
        String v = gl30 ? SPRITE_VERT_150 : SPRITE_VERT_100;
        String f = gl30 ? SPRITE_FRAG_150 : SPRITE_FRAG_100;

        spriteShader = new ShaderProgram(v, f);
        if (!spriteShader.isCompiled()) {
            throw new IllegalStateException("DebugOverlay Sprite shader compile error:\n" + spriteShader.getLog());
        }

        batch = new SpriteBatch(1000, spriteShader);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void resize(int width, int height) {
        uiCam.setToOrtho(false, width, height);
        uiCam.update();
    }

    public void render(PerspectiveCamera cam,
                       World world,
                       PlayerCameraController controller,
                       WeatherSystem weather,
                       DayNightCycle dayNightCycle,
                       String aimBlockName,
                       String aimBlockPos) {
        if (!enabled) return;

        if (uiCam.viewportWidth <= 1f || uiCam.viewportHeight <= 1f) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }

        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();

        float x = 10f;
        float y = uiCam.viewportHeight - 10f;
        float line = 18f;

        drawLine(x, y, "=== Debug (F3) ==="); y -= line;
        drawLine(x, y, gameName); y -= line;
        drawLine(x, y, "Version: " + version); y -= line;
        drawLine(x, y, "Seed: " + seed); y -= line;

        int fps = Gdx.graphics.getFramesPerSecond();
        float dtMs = Gdx.graphics.getDeltaTime() * 1000f;
        drawLine(x, y, "FPS: " + fps + " | dt: " + format(dtMs, 2) + " ms"); y -= line;

        Vector3 eye = cam.position;
        int eyeX = (int)Math.floor(eye.x);
        int eyeY = (int)Math.floor(eye.y);
        int eyeZ = (int)Math.floor(eye.z);

        drawLine(x, y, "EyePos: (" + eyeX + ", " + eyeY + ", " + eyeZ + ")"); y -= line;

        if (controller != null) {
            int fx = (int)Math.floor(controller.feetPos.x);
            int fy = Math.round(controller.feetPos.y) - 1;
            int fz = (int)Math.floor(controller.feetPos.z);
            drawLine(x, y, "FeetPos: (" + fx + ", " + fy + ", " + fz + ")"); y -= line;

            int wx = fx;
            int wz = fz;
            int cx = Math.floorDiv(wx, Chunk.SX);
            int cz = Math.floorDiv(wz, Chunk.SZ);
            int lx = wx - cx * Chunk.SX;
            int lz = wz - cz * Chunk.SZ;

            drawLine(x, y, "Chunk: (" + cx + ", " + cz + ") | Local: (" + lx + ", " + lz + ")"); y -= line;

            float yaw = normalizeYaw(controller.yawDeg);
            float pitch = MathUtils.clamp(controller.pitchDeg, -89f, 89f);
            drawLine(x, y, "Yaw: " + format(yaw, 1) + " deg | Pitch: " + format(pitch, 1) + " deg"); y -= line;
        } else {
            int cx = Math.floorDiv(eyeX, Chunk.SX);
            int cz = Math.floorDiv(eyeZ, Chunk.SZ);
            int lx = eyeX - cx * Chunk.SX;
            int lz = eyeZ - cz * Chunk.SZ;
            drawLine(x, y, "Chunk: (" + cx + ", " + cz + ") | Local: (" + lx + ", " + lz + ")"); y -= line;

            tmpDir.set(cam.direction).nor();
            float yaw = MathUtils.atan2(tmpDir.z, tmpDir.x) * MathUtils.radiansToDegrees;
            if (yaw < 0) yaw += 360f;
            float pitch = MathUtils.asin(MathUtils.clamp(tmpDir.y, -1f, 1f)) * MathUtils.radiansToDegrees;
            drawLine(x, y, "Yaw: " + format(yaw, 1) + " deg | Pitch: " + format(pitch, 1) + " deg"); y -= line;
        }

        tmpDir.set(cam.direction).nor();
        drawLine(x, y, "Dir: (" + format(tmpDir.x, 3) + ", " + format(tmpDir.y, 3) + ", " + format(tmpDir.z, 3) + ")"); y -= line;

        drawLine(x, y, "Camera: Near=" + format(cam.near, 2) + " Far=" + format(cam.far, 0)); y -= line;

        String aimName = (aimBlockName == null || aimBlockName.isEmpty()) ? "(none)" : aimBlockName;
        String aimPos = (aimBlockPos == null || aimBlockPos.isEmpty()) ? "(none)" : aimBlockPos;
        drawLine(x, y, "Aim: " + aimName + " @ " + aimPos); y -= line;

        // World block
        if (world != null) {
            int loaded = world.getLoadedChunkCount();
            int renderable = world.getRenderableChunks().size;
            int uploads = world.getPendingUploadCount();
            int meshQueueSize = world.getMeshQueueSize();

            int execQ = -1;
            try { execQ = world.getExecutorQueueSize(); } catch (Throwable ignored) {}

            drawLine(x, y, "--- World ---"); y -= line;
            drawLine(x, y, "Chunks loaded: " + loaded + " | Renderable: " + renderable); y -= line;
            if (execQ >= 0) drawLine(x, y, "Uploads: " + uploads + " | ExecQueue: " + execQ + " | MeshQueueSize: " + meshQueueSize);
            else drawLine(x, y, "Uploads: " + uploads + " | MeshQueueSize: " + meshQueueSize);
            y -= line;

//            dayNightCycle.updateFromLocalTime();
            drawLine(x, y, "LocalTime: " + dayNightCycle.getTimeString() + " | Ambient: " + dayNightCycle.getAmbient()); y -= line;

            if (weather != null) {
                String tgtStr = weather.getTargetType().name();
                float t01 = weather.getTransitionT();

                WeatherSystem.PrecipMode pm = weather.getPrecipitationMode();
                float pa = weather.getPrecipitationAlpha();
                float flash = weather.getLightningFlash();

                drawLine(x, y,
                    "Weather: " + tgtStr +
                        " | Trans=" + format(t01, 2)
                );
                y -= line;

                drawLine(x, y,
                    "Precip: " + pm.name() +
                        " | Alpha=" + format(pa, 2) +
                        " | Flash=" + format(flash, 2)
                );
                y -= line;
            } else {
                drawLine(x, y, "Weather: (null)"); y -= line;
            }
        }

        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long used = total - free;

        drawLine(x, y, "--- Memory ---"); y -= line;
        drawLine(x, y, "Used: " + toMB(used) + " MB | Total: " + toMB(total) + " MB | Max: " + toMB(max) + " MB"); y -= line;

        batch.end();

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    private void drawLine(float x, float y, String text) {
        font.draw(batch, text, x, y);
    }

    private static String format(float v, int decimals) {
        float scale = (float) Math.pow(10, decimals);
        float r = Math.round(v * scale) / scale;
        if (Math.abs(r) < 0.000001f) r = 0f;
        return String.valueOf(r);
    }

    private static long toMB(long bytes) {
        return bytes / (1024L * 1024L);
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
        spriteShader.dispose();
    }
}
