package com.atom.life.hud;

import com.atom.life.weather.WeatherAutoSync;
import com.atom.life.world.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.input.BlockInteractor;
import com.atom.life.weather.WeatherSystem;

import java.util.ArrayList;
import java.util.List;

import static com.atom.life.GlobalVariables.*;

public class ConsoleOverlay extends InputAdapter {

    private final BlockRegistry registry;

    private SpriteBatch batch;
    private final BitmapFont font = new BitmapFont(); // 你也可以换成你自己的 font
    private final GlyphLayout layout = new GlyphLayout();
    private final OrthographicCamera cam = new OrthographicCamera();

    private boolean open = false;

    private final StringBuilder input = new StringBuilder(64);
    private int caret = 0;

    private final List<String> log = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    private int width, height;

    private final BlockInteractor interactor;
    private final WeatherSystem weatherSystem;
    private final WeatherAutoSync weatherAutoSync;

    private final ConsoleCommandRouter router = new ConsoleCommandRouter();
    private final ConsoleCommandContext cmdCtx;

    public float autoCloseLeft = -1f;

    private SpriteBatch createSpriteBatchForCoreProfile() {
        boolean isGL3 = Gdx.graphics.isGL30Available();

        if (!isGL3) {
            return new SpriteBatch();
        }

        final String VERT =
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

        final String FRAG =
            "#version 150\n" +
                "uniform sampler2D u_texture;\n" +
                "in vec4 v_color;\n" +
                "in vec2 v_texCoords;\n" +
                "out vec4 fragColor;\n" +
                "void main(){\n" +
                "  fragColor = v_color * texture(u_texture, v_texCoords);\n" +
                "}\n";

        com.badlogic.gdx.graphics.glutils.ShaderProgram.pedantic = false;
        com.badlogic.gdx.graphics.glutils.ShaderProgram shader =
            new com.badlogic.gdx.graphics.glutils.ShaderProgram(VERT, FRAG);

        if (!shader.isCompiled()) {
            throw new IllegalArgumentException("ConsoleOverlay SpriteBatch shader compile failed:\n" + shader.getLog());
        }

        return new SpriteBatch(1000, shader);
    }

    public ConsoleOverlay(BlockRegistry registry,
                          BlockInteractor interactor,
                          WeatherSystem weatherSystem,
                          World world,
                          WeatherAutoSync weatherAutoSync) {
        this.registry = registry;
        this.batch = createSpriteBatchForCoreProfile();
        this.interactor = interactor;
        this.weatherSystem = weatherSystem;
        this.weatherAutoSync = weatherAutoSync;

        this.cmdCtx = new ConsoleCommandContext(
            registry,
            interactor,
            weatherSystem,
            world,
            weatherAutoSync,
            this::println);
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        setOpen(!open);
    }

    public void setOpen(boolean v) {
        open = v;
        if (open) {
            autoCloseLeft = -1f;
            historyIndex = history.size();
        } else {
            autoCloseLeft = -1f;
        }
    }

    public void resize(int w, int h) {
        this.width = w;
        this.height = h;
        cam.setToOrtho(false, w, h);
        cam.update();
    }

    public void dispose() {
        if (batch != null) batch.dispose();
        if (font != null) font.dispose();
    }

    public void render() {
        if (!open) return;

        if (cam.viewportWidth <= 1f || cam.viewportHeight <= 1f) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }

        if (autoCloseLeft >= 0f) {
            autoCloseLeft -= Gdx.graphics.getDeltaTime();
            if (autoCloseLeft <= 0f) {
                autoCloseLeft = -1f;
                setOpen(false);
                return;
            }
        }

        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();

        int padding = 8;
        int lineH = 18;

        int maxLines = MathUtils.clamp((int) (height * maxProportion) / lineH, 4, 40);
        int start = Math.max(0, log.size() - maxLines);

        int y = height - padding - lineH;
        for (int i = start; i < log.size(); i++) {
            font.draw(batch, log.get(i), padding, y);
            y -= lineH;
        }

        if (autoCloseLeft < 0f) {
            String prompt = "> " + input.toString();
            font.draw(batch, prompt, padding, padding + lineH);

            String beforeCaret = "> " + input.substring(0, MathUtils.clamp(caret, 0, input.length()));
            layout.setText(font, beforeCaret);
            float caretX = padding + layout.width;
            font.draw(batch, "|", caretX, padding + lineH);
        }

        batch.end();

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_CULL_FACE);
    }

    public void println(String s) {
        log.add(s);
        if (log.size() > 200) log.remove(0);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!open) return false;

        if (autoCloseLeft >= 0f) {
            return false;
        }

        if (keycode == Input.Keys.ENTER) {
            boolean submitted = submit();

            if (submitted) {
                autoCloseLeft = 5f;
                Gdx.input.setCursorCatched(true);
            }
            return true;
        }

        if (keycode == Input.Keys.BACKSPACE) {
            if (caret > 0 && input.length() > 0) {
                input.deleteCharAt(caret - 1);
                caret--;
            }
            return true;
        }

        if (keycode == Input.Keys.DEL) {
            if (caret < input.length()) {
                input.deleteCharAt(caret);
            }
            return true;
        }

        if (keycode == Input.Keys.LEFT) {
            caret = Math.max(0, caret - 1);
            return true;
        }

        if (keycode == Input.Keys.RIGHT) {
            caret = Math.min(input.length(), caret + 1);
            return true;
        }

        if (keycode == Input.Keys.UP) {
            if (!history.isEmpty()) {
                historyIndex = Math.max(0, historyIndex - 1);
                setInput(history.get(historyIndex));
            }
            return true;
        }

        if (keycode == Input.Keys.DOWN) {
            if (!history.isEmpty()) {
                historyIndex = Math.min(history.size(), historyIndex + 1);
                if (historyIndex >= history.size()) setInput("");
                else setInput(history.get(historyIndex));
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        if (!open) return false;

        if (autoCloseLeft >= 0f) return false;

        if (character == '\r' || character == '\n' || character == '\t') return true;

        if (character >= 32 && character != 127) {
            input.insert(caret, character);
            caret++;
            return true;
        }
        return false;
    }

    private void setInput(String s) {
        input.setLength(0);
        input.append(s);
        caret = input.length();
    }

    private boolean submit() {
        String line = input.toString().trim();
        if (line.isEmpty()) return false;

        println("> " + line);
        history.add(line);
        historyIndex = history.size();

        execute(line);

        setInput("");
        return true;
    }

    private void execute(String line) {
        router.execute(line, cmdCtx);
    }

    public boolean isBlockingGameInput() {
        // open 且没有进入倒计时输入透传 => 才阻塞游戏控制
        return open && autoCloseLeft < 0f;
    }
}
