package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.data.provider.gregtech.probe.StructureWriter;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Guards the one gap the probe cannot notice about itself.
 *
 * <p>
 * {@link StructureWriter} recognises GregTech's structure fields by name and type. A field it does not
 * recognise is skipped, so the probe reads that machine at whatever the field happens to hold and
 * never varies it - the sensitivity scan then finds no setting, the node offers no row, and the chart
 * plans at one arbitrary structure. Nothing else in the codebase can see this: a machine read
 * incompletely looks exactly like one read fully.
 *
 * <p>
 * <b>This test is red, deliberately.</b> GregTech has 50 such field names today and none has been
 * looked at, so the honest state of the code is failing rather than waived: every one of them is a
 * machine PlanNH may be planning at one arbitrary structure. {@link #WAIVED} starts empty and grows
 * one entry at a time, each with a reason for why that field does not reach a number - the waiver is
 * the record of an investigation, not a way to quiet the list.
 */
class GTStructureCoverageTest {

    /** Names that read like a structure input. A tripwire, not a proof - the pattern can miss. */
    private static final Pattern LOOKS_STRUCTURAL = Pattern
        .compile("(?i).*(tier|level|coil|solenoid|glass|width|height|slice|mode).*");

    /**
     * Counters {@code checkMachine} fills while walking blocks. They carry a structure-shaped name but
     * are outputs of validation rather than inputs to the numbers.
     */
    private static final Pattern LOOKS_LIKE_A_COUNTER = Pattern.compile("(?i).*(amount|count|index|texture|checked).*");

    /**
     * Fields investigated and found not to reach any number a chart shows. Empty on purpose: an entry
     * here is a claim that somebody read the machine and checked, so it is added one at a time with the
     * reason beside it, never in bulk to make the build green.
     */
    private static final Set<String> WAIVED = Set.of();

    /** Unrecognised structure-shaped field names, to the machines declaring them. Scanned once. */
    private static final Map<String, List<String>> UNMODELLED = scan();

    @Test
    void everyStructureFieldGregTechExposesIsModelled() {
        final List<String> fresh = new ArrayList<>();
        UNMODELLED.forEach((field, machines) -> { if (!WAIVED.contains(field)) fresh.add(field + " on " + machines); });

        if (fresh.isEmpty()) return;
        fail(
            fresh.size() + " GregTech structure fields the probe cannot set. Each is a machine read at one\n"
                + "arbitrary structure, with no row offered for it:\n  "
                + String.join("\n  ", fresh)
                + "\n\nPer field: map it in StructureWriter.BY_NAME - which may also need a Coding and a"
                + "\nSettings constant - or, once you have checked it reaches no number, add it to WAIVED"
                + "\nwith that reason.");
    }

    /** A scan that silently found nothing would make the test above pass for the wrong reason. */
    @Test
    void theScanActuallyReachedGregTech() {
        assertTrue(
            multiblockCount() > 100,
            "only " + multiblockCount() + " multiblocks found; the jar scan is not reaching GregTech");
    }

    private static int multiblockCount() {
        return multiblocks().size();
    }

    private static List<Class<?>> multiblocks() {
        final List<Class<?>> found = new ArrayList<>();
        final File jar = jar();
        if (jar == null) return found;
        try (ZipFile zip = new ZipFile(jar)) {
            for (final Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                final String entry = e.nextElement()
                    .getName();
                if (!entry.endsWith(".class")) continue;
                final Class<?> c = load(
                    entry.substring(0, entry.length() - ".class".length())
                        .replace('/', '.'));
                if (c != null && MTEMultiBlockBase.class.isAssignableFrom(c) && c != MTEMultiBlockBase.class) {
                    found.add(c);
                }
            }
        } catch (final Exception ignored) {
            // An unreadable jar is reported by theScanActuallyReachedGregTech rather than here.
        }
        return found;
    }

    private static Map<String, List<String>> scan() {
        final Map<String, List<String>> unmodelled = new TreeMap<>();
        for (final Class<?> machine : multiblocks()) {
            final Field[] fields;
            try {
                fields = machine.getDeclaredFields();
            } catch (final Throwable t) {
                // A field typed from a mod this pack does not ship; nothing to say about it.
                continue;
            }
            for (final Field field : fields) {
                if (!isCandidate(field)) continue;
                unmodelled.computeIfAbsent(field.getName(), k -> new ArrayList<>())
                    .add(machine.getSimpleName());
            }
        }
        return unmodelled;
    }

    private static boolean isCandidate(final Field field) {
        if (Modifier.isStatic(field.getModifiers())) return false;
        final Class<?> type = field.getType();
        if (!(type == int.class || type == byte.class || type == short.class || type.isEnum())) return false;
        if (StructureWriter.covers(field)) return false;
        final String name = field.getName();
        return LOOKS_STRUCTURAL.matcher(name)
            .matches()
            && !LOOKS_LIKE_A_COUNTER.matcher(name)
                .matches();
    }

    private static Class<?> load(final String className) {
        try {
            return Class.forName(className, false, GTStructureCoverageTest.class.getClassLoader());
        } catch (final Throwable t) {
            return null;
        }
    }

    private static File jar() {
        try {
            return new File(
                MTEMultiBlockBase.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (final Exception e) {
            return null;
        }
    }
}
