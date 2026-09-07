package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A screen that names a GregTech class fails to open on a pack that does not have GregTech, and
 * PlanNH's own screen is the one thing every pack sees. The rule is the GUI's, not the build's: it
 * holds however GregTech happens to be scoped in {@code dependencies.gradle} on any given day. This
 * is a source check rather than a runtime one because the failure is a {@code NoClassDefFoundError}
 * raised by classloading, which no headless test can provoke while the jar is on the test classpath.
 *
 * <p>
 * An allowlist rather than a list of banned mods: the next mod integration should have to say out
 * loud that it is reaching into the GUI, instead of being caught only when someone runs the pack
 * without it. PlanNH's own {@code data.provider.gregtech} classes are allowed here - that is where
 * mod references are supposed to live, behind a {@code Compat.GREGTECH.isLoaded} guard, which is what
 * {@code GTHooks} exists for.
 */
class GuiModIndependenceTest {

    private static final Path GUI = Path.of("src/main/java/com/sbancuz/plannh/gui");

    /** Everything the GUI is allowed to depend on: the JDK, Minecraft, Forge, and hard dependencies. */
    private static final Set<String> ALLOWED_ROOTS = Set.of(
        "java",
        "javax",
        "org.lwjgl",
        "org.jetbrains",
        "org.apache",
        "net.minecraft",
        "net.minecraftforge",
        "com.google",
        "com.cleanroommc",
        "com.gtnewhorizon",
        "com.sbancuz",
        "codechicken",
        "lombok",
        "it.unimi.dsi");

    @Test
    void noGuiClassNamesAModPackage() {
        assertTrue(
            Files.isDirectory(GUI),
            "the gui sources are not where this test expects them: " + GUI.toAbsolutePath());

        final List<String> offenders = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(GUI)) {
            for (final Path source : sources.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .toList()) {
                for (final String line : Files.readAllLines(source)) {
                    if (!line.startsWith("import ")) continue;
                    final String imported = line.substring("import ".length())
                        .replaceFirst("^static ", "");
                    if (ALLOWED_ROOTS.stream()
                        .noneMatch(root -> imported.startsWith(root + "."))) {
                        offenders.add(source.getFileName() + ": " + line.trim());
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        assertEquals(
            List.of(),
            offenders,
            "the GUI must open on a pack without these mods; put the reference behind a Compat guard");
    }
}
