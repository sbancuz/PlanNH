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
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.probe.MachineProbe;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.RecipeMap;

/**
 * Writes what PlanNH believes about every GregTech multiblock to a Markdown file. No test can produce
 * it: {@code GregTechAPI.METATILEENTITIES} is empty outside a client, so review happens against this.
 */
public class MachineTableCommand extends CommandBase {

    public static final String COMMAND_NAME = "plannh_machines";

    private static final String FILE_NAME = "plannh-machines.md";

    /** Listing these says nothing about a particular machine. By key, because the label is translated. */
    private static final Set<String> ON_EVERY_MACHINE = Set
        .of(Settings.VOLTAGE.key(), Settings.MACHINES.key(), Settings.AMP.key(), GTSettings.ADVANCED);

    /** What a chart reads for this machine, in the order GTPresetApplier and GTMachineIndex pick. */
    private enum Source {

        DESCRIBER("Uses the machine's own OverclockDescriber, which GregTech hands over as public API."),
        PROBE("Uses GT machine's actual OC function."),
        OVERRIDE("Hand-written overrides due to wrong probe numbers."),
        HAND_WRITTEN_FALLBACK("Hand-written overrides due to probe being unable to read the machine."),
        UNMODELLED("Nothing answers, so the node plans as a plain single-speed machine.");

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
        final ReviewTicks carried = ReviewTicks.from(out);
        try (PrintWriter writer = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            return write(writer, carried);
        }
    }

    private static int write(final PrintWriter writer, final ReviewTicks carried) {
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

        writer.println("# Multiblock config simplifier table");
        writer.println();
        writeSections(writer);
        writeLegend(writer);
        for (final Map.Entry<Source, List<Row>> section : bySource.entrySet()) {
            writeSection(writer, section.getKey(), section.getValue(), carried);
        }
        return total;
    }

    private static void writeSections(final PrintWriter writer) {
        writer.println("## Sections");
        writer.println();
        writer.println("Two things can say how a machine behaves: GregTech itself - an OverclockDescriber the");
        writer.println("machine publishes, or MachineProbe reading a fake built instance at runtime - and the");
        writer.println("hand-written rows in GTMachineOverrides. The heading says which one a chart reads for that");
        writer.println("machine; the probe column says whether a reading backs it up.");
        writer.println();
    }

    private static void writeLegend(final PrintWriter writer) {
        writer.println("## Columns");
        writer.println();
        writer.println("- **" + ReviewTicks.COLUMN + "** - review state, not data. Regenerating carries a tick");
        writer.println(
            "  forward only while this machine's numbers are unchanged; anything that moved returns to "
                + ReviewTicks.UNCHECKED
                + ".");
        writer.println("- **numbers** - what a chart plans this machine with, at the structure an untouched node");
        writer.println("  shows: parallel, duration and EU modifiers, the two overclock factors, machine heat,");
        writer.println("  heat overclock and discount flags, recipe heat, tier skips.");
        writer.println("- **probe** - what the probe says about the row this machine is read from. `not read` means");
        writer.println("  the probe returned nothing, so nothing corroborates the row; `-` means there is no row.");
        writer.println("- **rows** - structure rows a node offers beyond the four every machine shows (Tier,");
        writer.println("  Mach, Amp, Advanced). The goal is the fewest rows that still describe every way this");
        writer.println("  machine can be changed, which is blank only when nothing else changes it.");
        writer.println("- **override** - why this machine is not read from GregTech.");
        writer.println("- **settings** - structure settings that change a number this machine reports; blank means");
        writer.println("  none do. `a -> b` means the row claims `a` and the machine reports `b`: fix the row.");
        writer.println("- **modes** - two machines behind one controller. `{map=n}` is the mode each recipemap");
        writer.println("  selects, so the node derives it; `asks` means it cannot, and the node offers the row.");
        writer.println();
    }

