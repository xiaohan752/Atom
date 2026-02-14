package com.atom.life.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.atom.life.world.blocks.BlockDef;
import com.atom.life.world.blocks.BlockRegistry;

public class BlockAtlas {

    private final Texture texture;

    private final int tileSize = 16;
    private final int tilesPerRow;
    private final int atlasW;
    private final int atlasH;

    private final boolean[] painted;

    // Precomputed UVs for 4 tiles: each tile -> [u0,v0,u1,v1]
    private final float[][] uvs;

    public BlockAtlas(BlockRegistry registry) {
        // 1) tileIndex
        int maxTile = 0;
        for (int i = 0; i < 256; i++) {
            BlockDef d = registry.def((byte)i);
            if (d.shape == BlockDef.Shape.AIR) continue;
            if (d.renderLayer == BlockDef.RenderLayer.NONE) continue;
            if (d == null) continue;
            maxTile = Math.max(maxTile, Math.max(d.tileTop, Math.max(d.tileSide, d.tileBottom)));
        }

        int tileCount = Math.max(1, maxTile + 1);

        // 2) tilesPerRow
        this.tilesPerRow = (int) Math.ceil(Math.sqrt(tileCount));
        this.atlasW = tileSize * tilesPerRow;
        this.atlasH = tileSize * tilesPerRow;

        this.uvs = new float[tileCount][4];
        this.painted = new boolean[tileCount];

        Pixmap pm = new Pixmap(atlasW, atlasH, Pixmap.Format.RGBA8888);

        for (int id = 0; id < 256; id++) {
            BlockDef d = registry.def((byte) id);
            if (d == null) continue;

            paintTileIfNeeded(pm, d.tileTop, d);
            paintTileIfNeeded(pm, d.tileSide, d);
            paintTileIfNeeded(pm, d.tileBottom, d);
        }
        buildTilesFromRegistry(pm, registry);

        texture = new Texture(pm);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();

        // UV cache
        for (int t = 0; t < tileCount; t++) {
            int tx = t % tilesPerRow;
            int ty = t / tilesPerRow;

            float u0 = (tx * tileSize) / (float) atlasW;
            float v0 = (ty * tileSize) / (float) atlasH;
            float u1 = ((tx + 1) * tileSize) / (float) atlasW;
            float v1 = ((ty + 1) * tileSize) / (float) atlasH;

            uvs[t][0] = u0;
            uvs[t][1] = v0;
            uvs[t][2] = u1;
            uvs[t][3] = v1;
        }
    }

    private void paintTileIfNeeded(Pixmap pm, int tileIndex, BlockDef d) {
        if (tileIndex < 0 || tileIndex >= painted.length) return;
        if (painted[tileIndex]) return;

        int tx = tileIndex % tilesPerRow;
        int ty = tileIndex / tilesPerRow;

        if (d.jitter) {
            fillTileJitter(pm, tx, ty, tileIndex, d.baseColorRGBA, d.jitterStrength, d.lumaJitter);
        } else {
            fillTileSolid(pm, tx, ty, d.baseColorRGBA);
        }

        painted[tileIndex] = true;
    }

    private void fillTileSolid(Pixmap pm, int tileX, int tileY, int rgba) {
        int x0 = tileX * tileSize;
        int y0 = tileY * tileSize;
        pm.setColor(intToColor(rgba));
        pm.fillRectangle(x0, y0, tileSize, tileSize);
    }

    private void fillTileJitter(Pixmap pm, int tileX, int tileY, int tileIndex,
                                int rgba, float strength, float lumaJitter) {

        float br = ((rgba >> 24) & 0xFF) / 255f;
        float bg = ((rgba >> 16) & 0xFF) / 255f;
        float bb = ((rgba >> 8)  & 0xFF) / 255f;
        float ba = (rgba & 0xFF) / 255f;

        int x0 = tileX * tileSize;
        int y0 = tileY * tileSize;

        java.util.Random rand = new java.util.Random(0xC0FFEE ^ (tileIndex * 1315423911L));

        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                float n1 = rand.nextFloat() * 2f - 1f;
                float n2 = rand.nextFloat() * 2f - 1f;
                float n3 = rand.nextFloat() * 2f - 1f;

                float rr = br + n1 * strength;
                float gg = bg + n2 * strength;
                float bb2 = bb + n3 * strength;

                float lum = (rand.nextFloat() * 2f - 1f) * lumaJitter;
                rr += lum; gg += lum; bb2 += lum;

                rr = clamp01(rr);
                gg = clamp01(gg);
                bb2 = clamp01(bb2);

                pm.setColor(rr, gg, bb2, ba);
                pm.drawPixel(x0 + dx, y0 + dy);
            }
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static com.badlogic.gdx.graphics.Color intToColor(int rgba) {
        float r = ((rgba >> 24) & 0xFF) / 255f;
        float g = ((rgba >> 16) & 0xFF) / 255f;
        float b = ((rgba >> 8)  & 0xFF) / 255f;
        float a = (rgba & 0xFF) / 255f;
        return new com.badlogic.gdx.graphics.Color(r, g, b, a);
    }

