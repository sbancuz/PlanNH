package com.sbancuz.plannh.gui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.flowchart.Port;

import codechicken.nei.guihook.GuiContainerManager;
import gregtech.api.items.MetaGeneratedItem;
import gregtech.api.util.GTUtility;

/**
 * Derives a representative color for an ingredient from its sprite, so ports and edges can be
 * told apart by what flows through them.
 *
 * <p>
 * The color is the modal color of the sprite's opaque pixels: pixels are binned at 5 bits per
 * channel and the average color of the fullest bin wins. This tracks the dominant material color
 * (e.g. copper orange) instead of being washed out by outlines and shading the way a plain
 * average is.
 */
public final class IngredientColors {

    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static final int ALPHA_OPAQUE_MIN = 128;

    private static final int OUTLINE_DARK = 0xF00A0A0A;
    private static final int OUTLINE_LIGHT = 0xF0F2F2F2;

    private IngredientColors() {}

    /**
     * Representative ARGB color for the port's ingredient; {@code fallback} (with its alpha) if
     * unknown. Colors are true to the sprite - readability on arbitrary backgrounds comes from
     * pairing them with {@link #outlineFor(int)}.
     */
    public static int of(final Port<?> port, final int fallback) {
        final Object value = port.getValue();
        final int rgb;
        if (value instanceof final ItemStack stack) {
            rgb = ofItem(stack);
        } else if (value instanceof final FluidStack fluidStack) {
            rgb = ofFluid(fluidStack);
        } else {
            rgb = -1;
        }
        if (rgb == -1) return fallback;
        return Color.withAlpha(rgb, Color.getAlpha(fallback));
    }

    /**
     * Contrast outline for a color: light outline under dark colors, dark outline under light
     * ones. This lets derived colors keep their true luminance (brown stays brown, navy stays
     * navy) and still read on any world background.
     */
    public static int outlineFor(final int color) {
        final float lum = luminance(
            Color.getRed(color) / 255f,
            Color.getGreen(color) / 255f,
            Color.getBlue(color) / 255f);
        return lum < 0.5f ? OUTLINE_LIGHT : OUTLINE_DARK;
    }

