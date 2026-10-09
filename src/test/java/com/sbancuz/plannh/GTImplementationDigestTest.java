package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.GTMachineOverrides;

/**
 * Fails when the implementation behind a hand-written override changes.
 *
 * <p>
 * Only the overrides are watched, because they are the only machines whose numbers PlanNH
 * <em>asserts</em> rather than reads. Everywhere else the probe takes GregTech's own answer at
 * runtime, so a GregTech rewrite is picked up rather than missed - watching those classes would fail
 * the build on every release for nothing, and a gate that cries wolf gets regenerated without being
 * read.
 *
 * <p>
 * An override transcribes something out of GregTech: the EBF's voltage term, the Multi Smelter's
 * fixed cost. When that source moves, the transcription is stale and nothing else in CI can tell.
 */
class GTImplementationDigestTest {

    private static final String EXPECTED = "gregtech-digests.properties";

    /** Written on failure so the fix is a file copy rather than pasting hex out of a build log. */
    private static final Path ACTUAL = Path.of("build", "gregtech-digests.actual.properties");

    /** The overridden machines, straight from the override file so a new one is watched by existing. */
    private static List<String> watched() {
        final List<String> keys = new ArrayList<>();
        GTMachineOverrides.keys()
            .forEach(keys::add);
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    @Test
    void everyWatchedClassIsStillTheOneTheNumbersWereReadFrom() throws IOException {
        final Properties expected = new Properties();
        try (InputStream in = getClass().getClassLoader()
            .getResourceAsStream(EXPECTED)) {
            assertNotNull(in, EXPECTED + " is missing; it is the record of which GregTech was verified");
            expected.load(in);
        }

        final Map<String, String> actual = new LinkedHashMap<>();
        for (final String className : watched()) {
            actual.put(className, digestOf(className));
        }

        final List<String> moved = new ArrayList<>();
        for (final Map.Entry<String, String> entry : actual.entrySet()) {
            final String was = expected.getProperty(entry.getKey());
            if (was == null) moved.add(entry.getKey() + " is watched but has no recorded digest");
            else if (!was.equals(entry.getValue()))
                moved.add(entry.getKey() + "\n    was " + was + "\n    now " + entry.getValue());
        }

        if (moved.isEmpty()) return;
        write(actual);
        fail(
            "A hand-written override no longer matches the GregTech it was read from:\n  " + String.join("\n  ", moved)
                + "\n\nRe-verify before releasing:"
                + "\n  1. ./gradlew runClient -PgtnhRecipes, then /plannh_machines"
                + "\n  2. copy run/client/plannh-machines.md over the checked-in copy and read the diff"
                + "\n  3. copy "
                + ACTUAL
                + " over src/test/resources/"
                + EXPECTED
                + "\nStep 3 without step 2 records that nobody looked.");
    }

    /** A watched class that stopped resolving is drift too, and a louder kind than a changed digest. */
    @Test
    void everyWatchedClassStillExists() {
        for (final String className : watched()) {
            assertTrue(
                getClass().getClassLoader()
                    .getResource(resourceName(className)) != null,
                className + " is gone from the pinned GregTech");
        }
    }

    private static String resourceName(final String className) {
        return className.replace('.', '/') + ".class";
    }

    private static String digestOf(final String className) throws IOException {
        try (InputStream in = GTImplementationDigestTest.class.getClassLoader()
            .getResourceAsStream(resourceName(className))) {
            if (in == null) return "absent";
            return HexFormat.of()
                .formatHex(sha256().digest(in.readAllBytes()));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JRE", e);
        }
    }

    private static void write(final Map<String, String> actual) throws IOException {
        final StringBuilder sb = new StringBuilder(header());
        actual.forEach(
            (k, v) -> sb.append(k)
                .append('=')
                .append(v)
                .append('\n'));
        Files.createDirectories(ACTUAL.getParent());
        Files.writeString(ACTUAL, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String header() {
        return """
            # SHA-256 of the GregTech classes PlanNH's numbers are derived from.
            # A changed digest means GregTech's implementation moved: re-verify against a client and
            # regenerate plannh-machines.md before updating this file. See GTImplementationDigestTest.
            """;
    }
}
