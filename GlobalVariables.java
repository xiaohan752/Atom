package com.atom.life;

public class GlobalVariables {
    public static final String gameName = "Atom";
    public static final String gameVersion = "Alpha_05022026";
    public static final boolean debug = false;

    // Main window
    public static int SCREEN_WIDTH = 854;
    public static int SCREEN_HEIGHT = 480;

    // World
    public static String worldName = "World";
    public static long seed = 0L;
    public static String worldMode = "normal";
    public static int renderDistance = 8;

    // Player
    public static float reach = 5f;

    // Weather
    public static boolean weatherAutoSyncEnabled = true;
    public static float weatherAutoSyncSeconds = 900f;

    // Terrain params (see TerrainConfig)

    // Console
    public static float maxProportion = 0.8f;
}
