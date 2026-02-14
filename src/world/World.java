package com.atom.life.world;

import com.atom.life.GlobalVariables;
import com.atom.life.data.WorldIO;
import com.atom.life.world.blocks.BlockDef;
import com.atom.life.world.blocks.BlockRegistry;
import com.atom.life.mesh.ChunkMesher;
import com.atom.life.render.BlockAtlas;
import com.atom.life.world.circuit.CircuitSystem;
import com.atom.life.world.light.BlockLightSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;

import java.util.Set;

import static com.atom.life.GlobalVariables.*;

public class World implements BlockAccess, MesherLightAccess {

    private final long seed;
    private final int renderDistance;

    private final WorldIO info;
    private final String worldMode;
    private final String worldVersion;

    private final BlockRegistry registry;
    private final BlockAtlas atlas;

    // systems
    private final ChunkStore store;
    private final ChunkIOSystem ioSystem;
    private final MeshSystem meshSystem;
    private final CircuitSystem circuitSystem;

    // player chunk
    private volatile int playerCx = 0;
    private volatile int playerCz = 0;

    private boolean cameraSnappedToGround = false;

    // vertex attributes
    public final com.badlogic.gdx.graphics.VertexAttributes vertexAttributes;

    private final BlockLightSystem lightSystem;

    public World(BlockRegistry registry, BlockAtlas atlas) {
        this.renderDistance = GlobalVariables.renderDistance;
        this.registry = registry;
        this.atlas = atlas;

        FileHandle saveDir = getSaveDir();
        this.info = WorldIO.loadOrCreate(saveDir);
        this.seed = info.seed;
        this.worldMode = info.worldMode;
        this.worldVersion = info.version;

        this.vertexAttributes = new com.badlogic.gdx.graphics.VertexAttributes(
            new com.badlogic.gdx.graphics.VertexAttribute(
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, "a_position"
            ),
            new com.badlogic.gdx.graphics.VertexAttribute(
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, 3, "a_normal"
            ),
            new com.badlogic.gdx.graphics.VertexAttribute(
                com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, "a_localUV"
            ),
            new com.badlogic.gdx.graphics.VertexAttribute(
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Generic, 4, "a_atlasRect"
            ),
            new com.badlogic.gdx.graphics.VertexAttribute(
                com.badlogic.gdx.graphics.VertexAttributes.Usage.Generic, 1, "a_emission"
            )
        );

        this.store = new ChunkStore();

        this.ioSystem = new ChunkIOSystem(info, saveDir, store, registry);

        ChunkMesher mesher = new ChunkMesher(registry, atlas);
        this.meshSystem = new MeshSystem(store, mesher, this, vertexAttributes);

        this.lightSystem = new BlockLightSystem(this, registry, store, meshSystem);
        this.circuitSystem = new CircuitSystem(this);
    }

    public void tickCircuits(float dt) {
        circuitSystem.update(dt);
    }

    public boolean interactCircuit(int wx, int wy, int wz) {
        return circuitSystem.onInteract(wx, wy, wz);
    }

    public FileHandle getSaveDir() {
        return Gdx.files.local("saves/" + worldName + "/");
    }

    // ---- stats ----
    public int getLoadedChunkCount() { return store.getLoadedChunkCount(); }
    public int getPendingUploadCount() { return meshSystem.getPendingUploadCount(); }
    public int getExecutorQueueSize() { return ioSystem.getExecutorQueueSize(); }
    public int getMeshQueueSize() { return meshSystem.getMeshQueueSize(); }
    public Array<Chunk> getRenderableChunks() { return store.getRenderableChunks(); }

    // ---- main update ----
    public void update(Vector3 playerPos) {
        playerCx = Math.floorDiv((int) Math.floor(playerPos.x), Chunk.SX);
        playerCz = Math.floorDiv((int) Math.floor(playerPos.z), Chunk.SZ);

        meshSystem.setPlayerChunk(playerCx, playerCz);

        int r = renderDistance;

        // 1) IO: ensure chunks in range
        streamEnsureChunks(r);

        // 2) Store: build render list near
        store.buildRenderListNear(playerCx, playerCz, r);

        // 3) Store: unload selection
        unloadFarChunks();

        // 4) Mesh: budgeted reschedule
        meshSystem.rescheduleDirtyNear(renderDistance + 1, 64);

        // 5) IO: delayed save scheduling
        ioSystem.pumpChunkSavesDelayed();
    }

    private void streamEnsureChunks(int r) {
        if (ioSystem.isClosing()) return;

        final int r2 = r * r;
        for (int cz = playerCz - r; cz <= playerCz + r; cz++) {
            for (int cx = playerCx - r; cx <= playerCx + r; cx++) {
                int dx = cx - playerCx;
                int dz = cz - playerCz;
                if (dx * dx + dz * dz > r2) continue;

                ioSystem.ensureChunkAsync(cx, cz, (readyChunk) -> {
                    Gdx.app.postRunnable(() -> {
                        if (readyChunk == null || !readyChunk.isReady()) return;

                        lightSystem.onChunkReady(readyChunk);
                        meshSystem.onChunkReady(readyChunk);
                    });
                });
            }
        }
    }

