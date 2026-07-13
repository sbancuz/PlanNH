package com.sbancuz.plannh.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.data.flowchart.Port;

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

    /**
     * Minimum perceived luminance for derived colors. The canvas background is the world, which
     * can be nearly black at night, so dark sprite colors (e.g. ethenone's navy) are lifted to
     * stay legible while keeping their hue.
     */
    private static final float MIN_LUMINANCE = 0.42f;

    private IngredientColors() {}

    /** Representative ARGB color for the port's ingredient; {@code fallback} (with its alpha) if unknown. */
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
        return Color.withAlpha(ensureReadable(rgb), Color.getAlpha(fallback));
    }

    /**
     * Lifts a color to {@link #MIN_LUMINANCE}: first scaled up (preserves saturation), then
     * blended toward white for whatever channel clipping ate (a saturated navy can't reach the
     * target by scaling alone).
     */
    private static int ensureReadable(final int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float lum = luminance(r, g, b);
        if (lum >= MIN_LUMINANCE) return rgb;
        if (lum < 0.004f) {
            final int v = Math.round(MIN_LUMINANCE * 255);
            return v << 16 | v << 8 | v;
        }
        final float scale = MIN_LUMINANCE / lum;
        r = Math.min(1f, r * scale);
        g = Math.min(1f, g * scale);
        b = Math.min(1f, b * scale);
        lum = luminance(r, g, b);
        if (lum < MIN_LUMINANCE) {
            final float t = (MIN_LUMINANCE - lum) / (1f - lum);
            r += (1f - r) * t;
            g += (1f - g) * t;
            b += (1f - b) * t;
        }
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static float luminance(final float r, final float g, final float b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    /** Mixes the color toward white, for hover rings and highlights. */
    public static int brighten(final int color, final float factor) {
        final int r = Color.getRed(color) + Math.round((255 - Color.getRed(color)) * factor);
        final int g = Color.getGreen(color) + Math.round((255 - Color.getGreen(color)) * factor);
        final int b = Color.getBlue(color) + Math.round((255 - Color.getBlue(color)) * factor);
        return Color.argb(r, g, b, Color.getAlpha(color));
    }

    private static int ofItem(final ItemStack stack) {
        if (stack == null || stack.getItem() == null) return -1;
        final String key = "item:" + Item.itemRegistry.getNameForObject(stack.getItem()) + ":" + stack.getItemDamage();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                final Item item = stack.getItem();
                // Multi-render-pass items (all GT material items: gems, dusts, cells...) carry a
                // grayscale base sprite per pass, tinted by the per-pass color. Blend the passes
                // weighted by how many opaque pixels each contributes.
                final int passes = item.requiresMultipleRenderPasses()
                    ? Math.max(1, item.getRenderPasses(stack.getItemDamage()))
                    : 1;
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
                    final int tint = item.getColorFromItemStack(stack, pass);
                    if ((tint & 0xFFFFFF) != 0xFFFFFF) rgb = multiplyRgb(rgb, tint);
                    final long count = modal[1];
                    r += (long) (rgb >> 16 & 0xFF) * count;
                    g += (long) (rgb >> 8 & 0xFF) * count;
                    b += (long) (rgb & 0xFF) * count;
                    weight += count;
                }
                if (weight == 0) return -1;
                return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
            } catch (final Exception e) {
                return -1;
            }
        });
    }

    private static int ofFluid(final FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return -1;
        final Fluid fluid = fluidStack.getFluid();
        final String key = "fluid:" + fluid.getName();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                final long[] modal = modalSpriteColor(fluid.getIcon(fluidStack), true);
                final int tint = fluid.getColor(fluidStack);
                if (modal == null) return (tint & 0xFFFFFF) != 0xFFFFFF ? tint & 0xFFFFFF : -1;
                int rgb = (int) modal[0];
                if ((tint & 0xFFFFFF) != 0xFFFFFF) rgb = multiplyRgb(rgb, tint);
                return rgb;
            } catch (final Exception e) {
                return -1;
            }
        });
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
        final int height = Math.max(1, pixels.length / width);

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
        long[] best = null;
        for (final long[] acc : bins.values()) {
            if (best == null || acc[0] > best[0]) best = acc;
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

    private static int multiplyRgb(final int rgb, final int tint) {
        final int r = (rgb >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        final int g = (rgb >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        final int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }
}