    public Texture getTexture() {
        return texture;
    }

    public float[] uv(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= uvs.length) return uvs[0];
        return uvs[tileIndex];
    }

    public void dispose() {
        texture.dispose();
    }

    private void buildTilesFromRegistry(Pixmap pm, BlockRegistry registry) {
        final int tileCount = tilesPerRow * tilesPerRow;

        boolean[] painted = new boolean[tileCount];

        // owner tiles
        for (int id = 0; id < 256; id++) {
            BlockDef d = registry.def((byte) id);
            if (d == null) continue;

            if (d.shape == BlockDef.Shape.AIR) continue;
            if (d.renderLayer == BlockDef.RenderLayer.NONE) continue;

            int t0 = d.tileTop;
            int t1 = d.tileSide;
            int t2 = d.tileBottom;

            if (t0 == t1 && t1 == t2) {
                int t = t0;
                if (t >= 0 && t < tileCount && !painted[t]) {
                    paintTile(pm, t, d);
                    painted[t] = true;
                }
            }
        }

        // fill remaining tiles
        for (int id = 0; id < 256; id++) {
            BlockDef d = registry.def((byte) id);
            if (d == null) continue;

            if (d.shape == BlockDef.Shape.AIR) continue;
            if (d.renderLayer == BlockDef.RenderLayer.NONE) continue;

            int[] faceTiles = new int[]{ d.tileTop, d.tileSide, d.tileBottom };
            for (int t : faceTiles) {
                if (t < 0 || t >= tileCount) continue;
                if (painted[t]) continue;

                paintTile(pm, t, d);
                painted[t] = true;
            }
        }

        // fallback
        for (int t = 0; t < tileCount; t++) {
            if (!painted[t]) {
                int tx = t % tilesPerRow;
                int ty = t / tilesPerRow;
                pm.setColor(0f, 0f, 0f, 0f);
                pm.fillRectangle(tx * tileSize, ty * tileSize, tileSize, tileSize);
            }
        }
    }

    private void paintTile(Pixmap pm, int tileIndex, BlockDef d) {
        int tx = tileIndex % tilesPerRow;
        int ty = tileIndex / tilesPerRow;

        int rgba = (d.baseColorRGBA != 0) ? d.baseColorRGBA : 0xFFFFFFFF;

        boolean jitter = d.jitter;
        float jitterStrength = (d.jitterStrength > 0f) ? d.jitterStrength : 0.10f;
        float lumaJitter = (d.lumaJitter > 0f) ? d.lumaJitter : 0.06f;

        boolean isWater = "water".equalsIgnoreCase(d.name);

        if (d.renderLayer == BlockDef.RenderLayer.ALPHA) {
            if (isWater) {
                fillWaterLike(pm, tx, ty, tileIndex, rgba, jitter, jitterStrength, lumaJitter);
            } else {
                fillGlassLike(pm, tx, ty, rgba, jitter, jitterStrength, lumaJitter);
            }
            return;
        }

        if (jitter) fillJitterTile(pm, tx, ty, rgba, jitterStrength, lumaJitter);
        else fillSolid(pm, tx, ty, rgba);
    }

    private void fillSolid(Pixmap pm, int tileX, int tileY, int rgba) {
        int x0 = tileX * tileSize;
        int y0 = tileY * tileSize;
        pm.setColor(intToColor(rgba));
        pm.fillRectangle(x0, y0, tileSize, tileSize);
    }

    private void fillJitterTile(Pixmap pm, int tileX, int tileY, int rgba, float strength, float lumaJitter) {
        float br = ((rgba >> 24) & 0xFF) / 255f;
        float bg = ((rgba >> 16) & 0xFF) / 255f;
        float bb = ((rgba >> 8) & 0xFF) / 255f;
        float ba = (rgba & 0xFF) / 255f;

        int x0 = tileX * tileSize;
        int y0 = tileY * tileSize;

        int tileIndex = tileY * tilesPerRow + tileX;
        java.util.Random rand = new java.util.Random(0xC0FFEE ^ (tileIndex * 1315423911L));

        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                float n1 = rand.nextFloat() * 2f - 1f;
                float n2 = rand.nextFloat() * 2f - 1f;
                float n3 = rand.nextFloat() * 2f - 1f;

                float rr = clamp01(br + n1 * strength);
                float gg = clamp01(bg + n2 * strength);
                float bb2 = clamp01(bb + n3 * strength);

                float lum = (rand.nextFloat() * 2f - 1f) * lumaJitter;
                rr = clamp01(rr + lum);
                gg = clamp01(gg + lum);
                bb2 = clamp01(bb2 + lum);

                pm.setColor(rr, gg, bb2, ba);
                pm.drawPixel(x0 + dx, y0 + dy);
            }
        }
    }

    private void fillGlassLike(Pixmap pm, int tileX, int tileY, int rgba, boolean jitter, float strength, float lumaJitter) {
        float br = ((rgba >> 24) & 0xFF) / 255f;
        float bg = ((rgba >> 16) & 0xFF) / 255f;
        float bb = ((rgba >> 8) & 0xFF) / 255f;
        float ba = (rgba & 0xFF) / 255f;

        int x0 = tileX * tileSize;
        int y0 = tileY * tileSize;

        int tileIndex = tileY * tilesPerRow + tileX;
        java.util.Random rand = new java.util.Random(0x51A55EEDL ^ (tileIndex * 1315423911L));

        final float edgeAlpha = 1f;

        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                float rr = br, gg = bg, bb2 = bb;

                if (jitter) {
                    float n = (rand.nextFloat() * 2f - 1f) * strength;
                    float lum = (rand.nextFloat() * 2f - 1f) * lumaJitter;
                    rr = clamp01(rr + n + lum);
                    gg = clamp01(gg + n + lum);
                    bb2 = clamp01(bb2 + n + lum);
                }

                boolean edge = (dx == 0 || dy == 0 || dx == tileSize - 1 || dy == tileSize - 1);
                if (edge) {
                    rr = clamp01(rr + 0.18f);
                    gg = clamp01(gg + 0.18f);
                    bb2 = clamp01(bb2 + 0.18f);
                }

                float a = edge ? edgeAlpha : ba;

                pm.setColor(rr, gg, bb2, clamp01(a));
                pm.drawPixel(x0 + dx, y0 + dy);
            }
        }
    }

    private void fillWaterLike(Pixmap pm, int tileX, int tileY, int tileIndex,
                               int rgba, boolean jitter, float strength, float lumaJitter) {

        float br = ((rgba >> 24) & 0xFF) / 255f;
        float bg = ((rgba >> 16) & 0xFF) / 255f;
        float bb = ((rgba >> 8)  & 0xFF) / 255f;
        float ba = (rgba & 0xFF) / 255f;

        int x0 = tileX * tileSize;
        int y0 = tileY * tileSize;

        java.util.Random rand = new java.util.Random(0x2E86DEAAL ^ (tileIndex * 1315423911L));

        for (int dy = 0; dy < tileSize; dy++) {
            float t = dy / (float)(tileSize - 1);
            float grad = (t - 0.5f) * 0.12f;

            for (int dx = 0; dx < tileSize; dx++) {

                float rr = br;
                float gg = bg;
                float bb2 = bb;

//                float n = 0f;
//                if (jitter) {
//                    float n1 = rand.nextFloat() * 2f - 1f;
//                    float n2 = rand.nextFloat() * 2f - 1f;
//                    n = (n1 * strength * 0.65f) + (n2 * lumaJitter * 0.65f);
//                }
//
//                rr = clamp01(rr + grad + n);
//                gg = clamp01(gg + grad + n);
//                bb2 = clamp01(bb2 + grad + n);

//                int h = (dx * 73856093) ^ (dy * 19349663) ^ (tileIndex * 83492791);
//                if ((h & 31) == 0) { // 1/32 概率
//                    rr = clamp01(rr + 0.10f);
//                    gg = clamp01(gg + 0.10f);
//                    bb2 = clamp01(bb2 + 0.10f);
//                }

                pm.setColor(rr, gg, bb2, clamp01(ba));
                pm.drawPixel(x0 + dx, y0 + dy);
            }
        }

//        for (int i = 0; i < tileSize; i++) {
//            darkenPixel(pm, x0 + i, y0 + 0, 0.06f);
//            darkenPixel(pm, x0 + i, y0 + tileSize - 1, 0.06f);
//            darkenPixel(pm, x0 + 0, y0 + i, 0.06f);
//            darkenPixel(pm, x0 + tileSize - 1, y0 + i, 0.06f);
//        }
    }

    private void darkenPixel(Pixmap pm, int x, int y, float amount) {
        int c = pm.getPixel(x, y);
        float r = ((c >> 24) & 0xFF) / 255f;
        float g = ((c >> 16) & 0xFF) / 255f;
        float b = ((c >> 8) & 0xFF) / 255f;
        float a = (c & 0xFF) / 255f;

        r = clamp01(r - amount);
        g = clamp01(g - amount);
        b = clamp01(b - amount);

        pm.setColor(r, g, b, a);
        pm.drawPixel(x, y);
    }

}