    public int getSurfaceY(int wx, int wz) {
        for (int y = Chunk.SY - 1; y >= 0; y--) {
            byte id = getBlock(wx, y, wz);
            if (id == 0) continue;

            if (isSolidAt(wx, y, wz)) {
                return y;
            }
        }
        return 64;
    }

    private void unloadFarChunks() {
        LongArray toRemove = store.collectUnloadKeys(playerCx, playerCz, renderDistance);
        for (int i = 0; i < toRemove.size; i++) {
            long key = toRemove.get(i);
            Chunk c = store.getByKey(key);
            if (c == null) {
                store.removeByKey(key);
                continue;
            }

            if (c.dirtyBlocks && c.isReady()) {
                ioSystem.forceSaveSnapshotNow(c);
                c.savedRevision = c.saveRevision;
                c.dirtyBlocks = false;
            }

            meshSystem.onChunkUnloaded(c);

            c.status = Chunk.Status.UNLOADED;
            c.disposeGpu();

            // remove from map
            store.removeByKey(key);
        }
    }

    public void pumpMeshUploads() {
        meshSystem.pumpMeshUploads();
    }

    @Override
    public byte getBlock(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SY) return 0;

        int cx = Math.floorDiv(wx, Chunk.SX);
        int cz = Math.floorDiv(wz, Chunk.SZ);
        int lx = wx - cx * Chunk.SX;
        int lz = wz - cz * Chunk.SZ;

        Chunk c = store.getOrNull(cx, cz);
        if (c == null || !c.isReady()) return 0;

        return c.getLocal(lx, wy, lz);
    }

    public boolean setBlock(int wx, int wy, int wz, byte id) {
        if (wy < 0 || wy >= Chunk.SY) return false;

        int cx = Math.floorDiv(wx, Chunk.SX);
        int cz = Math.floorDiv(wz, Chunk.SZ);

        Chunk c = store.getOrNull(cx, cz);
        if (c == null || !c.isReady()) return false;

        int lx = wx - cx * Chunk.SX;
        int lz = wz - cz * Chunk.SZ;

        byte cur = c.getLocal(lx, wy, lz);
        if (cur == id) return false;

        c.setLocal(lx, wy, lz, id);

        lightSystem.onBlockChanged(wx, wy, wz, cur, id);

        circuitSystem.onBlockChanged(wx, wy, wz, cur, id);

        meshSystem.requestRemesh(c);

        if (lx == 0) forceNeighborMesh(cx - 1, cz);
        if (lx == Chunk.SX - 1) forceNeighborMesh(cx + 1, cz);
        if (lz == 0) forceNeighborMesh(cx, cz - 1);
        if (lz == Chunk.SZ - 1) forceNeighborMesh(cx, cz + 1);

        return true;
    }

    private void forceNeighborMesh(int cx, int cz) {
        Chunk n = store.getOrNull(cx, cz);
        if (n != null && n.isReady()) {
            meshSystem.requestRemeshForce(n);
        }
    }

    public String getBlockName(byte id) {
        BlockDef d = registry.def(id);
        return d == null ? ("id=" + (id & 0xFF)) : d.name;
    }

    @Override
    public byte getBlockLight(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SY) return 0;

        int cx = Math.floorDiv(wx, Chunk.SX);
        int cz = Math.floorDiv(wz, Chunk.SZ);
        int lx = wx - cx * Chunk.SX;
        int lz = wz - cz * Chunk.SZ;

        Chunk c = store.getOrNull(cx, cz);
        if (c == null || !c.isReady()) return 0;

        return c.getLightLocal(lx, wy, lz);
    }

    public byte idByName(String name, byte fallback) {
        return registry.idByName(name, fallback);
    }

    public Set<Byte> idByExpr(String expr, byte fallback) {
        return registry.idByExpr(expr, fallback);
    }

    public boolean isSolidAt(int wx, int wy, int wz) {
        byte id = getBlock(wx, wy, wz);
        if (id == 0) return false;

        BlockDef d = registry.def(id);
        return d != null && d.solid;
    }

    public BlockDef getDefAt(int wx, int wy, int wz) {
        byte id = getBlock(wx, wy, wz);
        return registry.def(id);
    }

    public boolean isWaterAt(float wx, float wy, float wz) {
        int bx = (int) Math.floor(wx);
        int by = (int) Math.floor(wy);
        int bz = (int) Math.floor(wz);

        byte id = getBlock(bx, by, bz);
        BlockDef d = registry.def(id);
        return d != null && d.isFluid;
    }

    // ---- dispose ----
    public void dispose() {
        ioSystem.beginShutdown();
        meshSystem.shutdownStopWorkers();

        ioSystem.shutdownExecutorsGracefully();

        for (Chunk c : store.chunksMap().values()) {
            if (c == null) continue;

            if (c.dirtyBlocks && c.isReady()) {
                ioSystem.forceSaveSync(c);
            }

            c.disposeGpu();
            c.status = Chunk.Status.UNLOADED;
        }

        store.clearAll();
    }

    public void beginLightBatch() {
        lightSystem.beginBatch();
    }

    public void endLightBatch() {
        lightSystem.endBatch();
    }
}
