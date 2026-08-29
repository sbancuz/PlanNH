package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.annotation.Versioned;
import com.sbancuz.plannh.annotation.VersionedInjector;

import cpw.mods.fml.common.discovery.ASMDataTable;

/**
 * The sweep over FML's annotation table, which is how the injector is actually entered in a game.
 *
 * <p>
 * The branch worth pinning is the one that skips a provider whose mod is absent. A provider names its
 * mod's types throughout, so on a pack without that mod loading it is what fails, and it fails with a
 * {@code LinkageError} during preInit.
 *
 * <p>
 * A holder records in a static initializer that it was loaded at all, and the tests read that. Class
 * literals do not trigger initialization (JLS 12.4.1), so naming a holder to build the table is safe;
 * only the injector loading it can set the flag.
 */
class VersionedAsmSweepTest {

    /**
     * Set from a holder's static initializer, which is the only way to see that it was loaded at all.
     * One-way on purpose: a class initializes once per JVM, so this cannot be reset between tests and
     * is only sound for a holder no test ever expects to be loaded.
     */
    static final class Loaded {

        static boolean absent;
    }

    /** Stands in for an installed mod's class. */
    static final class Dependency {

        static int SPEED = 42;
    }

    private static final String DEPENDENCY = "com.sbancuz.plannh.VersionedAsmSweepTest$Dependency";

    @Versioned.Mod(modId = "presentmod", sinceVersion = "1.0")
    static final class PresentHolder {

        @Versioned.Class(DEPENDENCY)
        static class Mirror {

            static int SPEED = 1;
        }
    }

    @Versioned.Mod(modId = "absentmod", sinceVersion = "1.0")
    static final class AbsentHolder {

        static {
            Loaded.absent = true;
        }

        @Versioned.Class(DEPENDENCY)
        static class Mirror {

            static int SPEED = 1;
        }
    }

    private static ASMDataTable tableNaming(final String... holderClassNames) {
        final ASMDataTable table = new ASMDataTable();
        for (final String holder : holderClassNames) {
            final String modId = holder.endsWith("AbsentHolder") ? "absentmod" : "presentmod";
            table.addASMData(null, Versioned.Mod.class.getName(), holder, null, Map.of("modId", modId));
        }
        return table;
    }

    /**
     * Injection is what the positive cases assert, rather than a load flag: a class initializes once,
     * so a flag set by the first test that touches a holder can never be cleared for the next one.
     */
    @BeforeEach
    void resetMirrors() {
        PresentHolder.Mirror.SPEED = 1;
    }

    /** The mod is installed, so its provider is loaded and its mirrors filled from the table entry. */
    @Test
    void aPresentModsProviderIsInjected() {
        VersionedInjector.injectAll(tableNaming(PresentHolder.class.getName()), modId -> true);

        assertEquals(Dependency.SPEED, PresentHolder.Mirror.SPEED);
    }

    /**
     * The one that matters: an absent mod's provider must not be touched at all. Loading it is the
     * failure, so asserting on its mirror would be too late - the flag says whether it was loaded.
     */
    @Test
    void anAbsentModsProviderIsNeverLoaded() {
        VersionedInjector.injectAll(tableNaming(AbsentHolder.class.getName()), modId -> false);

        assertFalse(Loaded.absent, "a provider was classloaded for a mod that is not installed");
        // Reading the mirror initializes the mirror, never the holder around it, so this is safe to
        // ask and says the skip happened before any field was written too.
        assertEquals(1, AbsentHolder.Mirror.SPEED);
    }

    /** One absent mod must not stop the providers either side of it from being filled in. */
    @Test
    void anAbsentModDoesNotStopTheRest() {
        VersionedInjector.injectAll(
            tableNaming(AbsentHolder.class.getName(), PresentHolder.class.getName()),
            modId -> !"absentmod".equals(modId));

        assertFalse(Loaded.absent);
        assertEquals(Dependency.SPEED, PresentHolder.Mirror.SPEED);
    }

    /**
     * A holder the jar no longer has. FML's table is built from bytes on disk, so a stale entry is
     * possible, and it must cost that entry rather than the launch.
     */
    @Test
    void aHolderThatIsNotOnTheClasspathIsSurvivable() {
        VersionedInjector.injectAll(tableNaming("com.sbancuz.plannh.NoSuchProvider"), modId -> true);
    }

    /** An entry with no modId recorded is swept rather than skipped; there is nothing to check it against. */
    @Test
    void anEntryWithoutAModIdIsStillSwept() {
        final ASMDataTable table = new ASMDataTable();
        table.addASMData(null, Versioned.Mod.class.getName(), PresentHolder.class.getName(), null, Map.of());

        VersionedInjector.injectAll(table, modId -> false);

        assertEquals(Dependency.SPEED, PresentHolder.Mirror.SPEED);
    }
}
