package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * Offscreen framebuffer with:
 * - color texture
 * - depth texture (sampleable) for soft particles (clouds)
 * Also includes a fullscreen blitter (Mesh + Shader) to avoid SpriteBatch shader issues in GL3 core.
 */
public class WorldFbo implements Disposable {

    private FrameBuffer fbo;
    private Texture colorTex;
    private Texture depthTex;

    private int width, height;

    // fullscreen quad
    private Mesh fsq;
    private ShaderProgram blitShader;
    private int u_tex;

    private static final String BLIT_VERT_150 =
        "#version 150\n" +
            "in vec2 a_pos;\n" +
            "in vec2 a_uv;\n" +
            "out vec2 v_uv;\n" +
            "void main(){\n" +
            "  v_uv = a_uv;\n" +
            "  gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String BLIT_FRAG_150 =
        "#version 150\n" +
            "uniform sampler2D u_tex;\n" +
            "in vec2 v_uv;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  fragColor = texture(u_tex, v_uv);\n" +
            "}\n";

    private static final String BLIT_VERT_100 =
        "attribute vec2 a_pos;\n" +
            "attribute vec2 a_uv;\n" +
            "varying vec2 v_uv;\n" +
            "void main(){\n" +
            "  v_uv = a_uv;\n" +
            "  gl_Position = vec4(a_pos, 0.0, 1.0);\n" +
            "}\n";

    private static final String BLIT_FRAG_100 =
        "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform sampler2D u_tex;\n" +
            "varying vec2 v_uv;\n" +
            "void main(){\n" +
            "  gl_FragColor = texture2D(u_tex, v_uv);\n" +
            "}\n";

    public void rebuild(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);

        // dispose old
        if (fbo != null) fbo.dispose();
        fbo = null;
        colorTex = null;
        depthTex = null;

        // Build FBO with depth texture attachment
        GLFrameBuffer.FrameBufferBuilder b = new GLFrameBuffer.FrameBufferBuilder(width, height);
        b.addBasicColorTextureAttachment(Pixmap.Format.RGBA8888);

        if (Gdx.graphics.isGL30Available()) {
            b.addDepthTextureAttachment(GL30.GL_DEPTH_COMPONENT24, GL20.GL_UNSIGNED_INT);
        } else {
            b.addDepthTextureAttachment(GL20.GL_DEPTH_COMPONENT16, GL20.GL_UNSIGNED_SHORT);
        }

        fbo = b.build();

        // Color texture
        colorTex = fbo.getColorBufferTexture();
        colorTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        colorTex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        // Depth texture: in texture attachments list (index 1, since we add color then depth)
        // In libGDX FrameBufferBuilder, attachments are stored in order of addition.
        depthTex = fbo.getTextureAttachments().get(1);
        depthTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        depthTex.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        ensureBlitter();
    }

    private void ensureBlitter() {
        if (fsq == null) {
            // Fullscreen quad: clip space coords + uv
            // We draw colorTex to screen; note: FBO texture is typically Y-flipped in libGDX.
            // Here we set UV to flip Y: bottom-left uses (0,1), top-left uses (0,0).
            float[] v = new float[] {
                // x, y,  u, v   （不翻Y）
                -1f, -1f,  0f, 0f,
                1f, -1f,  1f, 0f,
                1f,  1f,  1f, 1f,
                -1f,  1f,  0f, 1f
            };
            short[] i = new short[] {0,1,2, 0,2,3};

            fsq = new Mesh(true, 4, 6,
                new VertexAttributes(
                    new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_pos"),
                    new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_uv")
                )
            );
            fsq.setVertices(v);
            fsq.setIndices(i);
        }

        if (blitShader == null) {
            ShaderProgram.pedantic = false;
            boolean gl30 = Gdx.graphics.isGL30Available();
            blitShader = new ShaderProgram(gl30 ? BLIT_VERT_150 : BLIT_VERT_100,
                gl30 ? BLIT_FRAG_150 : BLIT_FRAG_100);
            if (!blitShader.isCompiled()) {
                throw new IllegalStateException("Blit shader compile error:\n" + blitShader.getLog());
            }
            u_tex = blitShader.getUniformLocation("u_tex");
        }
    }

    public void begin() {
        if (fbo != null) fbo.begin();
    }

    public void end() {
        if (fbo != null) fbo.end();
    }

    public void blitToScreen() {
        if (colorTex == null) return;

        // Draw fullscreen quad
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        blitShader.bind();
        colorTex.bind(0);
        blitShader.setUniformi(u_tex, 0);

        fsq.render(blitShader, GL20.GL_TRIANGLES);
    }

    public Texture getColorTexture() {
        return colorTex;
    }

    public Texture getDepthTexture() {
        return depthTex;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    @Override
    public void dispose() {
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (fsq != null) {
            fsq.dispose();
            fsq = null;
        }
        if (blitShader != null) {
            blitShader.dispose();
            blitShader = null;
        }
        colorTex = null;
        depthTex = null;
    }
}
