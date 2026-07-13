package com.sbancuz.plannh.gui;

import java.awt.image.BufferedImage;
import java.io.InputStream;
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

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.utils.Color;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.flowchart.Port;

import gregtech.api.enums.Materials;
import gregtech.api.items.MetaGeneratedItem;

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
                return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
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
        return CACHE.computeIfAbsent(key, k -> {
            try {
                // The fluid's own color if it defines one, else GT's material color (fluids like
                // hydrogen are registered by IC2 with a white color and a generic gas texture -
                // the GT material knows it should be red).
                int tint = fluid.getColor(fluidStack);
                if ((tint & 0xFFFFFF) == 0xFFFFFF) tint = gtFluidMaterialTint(fluid);
                if ((tint & 0xFFFFFF) != 0xFFFFFF) return tint & 0xFFFFFF;

                final long[] modal = modalSpriteColor(fluid.getIcon(fluidStack), true);
                if (modal == null) {
                    PlanNH.LOG.debug("No sprite color derivable for {}, using type fallback", k);
                    return -1;
                }
                return (int) modal[0];
            } catch (final Exception e) {
                PlanNH.LOG.debug("Sprite color derivation failed for {}: {}", k, e.toString());
                return -1;
            }
        });
    }

    /** GT material color for a fluid, resolved through GT's fluid-to-material map. */
    private static int gtFluidMaterialTint(final Fluid fluid) {
        if (!ModularUI.Mods.GT5U.isLoaded()) return 0xFFFFFF;
        final Materials material = Materials.FLUID_MAP.get(fluid);
        if (material != null && material.mRGBa != null && material.mRGBa.length >= 3) {
            return (material.mRGBa[0] & 0xFF) << 16 | (material.mRGBa[1] & 0xFF) << 8 | (material.mRGBa[2] & 0xFF);
        }
        return 0xFFFFFF;
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
        // loaded it from.
        long[] result = fromAtlasFrames(icon, blocksAtlas);
        if (result == null) result = fromResourcePng(icon, blocksAtlas);
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
            if (image == null) return null;
            final int width = image.getWidth();
            // Animated sprites are vertical strips; sample the first frame only.
            final int height = Math.min(image.getHeight(), width);
            final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            return modeWithOutlineFallback(pixels, width, height);
        } catch (final Exception e) {
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
