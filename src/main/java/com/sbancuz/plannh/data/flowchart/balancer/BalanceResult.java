package com.sbancuz.plannh.data.flowchart.balancer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.sbancuz.plannh.data.flowchart.balancer.alternatives.Alternatives;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * A solved balance plus the answers it could have had, or the NONE/stalled fallback with just
 * the configured counts. {@code notes} are solver messages worth the user's eyes (e.g. "missing
 * an edge?"). A {@link Solved} balance's {@code auto} is the {@link SolutionView} - it carries
 * where each external went, which the netted summary cannot reconstruct and which is the
 * difference between "this is a product" and "this is being thrown away at that port".
 */
public sealed interface BalanceResult permits BalanceResult.Solved,BalanceResult.Fallback {

    Map<UUID, Balancer.NodeBalance> nodeBalances();

    Map<RecipeProperty<?>, Long> propertyTotals();

    double totalOperations();

    int totalDurationTicks();

    List<Note> notes();

    /**
     * Every display number plus the solve's {@link SolutionView} and its alternatives.
     */
    record Solved(Map<UUID, Balancer.NodeBalance> nodeBalances, Map<RecipeProperty<?>, Long> propertyTotals,
        double totalOperations, int totalDurationTicks, List<Note> notes, SolutionView auto,
        @Nullable Alternatives alternatives) implements BalanceResult {}

    /**
     * NONE mode or a stalled solve: the configured counts and any notes, with no solved view.
     */
    record Fallback(Map<UUID, Balancer.NodeBalance> nodeBalances, Map<RecipeProperty<?>, Long> propertyTotals,
        double totalOperations, int totalDurationTicks, List<Note> notes) implements BalanceResult {}
}
