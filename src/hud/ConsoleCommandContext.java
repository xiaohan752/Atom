package com.atom.life.hud;

import com.atom.life.weather.WeatherAutoSync;
import com.atom.life.world.World;
import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.input.BlockInteractor;
import com.atom.life.weather.WeatherSystem;

import java.util.function.Consumer;

/**
 * Command execution context:
 * - holds references required by commands
 * - provides output sink (println)
 */
public final class ConsoleCommandContext {

    public final BlockRegistry registry;
    public final BlockInteractor interactor;
    public final WeatherSystem weatherSystem;
    public final WeatherAutoSync weatherAutoSync;

    // ✅ NEW: world access for setblock
    public final World world;

    private final Consumer<String> out;

    public ConsoleCommandContext(BlockRegistry registry,
                                 BlockInteractor interactor,
                                 WeatherSystem weatherSystem,
                                 World world,
                                 WeatherAutoSync weatherAutoSync,
                                 Consumer<String> out) {
        this.registry = registry;
        this.interactor = interactor;
        this.weatherSystem = weatherSystem;
        this.world = world;
        this.weatherAutoSync = weatherAutoSync;
        this.out = out;
    }

    public void println(String s) {
        if (out != null) out.accept(s);
    }
}
