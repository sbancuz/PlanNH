package com.sbancuz.plannh.data.channels;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.sbancuz.plannh.data.channels.ChannelProblem.Feed;
import com.sbancuz.plannh.data.channels.ChannelSolver.Mode;
import com.sbancuz.plannh.data.channels.ChannelSolver.Solution;
import com.sbancuz.plannh.data.flowchart.Graph;

public record ChannelReport(@Nonnull List<MachineReport> machines, @Nonnull List<Dye> dyes) {

    @FunctionalInterface
    public interface Analyzer {

        ChannelReport analyze(@Nonnull Graph graph, @Nonnull Feed feed);
    }

    @Nullable
    private static Analyzer analyzer;

    public static void setAnalyzer(@Nullable final Analyzer a) {
        analyzer = a;
    }

    @Nullable
    public static Analyzer analyzer() {
        return analyzer;
    }

    public record Dye(int rgb, @Nonnull String name) {}

    public record MachineReport(@Nonnull String handler, @Nonnull String machine, @Nullable String group, int capacity,
        @Nonnull List<PlanRecipe> recipes, @Nonnull Map<Ingredient, String> names,
        @Nonnull Map<Mode, Solution> solutions, @Nonnull List<Finding> findings, int dedicated) {

        public Solution solution(final boolean priority) {
            if (!priority) return solutions.get(Mode.NONE);
            // check both approaches for setting recipe priority, they're optimal in different cases
            final Solution circuit = solutions.get(Mode.CIRCUIT);
            final Solution color = solutions.get(Mode.COLOR);
            if (color.machines() != circuit.machines()) return color.machines() < circuit.machines() ? color : circuit;
            final int colorBlocks = color.total()
                .blocks();
            final int circuitBlocks = circuit.total()
                .blocks();
            return colorBlocks < circuitBlocks ? color : circuit;
        }

        public String catalystsName(final Set<Ingredient.Item> catalysts) {
            return catalysts.stream()
                .map(this::name)
                .sorted()
                .collect(Collectors.joining(" + "));
        }

        public String needsName(final List<Set<Ingredient>> needs) {
            return needs.stream()
                .map(
                    any -> any.stream()
                        .map(this::name)
                        .sorted()
                        .collect(Collectors.joining(" or ")))
                .collect(Collectors.joining(", "));
        }

        private String name(final Ingredient i) {
            return names.getOrDefault(i, i.toString());
        }
    }

    public record PlanRecipe(@Nonnull String label, @Nonnull Set<Ingredient.Item> catalysts,
        @Nonnull List<UUID> nodeIds) {}

    public enum Kind {
        CONFLICT,
        TOLERATED,
        /** Shouldn't exist, but a few GT recipes always conflict and have nothing to tell them apart. */
        INHERENT
    }

    public record Finding(@Nonnull Kind kind, int victim, @Nonnull String culprit, @Nonnull List<Set<Ingredient>> needs,
        boolean plan, @Nonnull String scale, long eut, double timeRatio) {}
}
