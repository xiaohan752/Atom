package com.atom.life.time;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Real-world day/night aligned to local time.
 * Sun: rises in +X (east) around 06:00, sets in -X (west) around 18:00.
 */
public final class DayNightCycle {

    private final ZoneId zone = ZoneId.systemDefault();

    private float dayFraction;   // [0..1)
    private float timeHours;     // [0..24)
    private float ambient;       // [0..1]

    // directions "from surface toward body"
    private final Vector3 sunDir = new Vector3(1, 0, 0);
    private final Vector3 moonDir = new Vector3(-1, 0, 0);

    private float sunIntensity;   // [0..1]
    private float moonIntensity;  // [0..1]

    private final Vector3 skyColor = new Vector3(0.53f, 0.75f, 1.0f);

    private int currentHour;
    private int currentMinute;
    private int currentSecond;

    public void updateFromLocalTime() {
        ZonedDateTime now = ZonedDateTime.now(zone);

        currentHour = now.getHour();
        currentMinute = now.getMinute();
        currentSecond = now.getSecond();
        int n = now.getNano();

        float seconds = currentHour * 3600f + currentMinute * 60f + currentSecond + (n / 1_000_000_000f);
        dayFraction = (seconds / 86400f) % 1f;
        if (dayFraction < 0f) dayFraction += 1f;

        timeHours = dayFraction * 24f;

        // Elevation curve: sunrise 06:00, noon 12:00, sunset 18:00
        float elev = MathUtils.sin((dayFraction - 0.25f) * MathUtils.PI2); // [-1..1]
        float twilight = 0.18f;

        float sun = MathUtils.clamp((elev + twilight) / (2f * twilight), 0f, 1f);
        sun = sun * sun * (3f - 2f * sun); // smoothstep
        sunIntensity = (float) Math.pow(sun, 1.25);

        // Azimuth: make 06:00 => +X (east), 18:00 => -X (west)
        // dayFraction=0.25 => az=0 => +X
        // dayFraction=0.75 => az=PI => -X
        float az = (dayFraction - 0.25f) * MathUtils.PI2;

        // Sun direction (from point toward sun)
        sunDir.set(MathUtils.cos(az), elev, MathUtils.sin(az)).nor();

        // Moon: simple 12h phase offset, also east->west (rises ~18:00, sets ~06:00)
        float moonFrac = dayFraction + 0.5f;
        moonFrac = moonFrac - (float)Math.floor(moonFrac);

        float moonElev = MathUtils.sin((moonFrac - 0.25f) * MathUtils.PI2); // [-1..1]
        float moon = MathUtils.clamp(moonElev, 0f, 1f);
        moonIntensity = (float) Math.pow(moon, 1.1);

        float moonAz = (moonFrac - 0.25f) * MathUtils.PI2;
        moonDir.set(MathUtils.cos(moonAz), moonElev, MathUtils.sin(moonAz)).nor();

        // Ambient: night low, day higher
        float nightAmbient = 0.30f;
        float dayAmbient = 0.40f;
        ambient = MathUtils.lerp(nightAmbient, dayAmbient, sunIntensity);

        // Sky color: night -> day + slight warm tint around dawn/dusk
        Vector3 nightSky = tmp(0.02f, 0.02f, 0.06f);
        Vector3 daySky   = tmp(0.53f, 0.75f, 1.00f);

        float dawn = smoothPulse(dayFraction, 0.23f, 0.27f);
        float dusk = smoothPulse(dayFraction, 0.73f, 0.77f);
        float warm = MathUtils.clamp(dawn + dusk, 0f, 1f);

        skyColor.set(nightSky).lerp(daySky, sunIntensity);
        skyColor.x = MathUtils.clamp(skyColor.x + 0.25f * warm, 0f, 1f);
        skyColor.y = MathUtils.clamp(skyColor.y + 0.10f * warm, 0f, 1f);
        skyColor.z = MathUtils.clamp(skyColor.z - 0.08f * warm, 0f, 1f);
    }

    public float getDayFraction() { return dayFraction; }
    public float getTimeHours() { return timeHours; }
    public String getTimeString() { return String.format("%s:%s:%s", currentHour, currentMinute, currentSecond); }
    public float getAmbient() { return ambient; }
    public Vector3 getSkyColor() { return skyColor; }

    public Vector3 getSunDir() { return sunDir; }        // toward sun
    public float getSunIntensity() { return sunIntensity; }

    public Vector3 getMoonDir() { return moonDir; }      // toward moon
    public float getMoonIntensity() { return moonIntensity; }

    // helpers
    private static final Vector3 TMP = new Vector3();
    private static Vector3 tmp(float r, float g, float b) { return TMP.set(r, g, b); }

    private static float smoothPulse(float t, float a, float b) {
        if (a > b) { float x=a; a=b; b=x; }
        float mid = (a + b) * 0.5f;
        float w = (b - a) * 0.5f;
        float d = Math.abs(t - mid);
        float x = 1f - MathUtils.clamp(d / Math.max(w, 1e-6f), 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
