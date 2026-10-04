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
 * GregTech is a compile-only dependency, so a screen class referencing a GregTech class fails to open on a pack
 * without it, and PlanNH's screen opens in every pack. The check reads source because the failure is a
 * {@code NoClassDefFoundError} raised by classloading, which no headless test can provoke while the jar is on the test
 * classpath.
 *
 * <p>
 * With a list of banned mods, the next mod integration would go unnoticed until someone runs a pack without that mod,
 * so this is an allowlist. PlanNH's {@code data.provider.gregtech} classes are allowed: mod references belong there,
 * reached through {@code GTHooks} behind a {@code Compat.GREGTECH.isLoaded} guard.
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
