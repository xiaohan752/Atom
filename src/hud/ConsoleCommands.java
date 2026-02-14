package com.atom.life.hud;

import com.atom.life.weather.WeatherType;

import java.util.Collections;
import java.util.Set;

/**
 * Concrete commands implementation.
 */
public final class ConsoleCommands {

    private ConsoleCommands() {}

    // ----------------------------------------
    // pickblock (keep existing semantics)
    // ----------------------------------------
    public static void pickBlock(ConsoleCommandContext ctx, String name) {
        if (ctx == null || ctx.registry == null || ctx.interactor == null) return;

        byte id = minIdOrZero(ctx.registry.idByExpr(name + "*", (byte) 0));
        if (id == 0) id = ctx.registry.idByName(name, (byte) 0);

        String blockFullName = normalizeBlockName(ctx.registry.nameOf(id));

        ctx.interactor.slopeMode = blockFullName.contains("slope");
        if (id != 0) {
            ctx.interactor.setSelectedBlock(id);
            ctx.println("pickblock: Picked block: " + blockFullName);
        } else {
            ctx.println("pickblock: Unknown block: " + name);
        }
    }

    // weather (keep existing semantics)
    public static void weather(ConsoleCommandContext ctx, String name) {
        if (ctx == null || ctx.weatherSystem == null) return;

        switch (name) {
            case "clear" -> ctx.weatherSystem.startTransitionTo(WeatherType.CLEAR);
            case "overcast" -> ctx.weatherSystem.startTransitionTo(WeatherType.OVERCAST);
            case "rain" -> ctx.weatherSystem.startTransitionTo(WeatherType.RAIN);
            case "snow" -> ctx.weatherSystem.startTransitionTo(WeatherType.SNOW);
            case "thunder" -> ctx.weatherSystem.startTransitionTo(WeatherType.THUNDER);
            default -> {
                ctx.println("weather: Unknown weather: " + name);
                return;
            }
        }

        ctx.println("weather: Set weather to: " + name);
    }

    /**
     * Usage:
     *  - setblock <x> <y> <z> <blockExpr>
     *  - setblock aim <blockExpr>
     *
     * blockExpr supports exact name or glob (via BlockRegistry.idByExpr => Set<Byte>).
     * If matched multiple ids: picks the smallest id (stable) and prints a hint.
     */
    public static void setBlock(ConsoleCommandContext ctx, String[] parts) {
        if (ctx == null) return;
        if (ctx.world == null) {
            ctx.println("setblock: World is null.");
            return;
        }
        if (ctx.registry == null) {
            ctx.println("setblock: Registry is null.");
            return;
        }

        if (parts.length < 3) {
            printSetBlockUsage(ctx);
            return;
        }

        final int x, y, z;
        final int exprStart;

        // parts[0] == "setblock"
        String p1 = parts[1];

        if ("aim".equalsIgnoreCase(p1)) {
            if (ctx.interactor == null) {
                ctx.println("setblock: Interactor is null.");
                return;
            }
            if (!ctx.interactor.hasAim()) {
                ctx.println("setblock: No aim block.");
                return;
            }
            x = ctx.interactor.getAimBlockX();
            y = ctx.interactor.getAimBlockY();
            z = ctx.interactor.getAimBlockZ();
            exprStart = 2;
        } else {
            if (parts.length < 5) {
                printSetBlockUsage(ctx);
                return;
            }
            Integer px = parseIntSafe(parts[1]);
            Integer py = parseIntSafe(parts[2]);
            Integer pz = parseIntSafe(parts[3]);
            if (px == null || py == null || pz == null) {
                ctx.println("setblock: Invalid coordinates.");
                return;
            }
            x = px;
            y = py;
            z = pz;
            exprStart = 4;
        }

        String expr = joinTail(parts, exprStart);
        if (expr.isEmpty()) {
            ctx.println("setblock: Missing blockExpr.");
            return;
        }

        // fallback to air id=0
        byte fallback = 0;

        byte id = ctx.world.idByName(expr, fallback);
        if (id == 0) {
            ctx.println("setblock: No match for '" + expr + "'");
            return;
        }

        boolean ok = ctx.world.setBlock(x, y, z, id);
        if (!ok) {
            ctx.println("setblock: Failed (Chunk not loaded / Y out of range / Same block).");
            return;
        }

        ctx.println("setblock: OK @ (" + x + "," + y + "," + z + ") -> " + ctx.world.getBlockName(id));
    }

    private static void printSetBlockUsage(ConsoleCommandContext ctx) {
        ctx.println("Usage:");
        ctx.println("  setblock <x> <y> <z> <blockExpr>");
        ctx.println("  setblock aim <blockExpr>");
    }

    public static void weatherAuto(ConsoleCommandContext ctx, String arg) {
        if (ctx == null || ctx.weatherAutoSync == null) {
            if (ctx != null) ctx.println("weatherauto: Not available.");
            return;
        }

        if (arg == null) arg = "";
        String a = arg.trim().toLowerCase();

        switch (a) {
            case "on" -> {
                ctx.weatherAutoSync.setEnabled(true);
                ctx.println("weatherauto: ON");
            }
            case "off" -> {
                ctx.weatherAutoSync.setEnabled(false);
                ctx.println("weatherauto: OFF");
            }
            case "status", "" -> ctx.println(ctx.weatherAutoSync.statusString());
            case "now" -> {
                if (!ctx.weatherAutoSync.isEnabled()) {
                    ctx.println("weatherauto: is OFF. Use 'weatherauto on' first.");
                } else {
                    ctx.weatherAutoSync.requestNow();
                    ctx.println("weatherauto: Requested sync now.");
                }
            }
            default -> {
                ctx.println("Usage: weatherauto on|off|status|now");
            }
        }
    }

    private static Integer parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return null; }
    }

    private static String joinTail(String[] parts, int start) {
        if (start >= parts.length) return "";
        if (start == parts.length - 1) return parts[start];

        StringBuilder sb = new StringBuilder(64);
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString().trim();
    }

    /** stable choose min id from Set<Byte> */
    private static byte minIdOrZero(Set<Byte> ids) {
        if (ids == null || ids.isEmpty()) return 0;

        int best = Integer.MAX_VALUE;
        for (Byte b : ids) {
            if (b == null) continue;
            int v = b & 0xFF;
            if (v < best) best = v;
        }
        return (best == Integer.MAX_VALUE) ? 0 : (byte) best;
    }

    // Keep your original behavior (strip suffixes)
    private static String normalizeBlockName(String name) {
        if (name == null) return "";
        if (name.endsWith("xp") ||
            name.endsWith("xn") ||
            name.endsWith("zn") ||
            name.endsWith("zp") ||
            name.endsWith("on")) {
            return name.substring(0, name.length() - 3);
        } else if (name.endsWith("off")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
}
