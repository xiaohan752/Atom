package com.atom.life.world;

import com.atom.life.io.ChunkIO;
import com.atom.life.data.WorldIO;
import com.atom.life.world.blocks.BlockRegistry;
import com.badlogic.gdx.files.FileHandle;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChunkIOSystem {

    private final WorldIO info;
    private final ChunkStore store;
    private final ChunkGenerator generator;
    private final ChunkIO chunkIO;

    private final ThreadPoolExecutor executor;

    // save scheduling
    private static final long SAVE_DELAY_MS = 2000;
    private static final long SAVE_SCAN_INTERVAL_MS = 200;
    private static final int  SAVE_BUDGET_PER_SCAN = 4;
    private long lastSaveScanMs = 0;

    private final AtomicBoolean closing = new AtomicBoolean(false);

    public ChunkIOSystem(WorldIO info, FileHandle saveDir, ChunkStore store, BlockRegistry registry) {
        this.info = info;
        this.store = store;

        this.generator = new ChunkGenerator(info.seed, info.worldMode, registry);
        this.chunkIO = new ChunkIO(saveDir);

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        int maxQueue = 512;
        this.executor = new ThreadPoolExecutor(
            threads, threads,
            0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(maxQueue),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public WorldIO worldInfo() { return info; }

    public ChunkIO chunkIO() {
        return chunkIO;
    }

    public boolean isClosing() {
        return closing.get();
    }

    public int getExecutorQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * ensure chunk in store, if newly created then async load/generate.
     * onChunkReady will be called when READY.
     */
    public void ensureChunkAsync(int cx, int cz, java.util.function.Consumer<Chunk> onChunkReady) {
        if (closing.get()) return;

        long key = ChunkKey.pack(cx, cz);

        // already exists
        if (store.getByKey(key) != null) return;

        // placeholder
        Chunk c = store.putIfAbsentNew(cx, cz);
        if (c.cx != cx || c.cz != cz) return;
        if (store.getByKey(key) != c) return;

        safeSubmitIO(() -> {
            try {
                if (closing.get()) return;

                byte[] loaded = chunkIO.tryLoad(cx, cz, Chunk.SX, Chunk.SY, Chunk.SZ);
                if (loaded == null) {
                    loaded = generator.generateChunkBlocks(cx, cz, Chunk.SX, Chunk.SY, Chunk.SZ);
                }

                if (closing.get()) return;

                System.arraycopy(loaded, 0, c.blocks, 0, c.blocks.length);

                c.dirtyBlocks = false;
                c.savedRevision = c.saveRevision;
                c.status = Chunk.Status.READY;

                c.dirtyMesh = true;

                if (onChunkReady != null) onChunkReady.accept(c);

            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
    }
    
    public void pumpChunkSavesDelayed() {
        if (closing.get()) return;

        long now = System.currentTimeMillis();
        if (now - lastSaveScanMs < SAVE_SCAN_INTERVAL_MS) return;
        lastSaveScanMs = now;

        int budget = SAVE_BUDGET_PER_SCAN;

        for (Chunk c : store.chunksMap().values()) {
            if (budget <= 0) break;
            if (c == null || !c.isReady()) continue;

            if (c.dirtyBlocks && c.savedRevision == c.saveRevision) {
                c.dirtyBlocks = false;
            }

            if (!c.dirtyBlocks) continue;
            if (now - c.lastDirtyTimeMs < SAVE_DELAY_MS) continue;

            requestSaveCoalesced(c);
            budget--;
        }
    }

    public void requestSaveCoalesced(Chunk c) {
        if (closing.get()) return;
        if (c == null || !c.isReady()) return;
        if (!c.dirtyBlocks) return;

        if (!c.saveQueued.compareAndSet(false, true)) return;

        final int rev = c.saveRevision;
        final int cx = c.cx, cz = c.cz;
        final byte[] snapshot = Arrays.copyOf(c.blocks, c.blocks.length);

        safeSubmitIO(() -> {
            try {
                chunkIO.save(cx, cz, Chunk.SX, Chunk.SY, Chunk.SZ, snapshot);
                c.savedRevision = rev;
            } catch (Throwable ex) {
                ex.printStackTrace();
            } finally {
                c.saveQueued.set(false);
            }
        });
    }

    public void forceSaveSnapshotNow(Chunk c) {
        if (c == null) return;
        if (!c.dirtyBlocks) return;

        final int cx = c.cx, cz = c.cz;
        final byte[] snapshot = Arrays.copyOf(c.blocks, c.blocks.length);

        if (closing.get() || executor.isShutdown()) {
            try {
                chunkIO.save(cx, cz, Chunk.SX, Chunk.SY, Chunk.SZ, snapshot);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
            return;
        }

        safeSubmitIO(() -> {
            try {
                chunkIO.save(cx, cz, Chunk.SX, Chunk.SY, Chunk.SZ, snapshot);
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
    }

    public void forceSaveSync(Chunk c) {
        if (c == null) return;
        if (!c.dirtyBlocks) return;
        try {
            chunkIO.save(c.cx, c.cz, Chunk.SX, Chunk.SY, Chunk.SZ, c.blocks);
            c.dirtyBlocks = false;
            c.savedRevision = c.saveRevision;
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    public void beginShutdown() {
        closing.set(true);
    }

    public void shutdownExecutorsGracefully() {
        beginShutdown();

        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(800, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void safeSubmitIO(Runnable r) {
        if (closing.get()) return;
        try {
            executor.execute(r);
        } catch (RejectedExecutionException ex) {
            // shutdown / queue full: drop
        }
    }
}
