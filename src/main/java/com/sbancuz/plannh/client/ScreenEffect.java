package com.sbancuz.plannh.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

public abstract class ScreenEffect {

    protected final static int INVALID = -1;

    protected final GPUProgram shader;

    private int prevDisplayWidth = INVALID;
    private int prevDisplayHeight = INVALID;

    protected int scaleFactor;
    protected int textureWidth;
    protected int textureHeight;

    protected int screenTex = INVALID;

    public ScreenEffect(GPUProgram shader) {
        this.shader = shader;
    }

    public void execute(final int x, final int y, final int width, final int height) {
        if (!shader.compile()) return;
        if (width <= 0 || height <= 0) return;

        final Minecraft mc = Minecraft.getMinecraft();
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int sf = sr.getScaleFactor();
        final int newTexW = width * sf;
        final int newTexH = height * sf;

        final boolean resChanged = mc.displayWidth != prevDisplayWidth || mc.displayHeight != prevDisplayHeight;
        final boolean sizeChanged = newTexW != textureWidth || newTexH != textureHeight;

        // Ensure screen capture texture exists BEFORE onResolutionChange
        if (screenTex < 0) {
            screenTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        if (resChanged || sizeChanged) {
            prevDisplayWidth = mc.displayWidth;
            prevDisplayHeight = mc.displayHeight;
            scaleFactor = sf;
            textureWidth = newTexW;
            textureHeight = newTexH;
            onResolutionChange();
        }

        final int screenX = x * scaleFactor;
        final int screenY = mc.displayHeight - (y + height) * scaleFactor;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, screenX, screenY, textureWidth, textureHeight);

        final int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        shader.bind();
        renderEffect(width, height);
        shader.unbind();

        GL20.glUseProgram(prevProgram);
    }

    protected abstract void onResolutionChange();

    protected abstract void renderEffect(final int width, final int height);

    protected void drawFullQuad(final int w, final int h) {
        final ByteBuffer verts = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder());
        verts.asFloatBuffer()
            .put(new float[] { 0, 0, w, 0, w, h, 0, h });
        verts.rewind();

        final ByteBuffer texs = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder());
        texs.asFloatBuffer()
            .put(new float[] { 0, 1, 1, 1, 1, 0, 0, 0 });
        texs.rewind();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glVertexPointer(2, GL11.GL_FLOAT, 0, verts);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, texs);
        GL11.glDrawArrays(GL11.GL_QUADS, 0, 4);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    protected void drawFullScreenQuad() {
        final ByteBuffer verts = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder());
        verts.asFloatBuffer()
            .put(new float[] { -1, -1, 1, -1, 1, 1, -1, 1 });
        verts.rewind();

        final ByteBuffer texs = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder());
        texs.asFloatBuffer()
            .put(new float[] { 0, 0, 1, 0, 1, 1, 0, 1 });
        texs.rewind();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glVertexPointer(2, GL11.GL_FLOAT, 0, verts);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, texs);
        GL11.glDrawArrays(GL11.GL_QUADS, 0, 4);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
}
