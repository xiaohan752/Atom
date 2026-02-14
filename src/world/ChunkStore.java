package com.atom.life.world;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkStore {

    private final ConcurrentHashMap<Long, Chunk> chunks = new ConcurrentHashMap<>();
    private final Array<Chunk> renderList = new Array<>(false, 512);

    public ConcurrentHashMap<Long, Chunk> chunksMap() {
        return chunks;
    }

    public Array<Chunk> getRenderableChunks() {
        return renderList;
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    public Chunk getByKey(long key) {
        return chunks.get(key);
    }

    public Chunk getOrNull(int cx, int cz) {
        return chunks.get(ChunkKey.pack(cx, cz));
    }

    public Chunk putIfAbsentNew(int cx, int cz) {
        long key = ChunkKey.pack(cx, cz);
        Chunk newChunk = new Chunk(cx, cz);
        Chunk prev = chunks.putIfAbsent(key, newChunk);
        return (prev != null) ? prev : newChunk;
    }

    public boolean contains(int cx, int cz) {
        return chunks.containsKey(ChunkKey.pack(cx, cz));
    }

    public void removeByKey(long key) {
        chunks.remove(key);
    }

    public void clearAll() {
        chunks.clear();
        renderList.clear();
    }

    public void buildRenderListNear(int playerCx, int playerCz, int r) {
        renderList.clear();

        final int r2 = r * r;
        for (int cz = playerCz - r; cz <= playerCz + r; cz++) {
            for (int cx = playerCx - r; cx <= playerCx + r; cx++) {
                int dx = cx - playerCx;
                int dz = cz - playerCz;
                if (dx * dx + dz * dz > r2) continue;

                Chunk c = getOrNull(cx, cz);
                if (c == null || !c.isReady()) continue;

                boolean hasOpaque = (c.meshOpaque != null && c.indexCountOpaque > 0);
                boolean hasAlpha  = (c.meshAlpha  != null && c.indexCountAlpha  > 0);

                if (hasOpaque || hasAlpha || c.dirtyMesh) {
                    renderList.add(c);
                }
            }
        }
    }

    public LongArray collectUnloadKeys(int playerCx, int playerCz, int renderDistance) {
        int unloadR = renderDistance + 2;
        int unloadR2 = unloadR * unloadR;

        LongArray toRemove = new LongArray();
        for (Map.Entry<Long, Chunk> e : chunks.entrySet()) {
            long key = e.getKey();
            Chunk c = e.getValue();

            int dx = c.cx - playerCx;
            int dz = c.cz - playerCz;

            if (dx * dx + dz * dz > unloadR2) {
                toRemove.add(key);
            }
        }
        return toRemove;
    }
}
