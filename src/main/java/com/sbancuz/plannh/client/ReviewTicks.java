package com.sbancuz.plannh.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.sbancuz.plannh.PlanNH;

/**
 * The machine table's hand-review column, read from the old file and written into the regenerated one.
 *
 * <p>
 * The table is generated, so every column can be rebuilt except the one where a human ticks the rows they have read. A
 * tick survives a regeneration only while the row's numbers equal the numbers it was reviewed against, so a GregTech
 * change to the machine clears it.
 */
public final class ReviewTicks {

    /** The unreviewed marker, and every row's mark in a fresh table. */
    public static final String UNCHECKED = "❌";

    /**
     * Column heading, which fromLines() also matches: a header row has a data row's shape and would otherwise be read
     * back as a machine.
     */
    public static final String COLUMN = "checked by hand";

    /** A machine's review state in the file being replaced, and the numbers it was reviewed at. */
    private record Reviewed(String tick, String numbers) {}

    private final Map<String, Reviewed> byMachine;

    private ReviewTicks(final Map<String, Reviewed> byMachine) {
        this.byMachine = byMachine;
    }

    /** No ticks, for a first run or a file that could not be read. */
    @Nonnull
    public static ReviewTicks none() {
        return new ReviewTicks(Map.of());
    }

    /** The ticks in the file about to be overwritten. A missing or unreadable file gives {@link #none()}. */
    @Nonnull
    public static ReviewTicks from(@Nonnull final File previous) {
        if (!previous.isFile()) return none();
        try {
            return fromLines(Files.readAllLines(previous.toPath(), StandardCharsets.UTF_8));
        } catch (final IOException e) {
            PlanNH.LOG.warn("PlanNH: could not read the previous machine table, every row starts unchecked", e);
            return none();
        }
    }

    /**
     * Reads tick, machine and numbers from every data row. Prose, the header and the separator have too few cells or an
     * unusable first cell, so they are skipped.
     */
    @Nonnull
    public static ReviewTicks fromLines(@Nonnull final List<String> lines) {
        final Map<String, Reviewed> found = new HashMap<>();
        for (final String line : lines) {
            if (!line.startsWith("| ")) continue;
            final String[] cells = line.split("\\|", -1);
            if (cells.length < 4) continue;
            final String tick = cells[1].trim();
            if (tick.isEmpty() || UNCHECKED.equals(tick) || COLUMN.equals(tick) || tick.startsWith("-")) continue;
            found.put(cells[2].trim(), new Reviewed(tick, cells[3].trim()));
        }
        return new ReviewTicks(found);
    }

    /**
     * The tick for this machine in the new table, kept only while the numbers match the reviewed ones. The reviewer's
     * mark is returned verbatim, so any mark counts as reviewed.
     */
    @Nonnull
    public String forMachine(@Nonnull final String machine, @Nonnull final String numbers) {
        final Reviewed was = byMachine.get(machine);
        if (was == null) return UNCHECKED;
        return was.numbers()
            .equals(numbers) ? was.tick() : UNCHECKED;
    }
}
