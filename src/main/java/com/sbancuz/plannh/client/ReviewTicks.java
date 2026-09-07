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
 * Carries the machine table's hand-review state across a regeneration.
 *
 * <p>
 * The table is generated, so everything in it can be rebuilt except the column saying a human has
 * read the row. That column has to survive being overwritten, and it has to stop surviving the moment
 * GregTech moves underneath the machine - so a tick is kept only where the row still
 * says the same numbers it was reviewed against.
 */
public final class ReviewTicks {

    /** The unreviewed marker, and what every row of a fresh table carries. */
    public static final String UNCHECKED = "❌";

    /**
     * The column's heading. Here rather than at the writer because the reader has to recognise it: a
     * header row has the same shape as a data row, and would otherwise be read back as a machine.
     */
    public static final String COLUMN = "checked by hand";

    /** A machine's review state in the file being replaced, and the numbers it was reviewed at. */
    private record Reviewed(String tick, String numbers) {}

    private final Map<String, Reviewed> byMachine;

    private ReviewTicks(final Map<String, Reviewed> byMachine) {
        this.byMachine = byMachine;
    }

    /** Nothing carried, for a first run or a file that could not be read. */
    @Nonnull
    public static ReviewTicks none() {
        return new ReviewTicks(Map.of());
    }

    /** The ticks in the file about to be overwritten; an unreadable file simply carries nothing. */
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
     * Reads tick, machine and numbers from every data row. Anything that is not one - prose, the
     * header, the separator - has too few cells or an unusable first cell and is skipped.
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
     * The tick this machine starts the new table with: carried only while the numbers are the ones
     * that were reviewed. Whatever mark the reviewer used comes back verbatim, so the column is theirs.
     */
    @Nonnull
    public String forMachine(@Nonnull final String machine, @Nonnull final String numbers) {
        final Reviewed was = byMachine.get(machine);
        if (was == null) return UNCHECKED;
        return was.numbers()
            .equals(numbers) ? was.tick() : UNCHECKED;
    }
}