    private static void writeSection(final PrintWriter writer, final Source source, final List<Row> rows,
        final ReviewTicks carried) {
        writer.println(
            "## " + source.name()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ') + " (" + rows.size() + ")");
        writer.println();
        writer.println(source.explanation);
        writer.println();
        if (rows.isEmpty()) return;

        rows.sort(Comparator.comparing(Row::machine));
        writer.println(
            "| " + ReviewTicks.COLUMN
                + " | machine | numbers | probe | rows | override | settings | modes | class | recipemaps |");
        writer.println("|---|---|---|---|---|---|---|---|---|---|");
        for (final Row row : rows) {
            writer.println(
                "| " + carried.forMachine(row.machine(), row.numbers())
                    + " | "
                    + row.machine()
                    + " | "
                    + row.numbers()
                    + " | "
                    + row.probe()
                    + " | "
                    + row.rows()
                    + " | "
                    + row.override()
                    + " | "
                    + row.settings()
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

    private record Row(Source source, String machine, String numbers, String probe, String settings, String rows,
        String modes, String override, String className, String recipeMaps) {}

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
        final GTMachinePreset table = GTMachineOverrides.preset(mte.getClass());
        final GTMachinePreset probed = MachineProbe.probe(mte);
        final GTMachineIndex.MachineEntry entry = GTMachineIndex.byId(mte.getLocalNameKey());
        final String reason = GTMachineOverrides.reason(mte.getClass());

        return new Row(
            source(entry),
            mte.getLocalName(),
            numbers(entry),
            probeAgreement(table, probed),
            settings(table, probed),
            visibleRows(entry, workable),
            modes(entry),
            reason == null ? "" : reason,
            mte.getClass()
                .getSimpleName(),
            recipeMaps(workable));
    }

    /**
     * The rows shown before Advanced is ticked. Asked the way the panel asks: a context carrying one of
     * the machine's recipemaps and a settings map naming it, which visibility predicates resolve against.
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
     * Both sets when the table and the probe disagree, one when only one source has the machine - the
     * section it sits under already says which.
     */
    private static String settings(@Nullable final GTMachinePreset table, @Nullable final GTMachinePreset probed) {
        if (table == null && probed == null) return "";
        if (table == null) return settingNames(probed);
        if (probed == null) return settingNames(table);

        final String fromTable = settingNames(table);
        final String fromProbe = settingNames(probed);
        return fromTable.equals(fromProbe) ? fromTable : fromTable + " -> " + fromProbe;
    }

    private static String settingNames(@Nullable final GTMachinePreset preset) {
        if (preset == null || preset.settings()
            .isEmpty()) return "";
        final List<String> names = new ArrayList<>();
        preset.settings()
            .forEach(setting -> names.add(setting.name()));
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

    /**
     * The heading a machine is listed under. Reads the index's own answer rather than working the rule
     * out a second time; the only judgement here is that a describer outranks whatever preset it holds.
     */
    private static Source source(@Nullable final GTMachineIndex.MachineEntry entry) {
        if (entry == null) return Source.UNMODELLED;
        if (entry.describer() != null) return Source.DESCRIBER;
        return switch (entry.numberSource()) {
            case PROBE -> Source.PROBE;
            case OVERRIDE -> Source.OVERRIDE;
            case HAND_WRITTEN_FALLBACK -> Source.HAND_WRITTEN_FALLBACK;
            case NONE -> Source.UNMODELLED;
        };
    }

    /**
     * What a chart actually plans this machine with, so the file doubles as the snapshot a GregTech
     * update is diffed against. A describer's numbers live in GregTech's own object rather than in a
     * preset, so those say so instead of showing a preset the describer outranks.
     */
    private static String numbers(@Nullable final GTMachineIndex.MachineEntry entry) {
        if (entry == null) return "-";
        if (entry.describer() != null) return "describer";
        return entry.preset() == null ? "-" : MachineProbe.numbersText(entry.preset());
    }

    /** Whether a probe reading backs up the row a chart is reading, which is how a row earns deletion. */
    private static String probeAgreement(@Nullable final GTMachinePreset table,
        @Nullable final GTMachinePreset probed) {
        if (probed == null) return "not read";
        if (table == null) return "-";
        return MachineProbe.agrees(table, probed) ? "agrees" : "disagrees";
    }
}
