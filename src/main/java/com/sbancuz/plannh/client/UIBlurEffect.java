package com.sbancuz.plannh.client;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.lib.config.ConfigTag;
import codechicken.nei.NEIClientConfig;

public class UIBlurEffect extends ScreenEffect {

    private int fboId = INVALID;
    private int fboTex = INVALID;
    private int downsample = 1;
    private float sigma = 16.f;

    private int fboWidth;
    private int fboHeight;

    private final ConfigTag sigmaSetting;

    public UIBlurEffect() {
        super(GPUProgram.BLUR);

        sigmaSetting = NEIClientConfig.getSetting(NEIPlanConfig.ConfigBlurStrength.KEY);
    }

    public void updateSigma(int sigma) {
        this.sigma = (float) sigma;

        int targetDs = sigma / 8;
        if (targetDs < 1) targetDs = 1;
        if (targetDs > 4) targetDs = 4;
        if (targetDs != downsample) {
            downsample = targetDs;
            onResolutionChange();
        }
    }

    @Override
    protected void onResolutionChange() {
        fboWidth = Math.max(1, textureWidth / downsample);
        fboHeight = Math.max(1, textureHeight / downsample);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTex);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            textureWidth,
            textureHeight,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) null);
        if (fboId < 0) fboId = GL30.glGenFramebuffers();
        if (fboTex < 0) {
            fboTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboTex);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            fboWidth,
            fboHeight,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            (ByteBuffer) null);
        final int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, fboTex, 0);

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            fboId = INVALID;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
    }

    @Override
    protected void renderEffect(int width, int height) {
        if (fboId < 0) return;

        int sigma = sigmaSetting.getIntValue(NEIPlanConfig.ConfigBlurStrength.defVal);
        if (sigma <= 0) return;
        if (sigma != this.sigma) updateSigma(sigma);

        final int maxTaps = 25;
        final int maxRadius = (maxTaps - 1) * 2;
        int radius = (int) Math.ceil(sigma * 3.0);
        if (radius > maxRadius) radius = maxRadius;
        if (radius % 2 != 0) radius++;
        final int numPairs = radius / 2;

        final float[] offsets = new float[maxTaps];
        final float[] weights = new float[maxTaps];
        offsets[0] = 0.0f;
        weights[0] = 1.0f;

        final float sigmaSq = (float) sigma * sigma;
        for (int k = 0; k < numPairs; k++) {
            final int i = 2 * k + 1;
            final int j = 2 * k + 2;
            final float wi = (float) Math.exp(-(i * i) / (2.0 * sigmaSq));
            final float wj = (float) Math.exp(-(j * j) / (2.0 * sigmaSq));
            final float w = wi + wj;
            final int idx = 1 + k;
            offsets[idx] = i + wj / w;
            weights[idx] = w;
        }

        final int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);

        // --- Horizontal Pass: downsample + blur (full-res screenTex -> half-res fboTex)
        GL11.glPushAttrib(GL11.GL_VIEWPORT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL11.glViewport(0, 0, fboWidth, fboHeight);
        shader.setUniform1i("uTexture", 0);
        shader.setUniform2f("uStep", 1.0f / textureWidth, 0.0f);
        shader.setUniformArray1f("uOffsets", offsets);
        shader.setUniformArray1f("uWeights", weights);

        drawFullScreenQuad();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();

        // --- Vertical Pass: blur + upscale (half-res fboTex -> full-res screen)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        shader.setUniform2f("uStep", 0.0f, 1.0f / textureHeight);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboTex);
        drawFullQuad(width, height);
    }
}