    private static float luminance(final float r, final float g, final float b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    private static int ofItem(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return -1;
        final String key = "item:" + Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                final Item item = stack.getItem();
                // Multi-render-pass items (all GT material items: gems, dusts, cells...) carry a
                // grayscale base sprite per pass, tinted by the per-pass color. Blend the passes
                // Ground truth first: render the stack the way NEI draws it and sample the
                // pixels. This is immune to the icon API misrepresenting custom renderers
                // (GT material items, GT blocks, fluid display items...).
                final long[] rendered = sampleRenderedStack(stack);
                if (rendered != null) return logColor(k, (int) rendered[0], "render-sample");

                // weighted by how many opaque pixels each contributes.
                final int passes = item.requiresMultipleRenderPasses()
                    ? Math.max(1, item.getRenderPasses(stack.getItemDamage()))
                    : 1;
                final int gtTint = gtMaterialTint(stack);
                long r = 0, g = 0, b = 0, weight = 0;
                for (int pass = 0; pass < passes; pass++) {
                    IIcon icon;
                    try {
                        icon = item.getIcon(stack, pass);
                    } catch (final Exception e) {
                        icon = null;
                    }
                    if (icon == null && pass == 0) icon = item.getIconIndex(stack);
                    final long[] modal = modalSpriteColor(icon, item.getSpriteNumber() == 0);
                    if (modal == null) continue;
                    int rgb = (int) modal[0];
                    int tint = item.getColorFromItemStack(stack, pass);
                    // GT material items are grayscale sprites tinted by their custom renderer,
                    // not by getColorFromItemStack; apply the material color to the base pass.
                    if (pass == 0 && (tint & 0xFFFFFF) == 0xFFFFFF) tint = gtTint;
                    if ((tint & 0xFFFFFF) != 0xFFFFFF) rgb = multiplyRgb(rgb, tint);
                    final long count = modal[1];
                    r += (long) (rgb >> 16 & 0xFF) * count;
                    g += (long) (rgb >> 8 & 0xFF) * count;
                    b += (long) (rgb & 0xFF) * count;
                    weight += count;
                }
                if (weight == 0) {
                    PlanNH.LOG.debug("No sprite color derivable for {}, using type fallback", k);
                    return -1;
                }
                return logColor(k, (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight), "sprite");
            } catch (final Exception e) {
                PlanNH.LOG.debug("Sprite color derivation failed for {}: {}", k, e.toString());
                return -1;
            }
        });
    }

    private static int ofFluid(final FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return -1;
        final Fluid fluid = fluidStack.getFluid();
        final String key = "fluid:" + fluid.getName();
        // Manual get/put instead of computeIfAbsent: the display-stack path recurses into
        // ofItem, and nested computeIfAbsent on one map is illegal.
        final Integer cached = CACHE.get(key);
        if (cached != null) return cached;
        final int rgb = computeFluidColor(fluidStack, fluid, key);
        CACHE.put(key, rgb);
        return rgb;
    }

    private static final int SAMPLE_SIZE = 16;
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;

    /**
     * Renders the stack into a small offscreen framebuffer via NEI's item drawing (the exact
     * pipeline recipe screens use) and returns the modal color of the result. Returns null when
     * rendering is unavailable (headless, no FBO support, binding hijacked by render mods) so
     * callers can fall back to sprite data.
     *
     * <p>
     * Uses raw GL FBOs instead of Minecraft's Framebuffer wrapper: under Angelica the wrapper
     * can silently no-op its bind, which would make this read back whatever is currently on
     * screen. The binding is verified before anything is drawn.
     */
    private static long[] sampleRenderedStack(final ItemStack stack) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) return null;
        if (!GLContext.getCapabilities().OpenGL30) return null;

        final int previousFbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        int fbo = 0;
        int colorTexture = 0;
        int depthBuffer = 0;
        boolean stateSaved = false;
        try {
            colorTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                SAMPLE_SIZE,
                SAMPLE_SIZE,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
            depthBuffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthBuffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, SAMPLE_SIZE, SAMPLE_SIZE);

            fbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            if (GL11.glGetInteger(GL_FRAMEBUFFER_BINDING) != fbo) {
                PlanNH.LOG.debug("FBO bind did not take for {}, falling back to sprites", stack);
                return null;
            }
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                colorTexture,
                0);
            GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER,
                depthBuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                PlanNH.LOG.debug("FBO incomplete for {}, falling back to sprites", stack);
                return null;
            }

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0, SAMPLE_SIZE, SAMPLE_SIZE, 0, -1000, 3000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            stateSaved = true;

            GL11.glViewport(0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GuiContainerManager.enable2DRender();
            // The sampler is invoked mid-GUI-draw, typically right after untextured rect
            // drawing: texturing must be re-enabled or vanilla-path icons render as flat
            // white/tint quads (custom renderers that bind their own textures were unaffected).
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            // Empty quantity string suppresses the white amount text (fluid displays carry
            // one), which otherwise wins the modal vote over pale sprites.
            GuiContainerManager.drawItem(0, 0, stack, false, "");

            final ByteBuffer buffer = BufferUtils.createByteBuffer(SAMPLE_SIZE * SAMPLE_SIZE * 4);
            GL11.glReadPixels(0, 0, SAMPLE_SIZE, SAMPLE_SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            final int[] pixels = new int[SAMPLE_SIZE * SAMPLE_SIZE];
            for (int i = 0; i < pixels.length; i++) {
                final int r = buffer.get(i * 4) & 0xFF;
                final int g = buffer.get(i * 4 + 1) & 0xFF;
                final int b = buffer.get(i * 4 + 2) & 0xFF;
                final int a = buffer.get(i * 4 + 3) & 0xFF;
                pixels[i] = a << 24 | r << 16 | g << 8 | b;
            }
            final long[] result = modeWithOutlineFallback(pixels, SAMPLE_SIZE, SAMPLE_SIZE);
            if (result == null) {
                PlanNH.LOG.debug("Render sample of {} produced no opaque pixels", stack);
            }
            return result;
        } catch (final Exception e) {
            PlanNH.LOG.debug("Render sampling failed for {}: {}", stack, e.toString());
            return null;
        } finally {
            if (stateSaved) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            } catch (final Exception ignored) {}
            if (fbo != 0) GL30.glDeleteFramebuffers(fbo);
            if (depthBuffer != 0) GL30.glDeleteRenderbuffers(depthBuffer);
            if (colorTexture != 0) GL11.glDeleteTextures(colorTexture);
        }
    }

    /**
     * Chemistry-convention colors for fluids whose in-game color sources are unusable. Hydrogen
     * defines a white fluid color, its texture region on the stitched atlas is empty, its fluid
     * display item renders no pixels, and its GT material color is blue - while every player
     * knows hydrogen as red-pink.
     */
    private static final Map<String, Integer> FLUID_CONVENTION_COLORS = Map.of("hydrogen", 0xE05A5A);

    private static int computeFluidColor(final FluidStack fluidStack, final Fluid fluid, final String key) {
        try {
            final Integer convention = FLUID_CONVENTION_COLORS.get(fluid.getName());
            if (convention != null) return logColor(key, convention, "convention");

            // The fluid's own color if it defines one.
            final int tint = fluid.getColor(fluidStack);
            if ((tint & 0xFFFFFF) != 0xFFFFFF) return logColor(key, tint & 0xFFFFFF, "fluid-color");

            // Otherwise color it the way NEI displays it: GT's fluid display item carries the
            // exact icon + tint combination the player sees in recipe screens.
            if (ModularUI.Mods.GT5U.isLoaded()) {
                final ItemStack display = GTUtility.getFluidDisplayStack(fluidStack, false);
                if (display != null) {
                    final int rgb = ofItem(display);
                    if (rgb != -1) return logColor(key, rgb, "fluid-display");
                }
            }

            final long[] modal = modalSpriteColor(fluid.getIcon(fluidStack), true);
            if (modal == null) {
                PlanNH.LOG.debug("No sprite color derivable for {}, using type fallback", key);
                return -1;
            }
            return logColor(key, (int) modal[0], "fluid-sprite");
        } catch (final Exception e) {
            PlanNH.LOG.debug("Sprite color derivation failed for {}: {}", key, e.toString());
            return -1;
        }
    }

    /**
     * Modal opaque color of an atlas sprite as {@code [rgb, opaquePixelCount]}, or null if pixels
     * are unavailable.
     *
     * <p>
     * Sprite outlines are excluded first: any opaque pixel touching a transparent one is part of
     * the (usually darkened) outline ring and would otherwise win the modal vote on gradient-
     * heavy sprites like the diamond. If erosion leaves nothing (very thin sprites), the full
     * opaque set is used instead.
     */
    private static long[] modalSpriteColor(final IIcon icon, final boolean blocksAtlas) {
        if (icon == null) return null;
        // Fast path: the stitched atlas' retained CPU-side frame data. Angelica (present in all
        // modern GTNH packs and this repo's dev runtime) frees that data, so fall back to reading
        // the sprite's source PNG through the resource manager - the same file the stitcher
        // loaded it from. Items that borrow icons from the other atlas (e.g. GT's fluid display
        // uses block-atlas fluid icons) get a cross-atlas retry.
        long[] result = fromAtlasFrames(icon, blocksAtlas);
        if (result == null) result = fromResourcePng(icon, blocksAtlas);
        if (result == null) result = fromAtlasFrames(icon, !blocksAtlas);
        if (result == null) result = fromResourcePng(icon, !blocksAtlas);
        if (result == null) {
            PlanNH.LOG.debug("No pixels found for icon '{}' (blocksAtlas={})", icon.getIconName(), blocksAtlas);
        }
        return result;
    }

    private static long[] fromAtlasFrames(final IIcon icon, final boolean blocksAtlas) {
        try {
            final TextureMap atlas = (TextureMap) Minecraft.getMinecraft()
                .getTextureManager()
                .getTexture(blocksAtlas ? TextureMap.locationBlocksTexture : TextureMap.locationItemsTexture);
            if (atlas == null) return null;
            final TextureAtlasSprite sprite = atlas.getAtlasSprite(icon.getIconName());
            if (sprite == null || sprite.getFrameCount() <= 0) return null;
            final int[][] frame = sprite.getFrameTextureData(0);
            if (frame == null || frame.length == 0 || frame[0] == null) return null;

            final int[] pixels = frame[0];
            final int width = Math.max(1, sprite.getIconWidth());
            return modeWithOutlineFallback(pixels, width, Math.max(1, pixels.length / width));
        } catch (final Exception e) {
            return null;
        }
    }

    private static long[] fromResourcePng(final IIcon icon, final boolean blocksAtlas) {
        final String name = icon.getIconName();
        if (name == null || name.isEmpty()) return null;
        final int colon = name.indexOf(':');
        final String domain = colon >= 0 ? name.substring(0, colon) : "minecraft";
        final String path = colon >= 0 ? name.substring(colon + 1) : name;
        final ResourceLocation location = new ResourceLocation(
            domain,
            "textures/" + (blocksAtlas ? "blocks" : "items") + "/" + path + ".png");
        try (InputStream in = Minecraft.getMinecraft()
            .getResourceManager()
            .getResource(location)
            .getInputStream()) {
            final BufferedImage image = ImageIO.read(in);
            if (image == null) {
                PlanNH.LOG.debug("PNG decoded null for icon '{}' at {}", name, location);
                return null;
            }
            final int width = image.getWidth();
            // Animated sprites are vertical strips; sample the first frame only.
            final int height = Math.min(image.getHeight(), width);
            final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            return modeWithOutlineFallback(pixels, width, height);
        } catch (final Exception e) {
            PlanNH.LOG.debug("PNG read failed for icon '{}' at {}: {}", name, location, e.toString());
            return null;
        }
    }

    private static long[] modeWithOutlineFallback(final int[] pixels, final int width, final int height) {
        long[] result = histogramMode(pixels, width, height, true);
        if (result == null) result = histogramMode(pixels, width, height, false);
        return result;
    }

    /** Bin at 5 bits/channel; per-bin sums let the winning bin be averaged smoothly. */
    private static long[] histogramMode(final int[] pixels, final int width, final int height,
        final boolean erodeOutline) {
        final Map<Integer, long[]> bins = new HashMap<>();
        long opaque = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = pixels[y * width + x];
                if ((argb >>> 24) < ALPHA_OPAQUE_MIN) continue;
                if (erodeOutline && touchesTransparency(pixels, width, height, x, y)) continue;
                opaque++;
                final int r = argb >> 16 & 0xFF;
                final int g = argb >> 8 & 0xFF;
                final int b = argb & 0xFF;
                final int bin = (r >> 3) << 10 | (g >> 3) << 5 | b >> 3;
                final long[] acc = bins.computeIfAbsent(bin, x2 -> new long[4]);
                acc[0]++;
                acc[1] += r;
                acc[2] += g;
                acc[3] += b;
            }
        }
        // Saturation-weighted mode: pale sprites (diamond, gases) fragment their gradient over
        // many near-white bins while pure white concentrates in one, so plain counts pick white.
        // Weighting by saturation prefers the sprite's characteristic hue; for genuinely
        // achromatic items (salt, dusts) all bins score equally and the biggest still wins.
        long[] best = null;
        double bestScore = -1;
        for (final long[] acc : bins.values()) {
            final double r = (double) acc[1] / acc[0];
            final double g = (double) acc[2] / acc[0];
            final double b = (double) acc[3] / acc[0];
            final double max = Math.max(r, Math.max(g, b));
            final double saturation = max <= 0 ? 0 : (max - Math.min(r, Math.min(g, b))) / max;
            final double score = acc[0] * (0.35 + saturation);
            if (score > bestScore) {
                bestScore = score;
                best = acc;
            }
        }
        if (best == null) return null;
        final int rgb = (int) (best[1] / best[0]) << 16 | (int) (best[2] / best[0]) << 8 | (int) (best[3] / best[0]);
        return new long[] { rgb, opaque };
    }

    /** True if a 4-neighbor is transparent; out-of-bounds neighbors count as opaque. */
    private static boolean touchesTransparency(final int[] pixels, final int width, final int height, final int x,
        final int y) {
        return isTransparent(pixels, width, height, x - 1, y) || isTransparent(pixels, width, height, x + 1, y)
            || isTransparent(pixels, width, height, x, y - 1)
            || isTransparent(pixels, width, height, x, y + 1);
    }

    private static boolean isTransparent(final int[] pixels, final int width, final int height, final int x,
        final int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        return (pixels[y * width + x] >>> 24) < ALPHA_OPAQUE_MIN;
    }

    private static int logColor(final String key, final int rgb, final String source) {
        PlanNH.LOG.debug("Ingredient color {} -> #{} via {}", key, String.format("%06X", rgb), source);
        return rgb;
    }

    private static int multiplyRgb(final int rgb, final int tint) {
        final int r = (rgb >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        final int g = (rgb >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        final int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    /** GT material color for MetaGeneratedItems (their renderer applies it, so the item API lies). */
    private static int gtMaterialTint(final ItemStack stack) {
        if (!ModularUI.Mods.GT5U.isLoaded()) return 0xFFFFFF;
        if (stack.getItem() instanceof final MetaGeneratedItem gtItem) {
            final short[] rgba = gtItem.getRGBa(stack);
            if (rgba != null && rgba.length >= 3) {
                return (rgba[0] & 0xFF) << 16 | (rgba[1] & 0xFF) << 8 | (rgba[2] & 0xFF);
            }
        }
        return 0xFFFFFF;
    }
}
