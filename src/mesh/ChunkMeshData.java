package com.atom.life.mesh;

public class ChunkMeshData {
    public final MeshData opaque;
    public final MeshData alpha;

    public ChunkMeshData(MeshData opaque, MeshData alpha) {
        this.opaque = opaque;
        this.alpha = alpha;
    }
}
