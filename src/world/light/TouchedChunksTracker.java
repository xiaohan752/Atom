package com.atom.life.world.light;

import com.atom.life.world.Chunk;
import com.atom.life.world.ChunkKey;
import com.atom.life.world.ChunkStore;
import com.atom.life.world.MeshSystem;
import com.atom.life.world.util.LongHashSet;
import com.badlogic.gdx.utils.LongArray;

public final class TouchedChunksTracker {

    private final ChunkStore store;
    private final MeshSystem meshSystem;

    private final LongArray touchedChunks = new LongArray(false, 64);
    private final LongHashSet touchedSeen = new LongHashSet(256);

    public TouchedChunksTracker(ChunkStore store, MeshSystem meshSystem) {
        this.store = store;
        this.meshSystem = meshSystem;
    }

    public void beginPass() {
        // stamp reset
        touchedSeen.reset();
        touchedChunks.clear();
    }

    public void markTouched(Chunk c) {
        if (c == null) return;
        long key = ChunkKey.pack(c.cx, c.cz);
        if (touchedSeen.add(key)) {
            touchedChunks.add(key);
        }
    }

    public void flushTouchedRemesh() {
        for (int i = 0; i < touchedChunks.size; i++) {
            long key = touchedChunks.get(i);
            Chunk c = store.getByKey(key);
            if (c != null && c.isReady()) {
                meshSystem.requestRemeshForce(c);
            }
        }
        touchedChunks.clear();
        touchedSeen.reset();
    }
}
