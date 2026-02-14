package com.atom.life.input;

/**
 * 行为对齐你原逻辑：
 * - 首次按下：立即触发，然后 cooldown = firstMultiplier * interval
 * - 按住：每 interval 触发一次
 * - 松开：重置
 */
public final class CooldownGate {

    private final Channel breakCh;
    private final Channel placeCh;

    public CooldownGate(float intervalSec, float firstMultiplier) {
        this.breakCh = new Channel(intervalSec, firstMultiplier);
        this.placeCh = new Channel(intervalSec, firstMultiplier);
    }

    public void tick(float dt) {
        breakCh.tick(dt);
        placeCh.tick(dt);
    }

    public boolean allowBreak(boolean down) {
        return breakCh.allow(down);
    }

    public boolean allowPlace(boolean down) {
        return placeCh.allow(down);
    }

    private static final class Channel {
        final float interval;
        final float firstMult;

        float cooldown = 0f;
        boolean wasDown = false;

        Channel(float interval, float firstMult) {
            this.interval = Math.max(1e-4f, interval);
            this.firstMult = Math.max(1f, firstMult);
        }

        void tick(float dt) {
            cooldown -= dt;
        }

        boolean allow(boolean down) {
            if (!down) {
                wasDown = false;
                cooldown = 0f;
                return false;
            }

            if (!wasDown) {
                wasDown = true;
                cooldown = firstMult * interval;
                return true;
            }

            if (cooldown <= 0f) {
                cooldown = interval;
                return true;
            }

            return false;
        }
    }
}
