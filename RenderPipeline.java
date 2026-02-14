package com.atom.life;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;

import com.atom.life.world.Chunk;

import static com.atom.life.GlobalVariables.*;

/**
 * Full render pipeline:
 * 1) Scene -> FBO (skybox, bodies, blocks opaque/alpha, clouds with depth, outline)
 * 2) Blit FBO -> screen
 * 3) PostFX: underwater overlay
 * 4) UI: crosshair, debug overlay, console
 * 5) LateFX: precipitation
 * 6) Restore GL state
 */
public final class RenderPipeline {
    public void render(GameSystems s) {
        float dt = Gdx.graphics.getDeltaTime();
        handleHotKeys(s, dt);

        updateGame(s, dt);
        computeSky(s);
        renderFrame(s, dt);

        updateTitle();
        restoreState();
    }
    public void handleHotKeys(GameSystems s, float dt) {
        // SLASH - toggle console
        if (Gdx.input.isKeyJustPressed(Input.Keys.SLASH)) {
            if (s.console.autoCloseLeft < 0f) {
                if (s.console.isOpen()) {
                    s.console.toggle();
                    Gdx.input.setCursorCatched(true);
                }
                if (!s.console.isOpen()) {
                    s.console.toggle();
                    Gdx.input.setCursorCatched(false);
                }
                if (s.debugOverlay.isEnabled()) s.debugOverlay.toggle();
            } else {
                s.console.autoCloseLeft = -1f;
            }
        }

        // ESC - release mouse
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (s.console.isOpen()) {
                s.console.toggle();
                Gdx.input.setCursorCatched(true);
                return;
            }
            if (s.debugOverlay.isEnabled()) s.debugOverlay.toggle();

            boolean catched = !Gdx.input.isCursorCatched();
            Gdx.input.setCursorCatched(catched);
        }

        // F3 toggle debug overlay
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            if (s.debugOverlay != null) s.debugOverlay.toggle();
            if (s.console.isOpen()) s.console.toggle();
        }
    }

    private void updateGame(GameSystems s, float dt) {
        s.controller.update(s.world, s.camera, dt, s.console);

        // Stream world + mesh uploads
        s.world.update(s.camera.position);
        s.world.pumpMeshUploads();

        // DayNight + weather
        s.dayNightCycle.updateFromLocalTime();

        if (s.weatherAuto != null) {
            s.weatherAuto.update(dt);
        }
        s.weather.update(dt);

        // Player interactions and outline target
        if (s.console == null || !s.console.isBlockingGameInput()) {
            s.interactor.update();
        }
        s.outlineRenderer.updateTarget(s.camera, s.world);
        //Tick circuits
        s.world.tickCircuits(dt);
    }

    private void computeSky(GameSystems s) {
        s.tmpSky.set(s.dayNightCycle.getSkyColor());
        if (s.weather != null) {
            s.weather.applySkyTint(s.tmpSky, s.tmpSky);
        }

        if (s.clouds != null && s.weather != null) {
            s.clouds.coverage = s.weather.getCloudCoverage();
        }

        // Set clear color
        Gdx.gl.glClearColor(s.tmpSky.x, s.tmpSky.y, s.tmpSky.z, 1f);
    }

    private void renderFrame(GameSystems s, float dt) {
        float fogK = s.weather.getFogDistanceFactor();

        renderSceneToFbo(s, dt, fogK);

        // Blit FBO color to screen
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        s.worldFbo.blitToScreen(); // fullscreen quad

        if (s.underwaterOverlay != null) {
            s.underwaterOverlay.render(s.world, s.camera, dt);
        }

        // UI
        renderUI(s, dt);

        // Late: precipitation must be last
        renderLate(s, dt);
    }

    private void renderSceneToFbo(GameSystems s, float dt, float fogK) {
        //   Render 3D scene into FBO (color + depth)
        s.worldFbo.begin();
        {
            Gdx.gl.glClearColor(s.tmpSky.x, s.tmpSky.y, s.tmpSky.z, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

            // Skybox first
            s.skybox.render(s.camera, s.dayNightCycle);

            // Sky bodies (sun/moon) BEFORE clouds so clouds can overlay them
            s.skyBodyRenderer.render(s.camera, s.dayNightCycle);

            // World blocks
            s.blockRenderer.begin(s.camera, s.atlas.getTexture());
            s.blockRenderer.setDayNight(s.dayNightCycle);
            s.blockRenderer.setFogFromDayNightAdaptive(s.camera, s.dayNightCycle, fogK);

            for (Chunk c : s.world.getRenderableChunks()) {
                s.tmp.set(c.cx * Chunk.SX, 0f, c.cz * Chunk.SZ);
                s.blockRenderer.setChunkTranslation(s.tmp);
                c.renderOpaque(s.blockRenderer.program());
            }
            s.blockRenderer.end();

            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            Gdx.gl.glDepthMask(false);

            s.blockRenderer.begin(s.camera, s.atlas.getTexture());
            s.blockRenderer.setDayNight(s.dayNightCycle);
            s.blockRenderer.setFogFromDayNightAdaptive(s.camera, s.dayNightCycle, fogK);

            for (Chunk c : s.world.getRenderableChunks()) {
                s.tmp.set(c.cx * Chunk.SX, 0f, c.cz * Chunk.SZ);
                s.blockRenderer.setChunkTranslation(s.tmp);
                c.renderAlpha(s.blockRenderer.program());
            }
            s.blockRenderer.end();

            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDisable(GL20.GL_BLEND);

            s.clouds.render(s.camera, s.dayNightCycle, dt,
                s.worldFbo.getDepthTexture(),
                s.worldFbo.getWidth(),
                s.worldFbo.getHeight()
            );

            // Outline in the same FBO so it can depth-test against blocks
            s.outlineRenderer.render(s.camera);
        }
        s.worldFbo.end();
    }

    private void renderUI(GameSystems s, float dt) {
        s.crosshair.render();
        String aimName = (s.interactor != null) ? s.interactor.getAimBlockName() : "(none)";
        String aimPos  = (s.interactor != null) ? s.interactor.getAimBlockPosString() : "(none)";
        s.debugOverlay.render(s.camera, s.world, s.controller, s.weather, s.dayNightCycle, aimName, aimPos);

        Gdx.gl.glDisable(GL20.GL_CULL_FACE);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        if (s.console != null) s.console.render();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
    }

    private void renderLate(GameSystems s, float dt) {
        if (s.precipitation != null && s.weather != null) {
            s.precipitation.update(s.camera, s.weather, dt);
            s.precipitation.render(s.camera, s.weather);
        }
    }

    private void updateTitle() {
        // Title
        if ((Gdx.graphics.getFrameId() & 31) == 0) {
            Gdx.graphics.setTitle(gameName);
        }
    }

    private void restoreState() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
