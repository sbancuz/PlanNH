package com.sbancuz.plannh.client;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.SettingDef;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.provider.GTProvider;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineIndex;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineOverrides;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePresets;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.probe.MachineProbe;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;

/**
 * Writes what PlanNH believes about every GregTech multiblock to a Markdown file.
 *
 * <p>
 * The file exists because the interesting facts only hold in a loaded game: which machines the probe
 * can read, which knobs move a number, which rows a node ends up showing, and where the hand-written
 * rows still disagree. A test cannot see any of that - {@code GregTechAPI.METATILEENTITIES} is empty
 * outside a client - so this is how the answers get reviewed.
 */
public class MachineTableCommand extends CommandBase {

    public static final String COMMAND_NAME = "plannh_machines";

    private static final String FILE_NAME = "plannh-machines.md";

    /**
     * Rows every multiblock shows, so listing them says nothing about any particular machine. Matched
     * by key rather than by label, which is translated.
     */
    private static final Set<String> ON_EVERY_MACHINE = Set
        .of(Settings.VOLTAGE.key(), Settings.MACHINES.key(), Settings.AMP.key(), GTSettings.ADVANCED);

    /** Where a machine's numbers come from, and what that means for whoever is reading. */
    private enum Source {

        BOTH("Both a hand-written row and a probe reading. The row is what a chart uses; the probe is"
            + " the check on it. Anything in the knobs column with an arrow, or any machine listed"
            + " under a differing headline in the log, is a row that no longer matches GregTech."),
        PROBE("Read from the installed GregTech at runtime, with no hand-written row behind it. These"
            + " are machines the table never covered, and they follow whatever GregTech the pack"
            + " ships without anybody maintaining them."),
        DESCRIBER("GregTech supplies its own overclock behaviour for these, and it outranks every preset -"
            + " GTPresetApplier uses the describer in preference to any row or probe reading. So a"
            + " row here would change nothing."),
        OVERRIDE("A hand-written row kept deliberately, because the probe cannot answer or answers wrongly."
            + " The reason is on each line and in GTMachineOverrides. These are the only machines"
            + " where PlanNH still asserts a number against the machine that owns it."),
        UNMODELLED("Nothing models these. They plan as a plain single-speed machine with no parallels and no"
            + " overclock behaviour, which for a generator or a bespoke endgame multiblock may be"
            + " the honest answer, and for the rest is the work queue.");

        private final String explanation;

        Source(final String explanation) {
            this.explanation = explanation;
        }
    }

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(final ICommandSender sender) {
        return "/" + COMMAND_NAME;
    }

