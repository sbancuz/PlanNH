package com.sbancuz.plannh.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.gui.CanvasWidget;

public enum GPUProgram {

    BLUR("/assets/plannh/shaders/gaussian_blur.vert", "/assets/plannh/shaders/gaussian_blur.frag");

    private final static int INVALID = -1;

    final private String vertexPath;
    final private String fragmentPath;

    private int program = INVALID;
    private boolean loaded = false;

    private final Map<String, Integer> uniformCache = new HashMap<>();
    private FloatBuffer uniformBuf;

    GPUProgram(String vertex, String fragment) {
        this.vertexPath = vertex;
        this.fragmentPath = fragment;
    }

    public boolean compile() {
        if (program != INVALID) return loaded;

        final String vertexSrc = loadShaderSource(vertexPath);
        final String fragmentSrc = loadShaderSource(fragmentPath);
        if (vertexSrc == null || fragmentSrc == null) return false;

        int vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSrc);
        if (vertex == INVALID) return false;
        int fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSrc);
        if (fragment == INVALID) return false;

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);

        GL20.glLinkProgram(program);

        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            final String log = GL20.glGetProgramInfoLog(program, 4096);
            PlanNH.LOG.error("Shader link error: {}", log);
            GL20.glDeleteProgram(program);
            program = INVALID;
            return false;
        }

        loaded = true;
        return true;
    }

    public void bind() {
        if (loaded) GL20.glUseProgram(program);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getUniform(final String name) {
        if (!uniformCache.containsKey(name)) {
            uniformCache.put(name, GL20.glGetUniformLocation(program, name));
        }
        return uniformCache.get(name);
    }

    public void setUniform1i(final String name, final int v) {
        GL20.glUniform1i(getUniform(name), v);
    }

    public void setUniform1f(final String name, final float v) {
        GL20.glUniform1f(getUniform(name), v);
    }

    public void setUniform2f(final String name, final float v1, final float v2) {
        GL20.glUniform2f(getUniform(name), v1, v2);
    }

    public void setUniformArray1f(final String name, final float[] v) {
        final int base = getUniform(name);
        if (uniformBuf == null || uniformBuf.capacity() < v.length) {
            uniformBuf = BufferUtils.createFloatBuffer(v.length);
        }
        uniformBuf.clear();
        uniformBuf.put(v);
        uniformBuf.flip();
        GL20.glUniform1(base, uniformBuf);
    }

    private static @Nullable String loadShaderSource(final String path) {
        final InputStream in = CanvasWidget.class.getResourceAsStream(path);
        if (in == null) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            final StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line)
                    .append('\n');
            }
            return sb.toString();
        } catch (final IOException e) {
            return null;
        }
    }

    private static int compileShader(final int type, final String src) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            final String log = GL20.glGetShaderInfoLog(shader, 4096);
            PlanNH.LOG.error("Shader error: {}", log);
            GL20.glDeleteShader(shader);
            return INVALID;
        }
        return shader;
    }
}
