package com.atom.life.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.atom.life.world.VoxelRaycaster;
import com.atom.life.world.World;

import static com.atom.life.GlobalVariables.reach;

public class BlockOutlineRenderer {

    private final Mesh mesh;
    private final ShaderProgram shader;

    private final Matrix4 worldMat = new Matrix4();
    private final Vector3 tmpDir = new Vector3();

    private final VoxelRaycaster.Hit hit = new VoxelRaycaster.Hit();

    private boolean hasTarget = false;
    private int tx, ty, tz;

    // GLSL 150 (GL3 core)
    private static final String VERT_150 =
        "#version 150\n" +
            "in vec3 a_position;\n" +
            "uniform mat4 u_projView;\n" +
            "uniform mat4 u_world;\n" +
            "void main(){\n" +
            "  gl_Position = u_projView * u_world * vec4(a_position, 1.0);\n" +
            "}\n";

    private static final String FRAG_150 =
        "#version 150\n" +
            "uniform vec4 u_color;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  fragColor = u_color;\n" +
            "}\n";

    public BlockOutlineRenderer() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(VERT_150, FRAG_150);
        if (!shader.isCompiled()) {
            throw new IllegalStateException("BlockOutline shader compile error:\n" + shader.getLog());
        }

        // 8 corners, 12 edges => 24 indices
        mesh = new Mesh(true, 8, 24,
            new VertexAttributes(
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position")
            )
        );

        float[] verts = new float[] {
            0,0,0,   // 0
            1,0,0,   // 1
            1,1,0,   // 2
            0,1,0,   // 3
            0,0,1,   // 4
            1,0,1,   // 5
            1,1,1,   // 6
            0,1,1    // 7
        };

        short[] inds = new short[] {
            // bottom
            0,1, 1,2, 2,3, 3,0,
            // top
            4,5, 5,6, 6,7, 7,4,
            // vertical
            0,4, 1,5, 2,6, 3,7
        };

        mesh.setVertices(verts);
        mesh.setIndices(inds);
    }

    public void updateTarget(PerspectiveCamera cam, World world) {
        if (world == null || cam == null) {
            hasTarget = false;
            return;
        }
        tmpDir.set(cam.direction).nor();
        hasTarget = VoxelRaycaster.raycast(world, cam.position, tmpDir, reach, hit);
        if (hasTarget) {
            tx = hit.x; ty = hit.y; tz = hit.z;
        }
    }

    public void render(PerspectiveCamera camera) {
        if (!hasTarget) return;

        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        Gdx.gl.glLineWidth(1f);

        worldMat.idt()
            .translate(tx, ty, tz)
            .translate(0.5f, 0.5f, 0.5f)
            .scale(1.01f, 1.01f, 1.01f)
            .translate(-0.5f, -0.5f, -0.5f);

        shader.bind();
        shader.setUniformMatrix("u_projView", camera.combined);
        shader.setUniformMatrix("u_world", worldMat);

        shader.setUniformf("u_color", 1f, 1f, 1f, 0.8f);

        mesh.render(shader, GL20.GL_LINES);

        Gdx.gl.glDepthMask(true);
    }

    public void dispose() {
        mesh.dispose();
        shader.dispose();
    }
}
