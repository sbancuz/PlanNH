package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * A {@code @Versioned.Class} mirror must not declare final fields.
 *
 * <p>
 * {@code static final int X = 100} is a JLS 4.12.4 constant variable: javac writes 100 into every use
 * site and the field is never read again, so injecting into it changes nothing at all.
 *
 * <p>
 * Checked in the source rather than by reflection because reflection cannot see the difference: a
 * final field with an injected value and a final field the compiler folded look identical from the
 * outside. The declaration is the only place the distinction is visible.
 */
class VersionedMirrorShapeTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final String MIRROR = "@Versioned.Class";

    @Test
    void noVersionedMirrorDeclaresAFinalField() {
        assertTrue(Files.isDirectory(MAIN), "sources are not where this test expects them: " + MAIN.toAbsolutePath());

        final List<String> offenders = new ArrayList<>();
        for (final Path source : javaSources()) {
            offenders.addAll(finalFieldsInMirrors(source));
        }

        assertEquals(
            List.of(),
            offenders,
            "a final mirror is compiled into its use sites, so @Versioned can never change it");
    }

    private static List<Path> javaSources() {
        try (Stream<Path> sources = Files.walk(MAIN)) {
            return sources.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .toList();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Walks the body of each annotated mirror by brace depth. Crude, but the shape being looked for is
     * a field declaration a few lines below a known annotation, and a parser would be more machinery
     * than the rule is worth.
     */
    private static List<String> finalFieldsInMirrors(final Path source) {
        final List<String> offenders = new ArrayList<>();
        final List<String> lines;
        try {
            lines = Files.readAllLines(source);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i)
                .contains(MIRROR)) continue;

            int depth = 0;
            for (int j = i; j < lines.size(); j++) {
                final String line = lines.get(j);
                depth += count(line, '{') - count(line, '}');
                if (depth > 0 && line.contains("static final ") && line.contains(";")) {
                    offenders.add(source.getFileName() + ": " + line.trim());
                }
                if (depth == 0 && j > i) break;
            }
        }
        return offenders;
    }

    private static int count(final String line, final char c) {
        int seen = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == c) seen++;
        }
        return seen;
    }
}
