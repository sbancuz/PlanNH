package com.sbancuz.plannh.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.Compat;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;

/**
 * Version strings as the packs report them. The suffixes ({@code -GTNH}, {@code -1.7.10}) are where a comparison can
 * go wrong, so each boundary is checked against its neighbour release.
 */
class UnsupportedPackTest {

    @Test
    void gtnh284IsRejectedOnEveryLibrary() {
        final List<String> outdated = UnsupportedPackException
            .outdated(pack("5.09.51.482", "2.8.44-GTNH", "2.2.18-1.7.10", "0.7.10"));
        assertEquals(4, outdated.size(), outdated.toString());
    }

    @Test
    void gtnh290Rc2IsAccepted() {
        final List<String> outdated = UnsupportedPackException
            .outdated(pack("5.09.54.205", "2.8.155-GTNH", "2.3.92-1.7.10", "0.11.52"));
        assertTrue(outdated.isEmpty(), outdated.toString());
    }

    @Test
    void gtnh290Rc1IsRejectedOnGregTechAndNei() {
        // RC-1 predates both APIs the required mixins inject into
        final List<String> outdated = UnsupportedPackException
            .outdated(pack("5.09.54.183", "2.8.145-GTNH", "2.3.91-1.7.10", "0.11.51"));
        assertEquals(2, outdated.size(), outdated.toString());
    }

    @Test
    void eachMinimumIsTheFirstSupportedRelease() {
        assertTrue(
            UnsupportedPackException.outdated(pack("5.09.54.192", "2.8.148-GTNH", "2.3.55-1.7.10", "0.11.5"))
                .isEmpty());
        assertEquals(
            4,
            UnsupportedPackException.outdated(pack("5.09.54.191", "2.8.147-GTNH", "2.3.54-1.7.10", "0.11.4"))
                .size());
    }

    @Test
    void absentModsAreNotReported() {
        // GregTech is optional, so a pack without it is judged on the other libraries alone
        final Map<String, ModContainer> mods = pack("5.09.51.482", "2.8.155-GTNH", "2.3.92-1.7.10", "0.11.52");
        mods.remove(Compat.IDs.GREGTECH);
        assertTrue(
            UnsupportedPackException.outdated(mods)
                .isEmpty());
    }

    private static Map<String, ModContainer> pack(final String gregtech, final String nei, final String modularui2,
        final String gtnhlib) {
        final Map<String, ModContainer> mods = new HashMap<>();
        for (final String[] mod : new String[][] { { Compat.IDs.GREGTECH, gregtech }, { "NotEnoughItems", nei },
            { "modularui2", modularui2 }, { "gtnhlib", gtnhlib } }) {
            final ModMetadata metadata = new ModMetadata();
            metadata.modId = mod[0];
            metadata.name = mod[0];
            metadata.version = mod[1];
            mods.put(mod[0], new DummyModContainer(metadata));
        }
        return mods;
    }
}
