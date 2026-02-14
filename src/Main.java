package com.atom.life;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;

public class Main extends ApplicationAdapter {
    private GameSystems gameSystems;
    private RenderPipeline renderPipeline;

    @Override
    public void create() {
        Gdx.input.setCursorCatched(true);
        gameSystems = new GameSystems(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        renderPipeline = new RenderPipeline();
    }

    @Override
    public void resize(int width, int height) {
        if (gameSystems != null) gameSystems.resize(width, height);
    }

    @Override
    public void render() {
        renderPipeline.render(gameSystems);
    }

    @Override
    public void dispose() {
        gameSystems.dispose();
    }
}
