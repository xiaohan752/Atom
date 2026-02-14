package com.atom.life;

import com.atom.life.data.GameIO;
import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.hud.ConsoleOverlay;
import com.atom.life.hud.Crosshair;
import com.atom.life.hud.DebugOverlay;
import com.atom.life.input.BlockInteractor;
import com.atom.life.input.PlayerCameraController;
import com.atom.life.data.PlayerIO;
import com.atom.life.data.PlayerState;
import com.atom.life.render.*;
import com.atom.life.time.DayNightCycle;
import com.atom.life.weather.WeatherSystem;
import com.atom.life.weather.WeatherAutoSync;
import com.atom.life.world.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import static com.atom.life.GlobalVariables.*;

public final class GameSystems implements Disposable {
    public PerspectiveCamera camera;
    public PlayerCameraController controller;
    public BlockRegistry registry;
    public BlockAtlas atlas;
    public World world;

    public DayNightCycle dayNightCycle;
    public WeatherSystem weather;
    public WeatherAutoSync weatherAuto;

    public WorldFbo worldFbo;

    // Renderers
    public BlockRenderer blockRenderer;
    public BlockOutlineRenderer outlineRenderer;
    public SkyBodyRenderer skyBodyRenderer;
    public SkyboxRenderer skybox;
    public CloudRenderer clouds;
    public PrecipitationRenderer precipitation;
    public UnderwaterOverlayRenderer underwaterOverlay;

    public Crosshair crosshair;
    public DebugOverlay debugOverlay;
    public ConsoleOverlay console;
    public BlockInteractor interactor;
    public InputMultiplexer multiplexer;

    public PlayerIO playerIO;

    public final Vector3 tmp = new Vector3();
    public final Vector3 tmpSky = new Vector3();

    public GameSystems(int width, int height) {
        // OpenGL states
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);

        GameIO cfg = GameIO.loadOrCreate();
        cfg.applyToGlobals();

        // Blocks + atlas + shader
        registry = new BlockRegistry();
        atlas = new BlockAtlas(registry);

        world = new World(registry, atlas);

        int groundY = world.getSurfaceY(0, 0);
        float spawnY = groundY + 2.5f;

        dayNightCycle = new DayNightCycle();
        weather = new WeatherSystem();
        weatherAuto = new WeatherAutoSync(weather);
        weatherAuto.setIntervalSeconds(GlobalVariables.weatherAutoSyncSeconds);

        blockRenderer = new BlockRenderer();
        outlineRenderer = new BlockOutlineRenderer();
        skyBodyRenderer = new SkyBodyRenderer();
        skybox = new SkyboxRenderer();
        clouds = new CloudRenderer();
        precipitation = new PrecipitationRenderer();
        underwaterOverlay = new UnderwaterOverlayRenderer();

        // FBO
        worldFbo = new WorldFbo();
        worldFbo.rebuild(width, height);

        // UI
        crosshair = new Crosshair();
        crosshair.sizePx = 5;

        // Debug overlay
        debugOverlay = new DebugOverlay();
        debugOverlay.setVersion(gameVersion);
        debugOverlay.resize(width, height);
        if (debug) {
            debugOverlay.setVersion(gameVersion + "_Debug");
            debugOverlay.toggle();
        }

        // Camera
        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 2000f;
        camera.position.set(0, spawnY, 0f);
        camera.lookAt(30f, 0f, 30f);
        camera.update(true);

        controller = new PlayerCameraController();
        controller.initFromCamera(camera);

        // Player interactions
        interactor = new BlockInteractor(world, camera, controller);

        console = new ConsoleOverlay(registry, interactor, weather, world, weatherAuto);
        console.resize(width, height);

        multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(console);
        Gdx.input.setInputProcessor(multiplexer);

        // Player save/load
        playerIO = new PlayerIO(world.getSaveDir());
        PlayerState loaded = playerIO.loadOrNull();
        if (loaded != null) {
            loaded.applyToController(controller);
            controller.applyToCamera(camera);
        } else {
            camera.position.set(5, 80, 5);
            camera.direction.set(1, 0, 0).nor();
            camera.update(true);
            if (debug) System.out.println("No player save. Spawn at default.");
        }
    }

    public void resize(int width, int height) {
        if (camera != null) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
            camera.update(true);
        }

        if (worldFbo != null) worldFbo.rebuild(width, height);

        if (crosshair != null) crosshair.resize(width, height);
        if (debugOverlay != null) debugOverlay.resize(width, height);
        if (console != null) console.resize(width, height);
    }

    @Override public void dispose() {
        if (worldFbo != null) worldFbo.dispose();

        if (precipitation != null) precipitation.dispose();
        if (skyBodyRenderer != null) skyBodyRenderer.dispose();
        if (skybox != null) skybox.dispose();
        if (clouds != null) clouds.dispose();
        if (underwaterOverlay != null) underwaterOverlay.dispose();
        if (blockRenderer != null) blockRenderer.dispose();

        if (world != null) world.dispose();
        if (atlas != null) atlas.dispose();

        if (playerIO != null && controller != null) {
            playerIO.save(PlayerState.fromController(controller));
        }

        // UI
        if (crosshair != null) crosshair.dispose();
        if (outlineRenderer != null) outlineRenderer.dispose();
        if (debugOverlay != null) debugOverlay.dispose();
        if (console != null) console.dispose();
    }
}
