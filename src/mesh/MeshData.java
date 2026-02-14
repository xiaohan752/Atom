package com.atom.life.mesh;

public class MeshData {
    public float[] vertices;
    public short[] indices;

    public int verticesLength; // number of floats used
    public int vertexCount;    // number of vertices (verticesLength / strideFloats)
    public int indexCount;

    public MeshData(float[] vertices, int verticesLength, short[] indices, int indexCount, int strideFloats) {
        this.vertices = vertices;
        this.verticesLength = verticesLength;
        this.indices = indices;
        this.indexCount = indexCount;
        this.vertexCount = verticesLength / strideFloats;
    }
}
