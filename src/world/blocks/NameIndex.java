package com.atom.life.world.blocks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * name -> id mapping with normalization and overwrite warning.
 */
final class NameIndex {

    private final Map<String, Byte> nameToId = new HashMap<>();

    byte findId(String name, byte fallback) {
        if (name == null) return fallback;
        String key = normalize(name);
        if (key.isEmpty()) return fallback;

        Byte v = nameToId.get(key);
        return v == null ? fallback : v;
    }

    void put(String name, byte id, boolean debug) {
        if (name == null) return;
        String key = normalize(name);
        if (key.isEmpty()) return;

        Byte prev = nameToId.put(key, id);

        // Requested #5: "same name overwrite risk" warning
        if (debug && prev != null && prev != id) {
            System.out.println("[BlockRegistry] name remap '" + key + "': " + (prev & 0xFF) + " -> " + (id & 0xFF));
        }
    }

    private static String normalize(String name) {
        // Requested #8: trim + lower
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
