package com.atom.life.world;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.atom.life.mesh.MeshData;

import java.util.concurrent.atomic.AtomicBoolean;

public class Chunk {

    public static final int SX = 16;
    public static final int SY = 256;
    public static final int SZ = 16;

    public enum Status { LOADING, READY, UNLOADED }

    public final int cx, cz;

    public final byte[] blocks = new byte[SX * SY * SZ];

    public volatile boolean dirtyBlocks = false;
    public volatile boolean dirtyMesh = true;

    public volatile boolean meshBuilding = false;
    public volatile Status status = Status.LOADING;

    public Mesh meshOpaque;
    public int indexCountOpaque = 0;

    public Mesh meshAlpha;
    public int indexCountAlpha = 0;

    public volatile int meshRevision = 0;
    public final AtomicBoolean remeshQueued = new AtomicBoolean(false);

    public volatile int saveRevision = 0;
    public volatile int savedRevision = 0;
    public final AtomicBoolean saveQueued = new AtomicBoolean(false);

    public volatile long lastDirtyTimeMs = 0;

    public final byte[] blockLight = new byte[Chunk.SX * Chunk.SY * Chunk.SZ];

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public int idx(int x, int y, int z) {
        return (y * SZ + z) * SX + x;
    }

    public byte getLocal(int x, int y, int z) {
        return blocks[idx(x, y, z)];
    }

    public void setLocal(int x, int y, int z, byte id) {
        blocks[idx(x, y, z)] = id;

        dirtyBlocks = true;
        lastDirtyTimeMs = System.currentTimeMillis();
        saveRevision++;

        dirtyMesh = true;
        meshRevision++;
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    //Apply mesh data (render thread only)
    public void applyMeshDataOpaque(MeshData md, VertexAttributes attrs) {
        if (status != Status.READY) return;

        if (md == null || md.indexCount == 0 || md.vertexCount == 0) {
            if (meshOpaque != null) {
                meshOpaque.dispose();
                meshOpaque = null;
            }
            indexCountOpaque = 0;
            return;
        }

        meshOpaque = apply(meshOpaque, md, attrs);
        indexCountOpaque = md.indexCount;
    }

    public void applyMeshDataAlpha(MeshData md, VertexAttributes attrs) {
        if (status != Status.READY) return;

        if (md == null || md.indexCount == 0 || md.vertexCount == 0) {
            if (meshAlpha != null) {
                meshAlpha.dispose();
                meshAlpha = null;
            }
            indexCountAlpha = 0;
            return;
        }

        meshAlpha = apply(meshAlpha, md, attrs);
        indexCountAlpha = md.indexCount;
    }

    private Mesh apply(Mesh mesh, MeshData md, VertexAttributes attrs) {
        if (md == null || md.vertexCount <= 0 || md.indexCount <= 0 || md.verticesLength <= 0) {
            if (mesh != null) mesh.dispose();
            return null;
        }

        if (mesh == null) {
            mesh = new Mesh(true, md.vertexCount, md.indexCount, attrs);
        } else {
            if (mesh.getMaxVertices() < md.vertexCount || mesh.getMaxIndices() < md.indexCount) {
                mesh.dispose();
                mesh = new Mesh(true, md.vertexCount, md.indexCount, attrs);
            }
        }

        mesh.setVertices(md.vertices, 0, md.verticesLength);
        mesh.setIndices(md.indices, 0, md.indexCount);
        return mesh;
    }

    //Render
    public void renderOpaque(ShaderProgram shader) {
        if (meshOpaque == null || indexCountOpaque == 0) return;
        meshOpaque.render(shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, 0, indexCountOpaque);
    }

    public void renderAlpha(ShaderProgram shader) {
        if (meshAlpha == null || indexCountAlpha == 0) return;
        meshAlpha.render(shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, 0, indexCountAlpha);
    }

    // Render thread
    public void disposeGpu() {
        if (meshOpaque != null) {
            meshOpaque.dispose();
            meshOpaque = null;
        }
        if (meshAlpha != null) {
            meshAlpha.dispose();
            meshAlpha = null;
        }
        indexCountOpaque = 0;
        indexCountAlpha = 0;
    }

    public byte getLightLocal(int x, int y, int z) {
        return blockLight[idx(x, y, z)];
    }

    public boolean setLightLocal(int x, int y, int z, byte level) {
        int i = idx(x, y, z);
        byte prev = blockLight[i];
        if (prev == level) return false;

        blockLight[i] = level;

        // lighting affects rendering but should NOT affect saving blocks
        dirtyMesh = true;
        meshRevision++;
        return true;
    }
}
