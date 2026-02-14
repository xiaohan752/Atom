package com.atom.life.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;

final class SkyTextures {

    static Texture makeSunTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        float cx = (size - 1) * 0.5f;
        float cy = (size - 1) * 0.5f;
        float r0 = size * 0.42f; // disc
        float r1 = size * 0.50f; // soft halo

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d = (float)Math.sqrt(dx*dx + dy*dy);

                float a;
                if (d <= r0) a = 1f;
                else if (d <= r1) a = 1f - (d - r0) / (r1 - r0);
                else a = 0f;

                // warm yellow
                float rr = 1.0f;
                float gg = 0.95f;
                float bb = 0.75f;

                pm.drawPixel(x, y, rgba(rr, gg, bb, a));
            }
        }

        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    static Texture makeMoonTexture(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        float cx = (size - 1) * 0.5f;
        float cy = (size - 1) * 0.5f;
        float r0 = size * 0.40f;
        float r1 = size * 0.48f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d = (float)Math.sqrt(dx*dx + dy*dy);

                float a;
                if (d <= r0) a = 1f;
                else if (d <= r1) a = 1f - (d - r0) / (r1 - r0);
                else a = 0f;

                // moon base
                float rr = 0.85f;
                float gg = 0.86f;
                float bb = 0.90f;

                // tiny "craters" noise (deterministic)
                float n = noise(x, y);
                rr *= MathUtils.lerp(0.92f, 1.02f, n);
                gg *= MathUtils.lerp(0.92f, 1.02f, n);
                bb *= MathUtils.lerp(0.92f, 1.02f, n);

                pm.drawPixel(x, y, rgba(rr, gg, bb, a));
            }
        }

        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    private static int rgba(float r, float g, float b, float a) {
        int ri = (int)(MathUtils.clamp(r,0,1) * 255f);
        int gi = (int)(MathUtils.clamp(g,0,1) * 255f);
        int bi = (int)(MathUtils.clamp(b,0,1) * 255f);
        int ai = (int)(MathUtils.clamp(a,0,1) * 255f);
        return (ri << 24) | (gi << 16) | (bi << 8) | (ai);
    }

    private static float noise(int x, int y) {
        int h = x * 374761393 + y * 668265263; // hash
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 1023) / 1023f;
    }

    private SkyTextures() {}
}
