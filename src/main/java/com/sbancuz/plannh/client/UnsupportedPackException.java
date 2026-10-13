package com.sbancuz.plannh.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;

import com.google.common.collect.ImmutableMap;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.Tags;

import cpw.mods.fml.client.CustomModLoadingErrorDisplayException;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.versioning.ComparableVersion;

/**
 * Halts loading on a pack older than PlanNH supports. Below the GregTech minimum, the required GTNEIDefaultHandler
 * mixin fails during GregTech's init and the game freezes on the loading screen. English only: the text doubles as the
 * log message, built before lang files load.
 */
public class UnsupportedPackException extends CustomModLoadingErrorDisplayException {

    private static final String HEADER = "PlanNH " + Tags.VERSION
        + " does not support this version of GT New Horizons.";
    private static final String FOOTER = "Remove PlanNH from the mods folder, or update to GT New Horizons 2.9.0-RC-2 or newer.";

    /** Oldest supported version of each mod, keyed by mod id. */
    private static final Map<String, String> MINIMUM_VERSIONS = ImmutableMap.<String, String>builder()
        // first version with GTNEIDefaultHandler.drawDescription(CachedDefaultRecipe, int)
        .put(Compat.IDs.GREGTECH, "5.09.54.192")
        // first version with RecipeWidget.getCyclingStacks
        .put("NotEnoughItems", "2.8.148-GTNH")
        // first versions with every class PlanNH references. Methods are unchecked
        .put("modularui2", "2.3.55")
        .put("gtnhlib", "0.11.5")
        .build();

    private final List<String> lines;
    private final List<String> wrapped = new ArrayList<>();

    private UnsupportedPackException(final List<String> lines) {
        super(String.join("\n", lines), null);
        this.lines = lines;
    }

    public static void throwIfUnsupported() {
        final List<String> outdated = outdated(
            Loader.instance()
                .getIndexedModList());
        if (outdated.isEmpty()) return;

        final List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("");
        lines.addAll(outdated);
        lines.add("");
        lines.add(FOOTER);
        throw new UnsupportedPackException(lines);
    }

    public static List<String> outdated(final Map<String, ModContainer> mods) {
        final List<String> outdated = new ArrayList<>();
        for (final Map.Entry<String, String> minimum : MINIMUM_VERSIONS.entrySet()) {
            final ModContainer mod = mods.get(minimum.getKey());
            if (mod == null || mod.getDisplayVersion() == null) continue;
            // getDisplayVersion() is the mcmod.info version. GregTech's getVersion() is the constant MC1710
            if (new ComparableVersion(mod.getDisplayVersion()).compareTo(new ComparableVersion(minimum.getValue()))
                < 0) {
                outdated.add(mod.getName() + " " + mod.getDisplayVersion() + " is older than " + minimum.getValue());
            }
        }
        return outdated;
    }

    @Override
    public void initGui(final GuiErrorScreen errorScreen, final FontRenderer fontRenderer) {
        final int textMargin = 20;
        wrapped.clear();
        for (final String line : lines) {
            wrapped.addAll(fontRenderer.listFormattedStringToWidth(line, errorScreen.width - textMargin));
        }
    }

    @Override
    public void drawScreen(final GuiErrorScreen errorScreen, final FontRenderer fontRenderer, final int mouseRelX,
        final int mouseRelY, final float tickTime) {
        // PlannhColors loads GTNHLib's ColorResource. GTNHLib before 0.11.5 lacks it, so use a literal colour
        int y = (errorScreen.height - wrapped.size() * fontRenderer.FONT_HEIGHT) / 2;
        for (final String line : wrapped) {
            errorScreen.drawCenteredString(fontRenderer, line, errorScreen.width / 2, y, 0xFFFFFF);
            y += fontRenderer.FONT_HEIGHT;
        }
    }
}
