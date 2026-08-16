package com.sbancuz.plannh.client;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineIndex;
import com.sbancuz.plannh.data.provider.gregtech.GTMachineOverrides;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset;
import com.sbancuz.plannh.data.provider.gregtech.GTMachinePresets;
import com.sbancuz.plannh.data.provider.gregtech.probe.MachineProbe;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Writes what PlanNH believes about every GregTech multiblock to a Markdown table.
 *
 * <p>
 * The table exists because the interesting facts only hold in a loaded game: which machines the probe
 * can read, which knobs move a number, which modes a recipe settles, and where the hand-written rows
 * still disagree. A test cannot see any of that - {@code GregTechAPI.METATILEENTITIES} is empty
 * outside a client - so this is how the answers get reviewed.
 *
 * <p>
 * Reading the result: a machine with a source of "table" and no override reason is one nobody has
 * checked against GregTech. A "differs" row is either a stale hand-written number or a probe that
 * cannot see the whole machine, and the override column says which was expected.
 */
public class MachineTableCommand extends CommandBase {

    public static final String COMMAND_NAME = "plannh_machines";

    private static final String FILE_NAME = "plannh-machines.md";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(final ICommandSender sender) {
        return "/" + COMMAND_NAME;
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
        final List<Row> rows = collect();
        rows.sort(Comparator.comparing(Row::machine));

        writer.println("| machine | class | knobs | modes | recipemaps | source | agreement | override |");
        writer.println("|---|---|---|---|---|---|---|---|");
        for (final Row row : rows) {
            writer.println(
                "| " + row.machine()
                    + " | "
                    + row.className()
                    + " | "
                    + row.knobs()
                    + " | "
                    + row.modes()
                    + " | "
                    + row.recipeMaps()
                    + " | "
                    + row.source()
                    + " | "
                    + row.agreement()
                    + " | "
                    + row.override()
                    + " |");
        }
        return rows.size();
    }

    private record Row(String machine, String className, String knobs, String modes, String recipeMaps, String source,
        String agreement, String override) {}

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
            mte.getLocalName(),
            mte.getClass()
                .getSimpleName(),
            knobs(table, probed),
            modes(entry),
            recipeMaps(workable),
            source(entry, table, probed, reason),
            agreement(table, probed),
            reason == null ? "" : reason);
    }

    /** Both sets when they differ, so a knob the scan dropped is visible rather than merely absent. */
    private static String knobs(@Nullable final GTMachinePreset table, @Nullable final GTMachinePreset probed) {
        final String fromTable = table == null ? "-"
            : table.knobs()
                .toString();
        final String fromProbe = probed == null ? "-"
            : probed.knobs()
                .toString();
        return fromTable.equals(fromProbe) ? fromTable : fromTable + " → " + fromProbe;
    }

    private static String modes(@Nullable final GTMachineIndex.MachineEntry entry) {
        if (entry == null) return "not offered";
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
     * What actually drives this machine's numbers. GregTech's own describer outranks everything, because
     * {@code GTPresetApplier.configure} uses it in preference to any preset - so a machine that has one
     * is modelled by GregTech whether or not a row or a probe reading also exists.
     */
    private static String source(@Nullable final GTMachineIndex.MachineEntry entry,
        @Nullable final GTMachinePreset table, @Nullable final GTMachinePreset probed, @Nullable final String reason) {
        if (entry != null && entry.describer() != null) return "gt describer";
        if (reason != null) return "override";
        if (table != null && probed != null) return "both";
        if (probed != null) return "probe";
        if (table != null) return "table";
        return "unmodelled";
    }

    private static String agreement(@Nullable final GTMachinePreset table, @Nullable final GTMachinePreset probed) {
        if (table == null || probed == null) return "";
        return MachineProbe.agrees(table, probed) ? "agrees" : "**differs**";
    }

    /** Client-side and read-only, so it needs no permission. CommandBase would demand op otherwise. */
    @Override
    public boolean canCommandSenderUseCommand(final ICommandSender sender) {
        return true;
    }
}
