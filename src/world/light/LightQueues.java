package com.atom.life.world.light;

import com.atom.life.world.util.LongIntHashSet;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;

public final class LightQueues {

    private final LongArray addChunk = new LongArray(false, 1024);
    private final IntArray addLocal = new IntArray(false, 1024);

    private final LongArray remXZ = new LongArray(false, 1024);
    private final IntArray remYL = new IntArray(false, 1024);

    private final LongIntHashSet addSeen = new LongIntHashSet(2048);

    public void beginPass(boolean clearQueues) {
        addSeen.reset();
        if (clearQueues) {
            addChunk.clear();
            addLocal.clear();
            remXZ.clear();
            remYL.clear();
        }
    }

    public void pushAdd(long chunkKey, int local) {
        if (addSeen.add(chunkKey, local)) {
            addChunk.add(chunkKey);
            addLocal.add(local);
        }
    }

    public boolean popAdd(AddEntry out) {
        if (addChunk.size == 0) return false;
        out.chunkKey = addChunk.pop();
        out.local = addLocal.pop();
        return true;
    }

    public int addSize() {
        return addChunk.size;
    }

    public void pushRemove(int wx, int wy, int wz, int level) {
        remXZ.add(packXZ32(wx, wz));
        remYL.add((wy & 0x3FF) | ((level & 0xFF) << 10));
    }

    public boolean popRemove(RemEntry out) {
        if (remXZ.size == 0) return false;
        long xz = remXZ.pop();
        int yl = remYL.pop();

        out.x = unpackX32(xz);
        out.z = unpackZ32(xz);
        out.y = (yl & 0x3FF);
        out.level = (yl >>> 10);
        return true;
    }

    public int remSize() {
        return remXZ.size;
    }

    public static final class AddEntry {
        public long chunkKey;
        public int local;
    }

    public static final class RemEntry {
        public int x, y, z, level;
    }

    private static long packXZ32(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX32(long xz) { return (int) (xz >> 32); }
    private static int unpackZ32(long xz) { return (int) xz; }
}