    /** Client-side and read-only, so it needs no permission. CommandBase would demand op otherwise. */
    @Override
    public boolean canCommandSenderUseCommand(final ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(final ICommandSender sender, final String[] args) {
        try {
            final File out = new File(Minecraft.getMinecraft().mcDataDir, FILE_NAME);
            sender.addChatMessage(new ChatComponentText("PlanNH: wrote " + writeTo(out) + " machines to " + FILE_NAME));
        } catch (final IOException e) {
            PlanNH.LOG.warn("PlanNH: cannot write {}", FILE_NAME, e);
            sender.addChatMessage(new ChatComponentText("PlanNH: could not write " + FILE_NAME + ", see the log"));
        }
    }

    /** Separate from the command so the table can also be produced without a loaded world. */
    public static int writeTo(final File out) throws IOException {
        try (PrintWriter writer = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            return write(writer);
        }
    }

    private static int write(final PrintWriter writer) {
        final Map<Source, List<Row>> bySource = new LinkedHashMap<>();
        for (final Source source : Source.values()) {
            bySource.put(source, new ArrayList<>());
        }

        int total = 0;
        for (final Row row : collect()) {
            bySource.get(row.source())
                .add(row);
            total++;
        }

        writer.println("# PlanNH: what it knows about each GregTech multiblock");
        writer.println();
        writeLegend(writer);
        for (final Map.Entry<Source, List<Row>> section : bySource.entrySet()) {
            writeSection(writer, section.getKey(), section.getValue());
        }
        return total;
    }

    private static void writeLegend(final PrintWriter writer) {
        writer.println("## Columns");
        writer.println();
        writer.println("- **rows** - what a node for this machine asks beyond the four every multiblock shows");
        writer.println("  (Tier, Mach, Amp, Advanced). Blank means it asks nothing else, which is the goal.");
        writer
            .println("- **override** - why this machine is not read from GregTech. Only the override section has one.");
        writer.println("- **knobs** - the structure settings that change a number this machine reports.");
        writer.println("  Blank means none do, so the node offers no structure rows at all. `a -> b` means the");
        writer.println("  hand-written row claims `a` and the machine itself reports `b`, which is a row to fix.");
        writer.println("- **modes** - machines that are two machines behind one controller. `{map=n}` says which");
        writer.println("  mode each recipemap selects, so the node derives it and asks nothing; `asks` means the");
        writer.println("  recipe cannot say which mode is meant and the node still offers the row.");
        writer.println();
    }

    private static void writeSection(final PrintWriter writer, final Source source, final List<Row> rows) {
        writer.println(
            "## " + source.name()
                .toLowerCase(Locale.ROOT) + " (" + rows.size() + ")");
        writer.println();
        writer.println(source.explanation);
        writer.println();
        if (rows.isEmpty()) return;

        rows.sort(Comparator.comparing(Row::machine));
        writer.println("| machine | rows | override | knobs | modes | class | recipemaps |");
        writer.println("|---|---|---|---|---|---|---|");
        for (final Row row : rows) {
            writer.println(
                "| " + row.machine()
                    + " | "
                    + row.rows()
                    + " | "
                    + row.override()
                    + " | "
                    + row.knobs()
                    + " | "
                    + row.modes()
                    + " | "
                    + row.className()
                    + " | "
                    + row.recipeMaps()
                    + " |");
        }
        writer.println();
    }

    private record Row(Source source, String machine, String knobs, String rows, String modes, String override,
        String className, String recipeMaps) {}

    private static List<Row> collect() {
        final List<Row> rows = new ArrayList<>();
        for (final IMetaTileEntity mte : GregTechAPI.METATILEENTITIES) {
            if (!(mte instanceof final RecipeMapWorkable workable) || !(mte instanceof MTEMultiBlockBase)) continue;
            try {
                rows.add(row(mte, workable));
            } catch (final RuntimeException | LinkageError e) {
                PlanNH.LOG.debug("PlanNH: {} would not describe itself", mte.getClass(), e);
            }
        }
        return rows;
    }

    private static Row row(final IMetaTileEntity mte, final RecipeMapWorkable workable) {
        final GTMachinePreset table = GTMachinePresets.lookup(mte.getClass());
        final GTMachinePreset probed = MachineProbe.probe(mte);
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.byId(mte.getLocalNameKey());
        final String reason = GTMachineOverrides.reason(mte.getClass());

        return new Row(
            source(entry, table, probed, reason),
            mte.getLocalName(),
            knobs(table, probed),
            visibleRows(entry, workable),
            modes(entry),
            reason == null ? "" : reason,
            mte.getClass()
                .getSimpleName(),
            recipeMaps(workable));
    }

    /**
     * The rows a node for this machine shows before Advanced is ticked, asked of the profile the way
     * the panel asks it: a context carrying one of the machine's own recipemaps, and a settings map
     * naming the machine, which is what every visibility predicate resolves against.
     */
    private static String visibleRows(@Nullable final GTMachineIndex.MachineEntry entry,
        final RecipeMapWorkable workable) {
        if (entry == null) return "not offered";
        final RecipeMap<?> map = workable.getAvailableRecipeMaps()
            .stream()
            .findFirst()
            .orElse(null);
        if (map == null) return "";

        final RecipeContext ctx = new RecipeContext(Map.<RecipeProperty<?>, Object>of(GTProvider.RECIPE_MAP, map));
        final MachineProfile profile = MachineProfileRegistry.get("gregtech:unified");
        final Map<String, Object> settings = Map.of(GTSettings.MACHINE, entry.id());

        final List<String> labels = new ArrayList<>();
        for (final SettingDef<?> def : profile.visibleSettings(ctx, settings)) {
            if (!ON_EVERY_MACHINE.contains(def.key)) labels.add(def.label);
        }
        return String.join(", ", labels);
    }

    /**
     * Blank when nothing moves a number, the set when both sources agree, and both sets when they do
     * not. A machine only reaching one source shows that one, because the section it is in already
     * says which source that is.
     */
    private static String knobs(@Nullable final GTMachinePreset table, @Nullable final GTMachinePreset probed) {
        if (table == null && probed == null) return "";
        if (table == null) return knobText(probed);
        if (probed == null) return knobText(table);

        final String fromTable = knobText(table);
        final String fromProbe = knobText(probed);
        return fromTable.equals(fromProbe) ? fromTable : fromTable + " -> " + fromProbe;
    }

    private static String knobText(@Nullable final GTMachinePreset preset) {
        if (preset == null || preset.knobs()
            .isEmpty()) return "";
        final List<String> names = new ArrayList<>();
        preset.knobs()
            .forEach(knob -> names.add(knob.name()));
        return String.join(", ", names);
    }

    private static String modes(@Nullable final GTMachineIndex.MachineEntry entry) {
        if (entry == null) return "";
        // A machine with one mode has no mode, so it asks nothing and shows nothing.
        if (entry.modes()
            .count() < 2) return "";

        final Map<String, Integer> modes = entry.modes()
            .byRecipeMap();
        final String count = entry.modes()
            .count() + " modes";
        if (modes == null) return count + ", asks";
        // Sorted so two runs of this command diff cleanly.
        return count + " " + new TreeMap<>(modes);
    }

    private static String recipeMaps(final RecipeMapWorkable workable) {
        final List<String> names = new ArrayList<>();
        workable.getAvailableRecipeMaps()
            .forEach(map -> names.add(map.unlocalizedName));
        names.sort(Comparator.naturalOrder());
        return String.join(", ", names);
    }

    private static Source source(@Nullable final GTMachineIndex.MachineEntry entry,
        @Nullable final GTMachinePreset table, @Nullable final GTMachinePreset probed, @Nullable final String reason) {
        if (entry != null && entry.describer() != null) return Source.DESCRIBER;
        if (reason != null) return Source.OVERRIDE;
        if (table != null && probed != null) return Source.BOTH;
        if (probed != null) return Source.PROBE;
        if (table != null) return Source.BOTH;
        return Source.UNMODELLED;
    }
}
