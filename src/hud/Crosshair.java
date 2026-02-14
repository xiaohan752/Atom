package com.atom.life.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class Crosshair {

    private final OrthographicCamera uiCam = new OrthographicCamera();

    private final ShaderProgram spriteShader;
    private final SpriteBatch batch;

    private final Texture pixel;

    private boolean enabled = true;

    public int sizePx = 3;
    public Color color = new Color(1, 1, 1, 1);

    // GLSL150 for GL3 core
    private static final String VERT_150 =
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

    private static final String FRAG_150 =
        "#version 150\n" +
            "uniform sampler2D u_texture;\n" +
            "in vec4 v_color;\n" +
            "in vec2 v_texCoords;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  fragColor = v_color * texture(u_texture, v_texCoords);\n" +
            "}\n";

    public Crosshair() {
        ShaderProgram.pedantic = false;
        spriteShader = new ShaderProgram(VERT_150, FRAG_150);
        if (!spriteShader.isCompiled()) {
            throw new IllegalStateException("Crosshair shader compile error:\n" + spriteShader.getLog());
        }
        batch = new SpriteBatch(10, spriteShader);

        // 1x1 white pixel texture
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();

        pixel.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggle() {
        enabled = !enabled;
    }

    public void resize(int w, int h) {
        uiCam.setToOrtho(false, w, h);
        uiCam.update();
    }

    public void render() {
        if (!enabled) return;

        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        batch.setColor(color);

        float cx = uiCam.viewportWidth * 0.5f;
        float cy = uiCam.viewportHeight * 0.5f;

        float s = Math.max(1, sizePx);
//        batch.draw(pixel, cx - s * 0.5f, cy - s * 0.5f, s, s);
        float thickness = 2f;
        float gap = 3f;
        float arm = 7f;

        batch.draw(pixel,
            cx - gap - arm, cy - thickness * 0.5f,
            arm, thickness);

        batch.draw(pixel,
            cx + gap, cy - thickness * 0.5f,
            arm, thickness);

        batch.draw(pixel,
            cx - thickness * 0.5f, cy + gap,
            thickness, arm);

        batch.draw(pixel,
            cx - thickness * 0.5f, cy - gap - arm,
            thickness, arm);

        batch.end();

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    public void dispose() {
        batch.dispose();
        spriteShader.dispose();
        pixel.dispose();
    }
}
