package com.atom.life.world;

import com.atom.life.mesh.ChunkMesher;
import com.atom.life.mesh.ChunkMeshData;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.Array;

import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MeshSystem {

    private final ChunkStore store;
    private final ChunkMesher mesher;
    private final BlockAccess access;

    private final VertexAttributes vertexAttributes;

    // bounded + near-first queue
    private final BoundedMeshQueue meshQueue;
    private final Thread[] meshWorkers;
    private final AtomicLong meshSeq = new AtomicLong(0);

    private final ConcurrentLinkedQueue<MeshUpload> uploadQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean closing = new AtomicBoolean(false);

    // player chunk for priority
    private volatile int playerCx = 0;
    private volatile int playerCz = 0;

    private static class MeshUpload {
        final long key;
        final int rev;
        final ChunkMeshData meshData;

        MeshUpload(long key, int rev, ChunkMeshData meshData) {
            this.key = key;
            this.rev = rev;
            this.meshData = meshData;
        }
    }

    private static final class MeshTask implements Comparable<MeshTask> {
        final Chunk chunk;
        final long key;
        final int priority; // smaller = nearer
        final long seq;     // tie-breaker

        MeshTask(Chunk chunk, long key, int priority, long seq) {
            this.chunk = chunk;
            this.key = key;
            this.priority = priority;
            this.seq = seq;
        }

        @Override
        public int compareTo(MeshTask o) {
            int d = Integer.compare(this.priority, o.priority);
            if (d != 0) return d;
            return Long.compare(this.seq, o.seq);
        }
    }

    private static final class BoundedMeshQueue {
        private final int capacity;
        private final PriorityQueue<MeshTask> pq = new PriorityQueue<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();

        BoundedMeshQueue(int capacity) {
            this.capacity = Math.max(64, capacity);
        }

        int size() {
            lock.lock();
            try { return pq.size(); }
            finally { lock.unlock(); }
        }

        void clear() {
            lock.lock();
            try {
                pq.clear();
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }

        boolean offer(MeshTask t) {
            lock.lock();
            try {
                if (pq.size() < capacity) {
                    pq.add(t);
                    notEmpty.signal();
                    return true;
                }

                // find worst (farthest)
                MeshTask worst = null;
                for (MeshTask e : pq) {
                    if (worst == null || e.compareTo(worst) > 0) worst = e;
                }

                if (worst != null && t.compareTo(worst) < 0) {
                    pq.remove(worst);
                    pq.add(t);
                    notEmpty.signal();
                    return true;
                }

                return false;
            } finally {
                lock.unlock();
            }
        }

        MeshTask take() throws InterruptedException {
            lock.lock();
            try {
                while (pq.isEmpty()) notEmpty.await();
                return pq.poll();
            } finally {
                lock.unlock();
            }
        }
    }

    public MeshSystem(ChunkStore store, ChunkMesher mesher, BlockAccess access, VertexAttributes vertexAttributes) {
        this.store = store;
        this.mesher = mesher;
        this.access = access;
        this.vertexAttributes = vertexAttributes;

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

        int maxMeshQueue = 2048;
        this.meshQueue = new BoundedMeshQueue(maxMeshQueue);

        this.meshWorkers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::meshWorkerLoop, "mesh-worker-" + i);
            t.setDaemon(true);
            t.start();
            meshWorkers[i] = t;
        }
    }

    public int getMeshQueueSize() {
        return meshQueue.size();
    }

    public int getPendingUploadCount() {
        return uploadQueue.size();
    }

    public void setPlayerChunk(int cx, int cz) {
        playerCx = cx;
        playerCz = cz;
    }

    public void onChunkReady(Chunk c) {
        if (c == null || !c.isReady()) return;
        requestRemeshForce(c);

        requestRemeshForceNeighbor(c.cx + 1, c.cz);
        requestRemeshForceNeighbor(c.cx - 1, c.cz);
        requestRemeshForceNeighbor(c.cx, c.cz + 1);
        requestRemeshForceNeighbor(c.cx, c.cz - 1);
    }

    private void requestRemeshForceNeighbor(int cx, int cz) {
        Chunk n = store.getOrNull(cx, cz);
        if (n != null && n.isReady()) {
            requestRemeshForce(n);
        }
    }

    public void onChunkUnloaded(Chunk c) {
        if (c == null) return;
        c.meshRevision++;
        c.remeshQueued.set(false);
        c.dirtyMesh = false;
    }

    public void requestRemesh(Chunk c) {
        if (closing.get()) return;
        if (c == null || !c.isReady()) return;

        c.dirtyMesh = true;

        if (!c.remeshQueued.compareAndSet(false, true)) return;

        int pri = computePriority(c);
        MeshTask task = new MeshTask(c, ChunkKey.pack(c.cx, c.cz), pri, meshSeq.incrementAndGet());
        if (!meshQueue.offer(task)) {
            c.remeshQueued.set(false);
        }
    }

    public void requestRemeshForce(Chunk c) {
        if (closing.get()) return;
        if (c == null || !c.isReady()) return;

        c.dirtyMesh = true;

        if (c.meshBuilding || c.remeshQueued.get()) {
            c.meshRevision++;
        }

        if (c.remeshQueued.get()) return;
        if (!c.remeshQueued.compareAndSet(false, true)) return;

        int pri = computePriority(c);
        MeshTask task = new MeshTask(c, ChunkKey.pack(c.cx, c.cz), pri, meshSeq.incrementAndGet());
        if (!meshQueue.offer(task)) {
            c.remeshQueued.set(false);
        }
    }

    public void rescheduleDirtyNear(int radius, int budget) {
        if (closing.get()) return;
        int r2 = radius * radius;

        for (Chunk c : store.chunksMap().values()) {
            if (budget <= 0) break;
            if (c == null || !c.isReady()) continue;
            if (!c.dirtyMesh) continue;
            if (c.remeshQueued.get()) continue;

            int dx = c.cx - playerCx;
            int dz = c.cz - playerCz;
            if (dx * dx + dz * dz > r2) continue;

            requestRemesh(c);
            budget--;
        }
    }

    private int computePriority(Chunk c) {
        int dx = c.cx - playerCx;
        int dz = c.cz - playerCz;
        long p = (long) dx * (long) dx + (long) dz * (long) dz;
        return p > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) p;
    }

    private void meshWorkerLoop() {
        while (!closing.get()) {
            MeshTask t;
            try {
                t = meshQueue.take();
            } catch (InterruptedException ie) {
                continue;
            }
            if (t == null) continue;
            runMeshTask(t);
        }
    }

    private void runMeshTask(MeshTask t) {
        Chunk c = t.chunk;
        if (c == null) return;

        final int revAtStart = c.meshRevision;
        c.meshBuilding = true;

        try {
            if (closing.get()) return;
            if (c.status != Chunk.Status.READY) return;

            boolean hasMesh =
                (c.indexCountOpaque > 0 && c.meshOpaque != null) ||
                    (c.indexCountAlpha  > 0 && c.meshAlpha  != null);

            if (!c.dirtyMesh && hasMesh) {
                return;
            }

            ChunkMeshData md = mesher.buildMesh(c, access);

            if (closing.get()) return;
            if (c.status != Chunk.Status.READY) return;

            if (c.meshRevision != revAtStart) return;

            c.dirtyMesh = false;
            uploadQueue.add(new MeshUpload(t.key, revAtStart, md));

        } catch (Throwable ex) {
            ex.printStackTrace();
        } finally {
            c.meshBuilding = false;
            c.remeshQueued.set(false);

            if (!closing.get() && c.status == Chunk.Status.READY) {
                if (c.dirtyMesh || c.meshRevision != revAtStart) {
                    requestRemesh(c);
                }
            }
        }
    }

    /**
     * Render thread only: upload GPU meshes
     */
    public void pumpMeshUploads() {
        MeshUpload u;
        int limit = 32;

        while (limit-- > 0 && (u = uploadQueue.poll()) != null) {
            Chunk c = store.getByKey(u.key);
            if (c == null || c.status != Chunk.Status.READY) continue;

            if (c.meshRevision != u.rev) {
                if (c.dirtyMesh) requestRemesh(c);
                continue;
            }

            if (u.meshData != null) {
                c.applyMeshDataOpaque(u.meshData.opaque, vertexAttributes);
                c.applyMeshDataAlpha(u.meshData.alpha, vertexAttributes);
            } else {
                c.applyMeshDataOpaque(null, vertexAttributes);
                c.applyMeshDataAlpha(null, vertexAttributes);
            }

            c.dirtyMesh = false;

            if (c.dirtyMesh) requestRemesh(c);
        }
    }

    public void shutdownStopWorkers() {
        closing.set(true);
        if (meshQueue != null) meshQueue.clear();

        if (meshWorkers != null) {
            for (Thread t : meshWorkers) {
                if (t != null) t.interrupt();
            }
        }
    }
}
