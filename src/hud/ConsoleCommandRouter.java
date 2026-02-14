package com.atom.life.hud;

/**
 * Parses a line and routes to concrete commands.
 */
public final class ConsoleCommandRouter {

    public void execute(String line, ConsoleCommandContext ctx) {
        String[] parts = splitTokens(line);
        if (parts.length == 0) return;

        String cmd = parts[0];

        switch (cmd.toLowerCase()) {
            case "pickblock" -> {
                if (parts.length < 2) {
                    ctx.println("Usage: pickblock <name>");
                    return;
                }
                // keep semantics: allow "name*" style
                String arg = joinTail(parts, 1);
                ConsoleCommands.pickBlock(ctx, stripOuterQuotes(arg));
            }
            case "weather" -> {
                if (parts.length < 2) {
                    ctx.println("Usage: weather <name>");
                    return;
                }
                String arg = joinTail(parts, 1);
                ConsoleCommands.weather(ctx, stripOuterQuotes(arg));
            }
            case "setblock" -> {
                // ConsoleCommands handles full validation/usage
                ConsoleCommands.setBlock(ctx, parts);
            }
            case "weatherauto" -> {
                String arg = (parts.length >= 2) ? joinTail(parts, 1) : "status";
                ConsoleCommands.weatherAuto(ctx, stripOuterQuotes(arg));
            }
            default -> ctx.println("Unknown command: " + cmd);
        }
    }

    /**
     * Examples:
     *  setblock 0 64 0 lime_block_jitter
     *  setblock aim red_*_wire_on
     *  pickblock "lime_block_jitter"
     */
    static String[] splitTokens(String line) {
        if (line == null) return new String[0];
        line = line.trim();
        if (line.isEmpty()) return new String[0];

        java.util.ArrayList<String> out = new java.util.ArrayList<>(8);

        StringBuilder cur = new StringBuilder(64);
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                } else {
                    cur.append(c);
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
                continue;
            }

            if (Character.isWhitespace(c)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(c);
        }

        if (cur.length() > 0) out.add(cur.toString());

        return out.toArray(new String[0]);
    }

    private static String joinTail(String[] parts, int start) {
        if (start >= parts.length) return "";
        if (start == parts.length - 1) return parts[start];

        StringBuilder sb = new StringBuilder(64);
        for (int i = start; i < parts.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String stripOuterQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}
