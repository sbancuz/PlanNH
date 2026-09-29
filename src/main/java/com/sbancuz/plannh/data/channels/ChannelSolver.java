package com.sbancuz.plannh.data.channels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;
import org.ojalgo.optimisation.integer.IntegerStrategy;
import org.ojalgo.type.context.NumberContext;

import com.sbancuz.plannh.data.flowchart.balancer.Budget;

public final class ChannelSolver {

    public static final int COLORS = 16;
    private static final int MAX_CLIQUES = 500;
    public static final int QUAD = 4;

    public enum Mode {
        /** Nothing else may run in a channel, relies on no priority */
        NONE,
        /** Allow reliance on circuit priority */
        CIRCUIT,
        /** Use colored buses for prioritization, share uncolored hatches. Allows for some hatch optimization. */
        COLOR
    }

    public record Limits(long millis, int nodesPerSolve) {

        public static final Limits DEFAULT = new Limits(150, 20_000);
    }

    public record Parts(int buses, int quad, int normal) {

        public static final Parts ZERO = new Parts(0, 0, 0);

        public int blocks() {
            return buses + quad + normal;
        }

        public Parts plus(final Parts o) {
            return new Parts(buses + o.buses, quad + o.quad, normal + o.normal);
        }
    }

    public record Channel(@Nonnull List<Integer> members, @Nonnull List<Set<Ingredient.Item>> checkOrder,
        @Nonnull Parts parts) {}

    public record Solution(@Nonnull Mode mode, @Nonnull List<Channel> channels, int machines, @Nonnull Parts total,
        boolean channelsMinimal, boolean blocksMinimal) {}

    private ChannelSolver() {}

    public static int[] fluidHatches(final int fluids) {
        final int quad = fluids / QUAD;
        final int rest = fluids % QUAD;
        if (rest == 1) return new int[] { quad, 1 };
        return new int[] { quad + (rest > 0 ? 1 : 0), 0 };
    }

    public static Parts parts(final ChannelProblem p, final Collection<Integer> members, final Mode mode) {
        int fluids = 0;
        final Set<Object> buses = new HashSet<>();
        for (final int i : members) {
            fluids = Math.max(
                fluids,
                p.recipes()
                    .get(i)
                    .fluidInputs());
            final Object bus = busKey(p, i, mode);
            if (bus != null) buses.add(bus);
        }
        final int[] hatches = fluidHatches(fluids);
        return new Parts(buses.size(), hatches[0], hatches[1]);
    }

    @Nullable
    private static Object busKey(final ChannelProblem p, final int i, final Mode mode) {
        final ChannelProblem.Recipe r = p.recipes()
            .get(i);
        if (!r.needsBus()) return null;
        return mode == Mode.COLOR ? r.catalysts() : Boolean.TRUE;
    }

    @Nullable
    public static List<Set<Ingredient.Item>> checkOrder(final ChannelProblem p, final Collection<Integer> members,
        final Mode mode) {
        return judge(p, members, mode).order();
    }

    record Verdict(@Nullable List<Set<Ingredient.Item>> order, @Nonnull List<Set<Integer>> fixes) {

        static final Verdict NEVER = new Verdict(null, List.of(Set.of()));

        boolean valid() {
            return order != null;
        }
    }

    static Verdict judge(final ChannelProblem p, final Collection<Integer> members, final Mode mode) {
        final Set<Integer> memberSet = new HashSet<>(members);
        final Set<Set<Ingredient.Item>> present = new HashSet<>();
        for (final int i : members) present.add(
            p.recipes()
                .get(i)
                .catalysts());
        final List<Set<Ingredient.Item>> sets = new ArrayList<>(present);
        sets.sort(p.catalystOrder());
        if (mode == Mode.COLOR && sets.size() > COLORS) return Verdict.NEVER;
        return switch (p.conflicts()) {
            case ChannelProblem.Batch b -> batch(p, b, memberSet, sets, mode);
            case ChannelProblem.Passive x -> passive(p, x, memberSet, sets, mode);
        };
    }

    private static Verdict batch(final ChannelProblem p, final ChannelProblem.Batch conflicts,
        final Set<Integer> members, final List<Set<Ingredient.Item>> sets, final Mode mode) {
        final Set<Ingredient.Item> catalysts = new HashSet<>();
        for (final Set<Ingredient.Item> s : sets) catalysts.addAll(s);
        // An edge A -> B: A's catalysts must be checked before B's
        final Map<Set<Ingredient.Item>, Set<Set<Ingredient.Item>>> edges = new LinkedHashMap<>();
        for (final Set<Ingredient.Item> s : sets) edges.put(s, new HashSet<>());
        final List<Set<Integer>> fixes = new ArrayList<>();

        for (final int victim : members) {
            final Set<Ingredient.Item> src = p.recipes()
                .get(victim)
                .catalysts();
            for (final ChannelProblem.BatchConflict c : conflicts.byVictim()
                .get(victim)) {
                if (mode == Mode.COLOR) {
                    // B runs from any bus holding its catalysts, seeing only the batch's fluids
                    if (!c.fluidsOnly()) continue;
                    for (final Set<Ingredient.Item> s : sets) {
                        if (s.equals(src) || !s.containsAll(c.needs())) continue;
                        if (src.isEmpty()) return Verdict.NEVER;
                        edges.get(src)
                            .add(s);
                    }
                    continue;
                }
                if (!catalysts.containsAll(c.needs())) continue;
                if (mode == Mode.NONE || src.isEmpty() || members.contains(c.planRecipe())) return Verdict.NEVER;
                if (!edges.containsKey(c.needs())) {
                    // B conflicts, but it could fit in this channel instead
                    final Set<Integer> fix = withCatalysts(p, c.needs());
                    fix.remove(c.planRecipe());
                    if (fix.isEmpty()) return Verdict.NEVER;
                    fixes.add(fix);
                    continue;
                }
                edges.get(src)
                    .add(c.needs());
            }
        }
        final List<Set<Ingredient.Item>> sorted = topoSort(sets, edges, p.catalystOrder());
        if (sorted == null) return Verdict.NEVER;
        if (!fixes.isEmpty()) return new Verdict(null, fixes);
        return new Verdict(mode == Mode.NONE ? sorted : circuitlessLast(sorted), List.of());
    }

    private static Verdict passive(final ChannelProblem p, final ChannelProblem.Passive conflicts,
        final Set<Integer> members, final List<Set<Ingredient.Item>> sets, final Mode mode) {
        final List<Set<Ingredient>> places = new ArrayList<>();
        if (mode == Mode.COLOR) {
            final Set<Ingredient> fluids = new HashSet<>();
            final Map<Set<Ingredient.Item>, Set<Ingredient>> buses = new HashMap<>();
            for (final int i : members) {
                final ChannelProblem.Recipe r = p.recipes()
                    .get(i);
                final Set<Ingredient> bus = buses.computeIfAbsent(r.catalysts(), HashSet::new);
                bus.addAll(r.catalysts());
                for (final Ingredient in : r.inputs()) (in instanceof Ingredient.Fluid ? fluids : bus).add(in);
            }
            for (final Set<Ingredient> bus : buses.values()) {
                bus.addAll(fluids);
                places.add(bus);
            }
        } else {
            final Set<Ingredient> held = new HashSet<>();
            for (final int i : members) held.addAll(
                p.recipes()
                    .get(i)
                    .held());
            places.add(held);
        }
        final List<Set<Integer>> fixes = new ArrayList<>();
        for (final ChannelProblem.PassiveConflict c : conflicts.all()) {
            if (members.contains(c.owner())) continue;
            for (final Set<Ingredient> place : places) {
                if (!c.metBy(place)) continue;
                if (c.owner() < 0) return Verdict.NEVER;
                fixes.add(Set.of(c.owner()));
                break;
            }
        }
        if (!fixes.isEmpty()) return new Verdict(null, fixes);
        return new Verdict(mode == Mode.NONE ? sets : circuitlessLast(new ArrayList<>(sets)), List.of());
    }

    private static Set<Integer> withCatalysts(final ChannelProblem p, final Set<Ingredient.Item> catalysts) {
        final Set<Integer> out = new HashSet<>();
        for (int i = 0; i < p.recipes()
            .size(); i++) {
            if (p.recipes()
                .get(i)
                .catalysts()
                .equals(catalysts)) out.add(i);
        }
        return out;
    }

    private static List<Set<Ingredient.Item>> circuitlessLast(final List<Set<Ingredient.Item>> sets) {
        if (sets.remove(Set.<Ingredient.Item>of())) sets.add(Set.of());
        return sets;
    }

    @Nullable
    private static List<Set<Ingredient.Item>> topoSort(final List<Set<Ingredient.Item>> sets,
        final Map<Set<Ingredient.Item>, Set<Set<Ingredient.Item>>> edges,
        final Comparator<Set<Ingredient.Item>> order) {
        final Map<Set<Ingredient.Item>, Integer> indegree = new HashMap<>();
        for (final Set<Ingredient.Item> s : sets) indegree.put(s, 0);
        for (final Set<Set<Ingredient.Item>> ds : edges.values())
            for (final Set<Ingredient.Item> d : ds) indegree.merge(d, 1, Integer::sum);

        final TreeSet<Set<Ingredient.Item>> ready = new TreeSet<>(order);
        for (final Set<Ingredient.Item> s : sets) if (indegree.get(s) == 0) ready.add(s);
        final List<Set<Ingredient.Item>> out = new ArrayList<>();
        while (!ready.isEmpty()) {
            final Set<Ingredient.Item> s = ready.pollFirst();
            out.add(s);
            for (final Set<Ingredient.Item> d : edges.get(s)) {
                if (indegree.merge(d, -1, Integer::sum) == 0) ready.add(d);
            }
        }
        return out.size() == sets.size() ? out : null;
    }

    public static Solution solve(final ChannelProblem p, final Mode mode) {
        return solve(p, mode, Limits.DEFAULT);
    }

    public static Solution solve(final ChannelProblem p, final Mode mode, final Limits limits) {
        return new Search(p, mode, limits).run();
    }

    private static final class Search {

        private final ChannelProblem p;
        private final Mode mode;
        private final Limits limits;
        private final Budget budget;
        private final int n;
        private final Map<Set<Integer>, Verdict> verdicts = new HashMap<>();
        private final Set<Cut> cuts = new LinkedHashSet<>();
        private final List<List<Integer>> cliques = new ArrayList<>();
        private boolean[][] apart;
        private List<List<Integer>> best;

        private record Cut(List<Integer> together, Set<Integer> unless) {}

        private enum Goal {
            CHANNELS,
            BLOCKS,
            CANONICAL
        }

        Search(final ChannelProblem p, final Mode mode, final Limits limits) {
            this.p = p;
            this.mode = mode;
            this.limits = limits;
            this.budget = Budget.of(limits.millis());
            this.n = p.recipes()
                .size();
        }

        Solution run() {
            if (n == 0) return new Solution(mode, List.of(), 0, Parts.ZERO, true, true);
            final List<Integer> all = new ArrayList<>();
            for (int i = 0; i < n; i++) all.add(i);
            best = firstFit(all);
            apart = new boolean[n][n];
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                final Verdict v = verdict(List.of(i, j));
                if (v.valid()) continue;
                addCuts(List.of(i, j), v, Set.of());
                apart[i][j] = apart[j][i] = v.fixes()
                    .contains(Set.of());
            }
            findCliques(new ArrayList<>(), all, new ArrayList<>());
            final boolean channelsMinimal = improve(Goal.CHANNELS);
            final boolean blocksMinimal = improve(Goal.BLOCKS);
            if (channelsMinimal && blocksMinimal) canonicalize();
            return solution(channelsMinimal, blocksMinimal);
        }

        private void findCliques(final List<Integer> clique, final List<Integer> candidates,
            final List<Integer> excluded) {
            if (cliques.size() >= MAX_CLIQUES) return;
            if (candidates.isEmpty() && excluded.isEmpty()) {
                if (clique.size() > 2) cliques.add(List.copyOf(clique));
                return;
            }
            final int pivot = !candidates.isEmpty() ? candidates.getFirst() : excluded.getFirst();
            for (final int v : List.copyOf(candidates)) {
                if (apart[pivot][v]) continue;
                clique.add(v);
                findCliques(clique, neighbours(candidates, v), neighbours(excluded, v));
                clique.removeLast();
                candidates.remove(Integer.valueOf(v));
                excluded.add(v);
            }
        }

        private List<Integer> neighbours(final List<Integer> of, final int v) {
            final List<Integer> out = new ArrayList<>();
            for (final int u : of) if (apart[u][v]) out.add(u);
            return out;
        }

        private boolean improve(final Goal goal) {
            final int k = best.size();
            if (k == 1 || goal == Goal.BLOCKS && k == n) return true;
            final double incumbent = goal == Goal.BLOCKS ? blocks(best) : k;
            final List<List<Integer>> split = settle(() -> new Model(k, goal, null, -1, 0), incumbent);
            if (split != null && better(split)) best = split;
            return split != null;
        }

        // Needed to resolve cases where just the label would change, standarize on lowest channel order
        private void canonicalize() {
            final int k = best.size();
            final int cap = blocks(best);
            List<List<Integer>> current = relabeled(best);
            final int[] labels = labels(current);
            for (int i = 1; i < n; i++) {
                final int recipe = i;
                if (labels[recipe] == lowestAllowed(labels, recipe)) continue;
                final List<List<Integer>> split = settle(
                    () -> new Model(k, Goal.CANONICAL, labels, recipe, cap),
                    labels[recipe]);
                if (split == null) return;
                current = split;
                System.arraycopy(labels(split), 0, labels, 0, n);
            }
            best = current;
        }

        private int lowestAllowed(final int[] labels, final int recipe) {
            final boolean[] taken = new boolean[n];
            for (int j = 0; j < recipe; j++) if (apart[j][recipe]) taken[labels[j]] = true;
            int c = 0;
            while (taken[c]) c++;
            return c;
        }

        private static List<List<Integer>> relabeled(final List<List<Integer>> split) {
            final List<List<Integer>> out = new ArrayList<>();
            for (final List<Integer> c : split) {
                final List<Integer> sorted = new ArrayList<>(c);
                sorted.sort(Comparator.naturalOrder());
                out.add(sorted);
            }
            out.sort(Comparator.comparing(List::getFirst));
            return out;
        }

        private int[] labels(final List<List<Integer>> split) {
            final int[] out = new int[n];
            for (int c = 0; c < split.size(); c++) for (final int i : split.get(c)) out[i] = c;
            return out;
        }

        @Nullable
        private List<List<Integer>> settle(final Supplier<Model> build, final double incumbent) {
            while (!budget.expired()) {
                final Model model = build.get();
                final Optimisation.Result result = model.m.minimise();
                if (!result.getState()
                    .isFeasible() || result.getValue() > incumbent + 0.5) return null;
                final List<List<Integer>> split = model.read();
                if (split == null) return null;
                final List<List<Integer>> sound = new ArrayList<>();
                boolean clean = true;
                for (final List<Integer> channel : split) {
                    if (verdict(channel).valid()) {
                        sound.add(channel);
                        continue;
                    }
                    clean = false;
                    cut(channel);
                    sound.addAll(firstFit(channel));
                }
                if (better(sound)) best = sound;
                if (clean) return result.getState()
                    .isOptimal() ? split : null;
            }
            return null;
        }

        private void cut(final List<Integer> channel) {
            final Set<Integer> all = Set.copyOf(channel);
            final List<Integer> core = new ArrayList<>(channel);
            for (int i = core.size() - 1; i >= 0 && core.size() > 2; i--) {
                final Integer dropped = core.remove(i);
                if (!stuck(verdict(core), all)) core.add(i, dropped);
            }
            addCuts(core, verdict(core), all);
        }

        private static boolean stuck(final Verdict v, final Set<Integer> channel) {
            return !v.valid() && v.fixes()
                .stream()
                .anyMatch(f -> disjoint(f, channel));
        }

        private void addCuts(final List<Integer> core, final Verdict v, final Set<Integer> channel) {
            for (final Set<Integer> fix : v.fixes()) {
                if (fix.isEmpty()) {
                    cuts.add(new Cut(List.copyOf(core), Set.of()));
                    return;
                }
            }
            for (final Set<Integer> fix : v.fixes()) {
                if (disjoint(fix, channel)) cuts.add(new Cut(List.copyOf(core), Set.copyOf(fix)));
            }
        }

        private static boolean disjoint(final Set<Integer> a, final Set<Integer> b) {
            for (final int i : a) if (b.contains(i)) return false;
            return true;
        }

        private Verdict verdict(final List<Integer> members) {
            return verdicts.computeIfAbsent(Set.copyOf(members), k -> judge(p, members, mode));
        }

        private List<List<Integer>> firstFit(final List<Integer> recipes) {
            final List<List<Integer>> out = new ArrayList<>();
            for (final int r : recipes) {
                boolean placed = false;
                for (final List<Integer> c : out) {
                    c.add(r);
                    if (verdict(c).valid()) {
                        placed = true;
                        break;
                    }
                    c.removeLast();
                }
                if (!placed) out.add(new ArrayList<>(List.of(r)));
            }
            return out;
        }

        private boolean better(final List<List<Integer>> split) {
            if (split.size() != best.size()) return split.size() < best.size();
            return blocks(split) < blocks(best);
        }

        private int blocks(final List<List<Integer>> split) {
            int total = 0;
            for (final List<Integer> c : split) total += parts(p, c, mode).blocks();
            return total;
        }

        private Solution solution(final boolean channelsMinimal, final boolean blocksMinimal) {
            final List<Channel> out = new ArrayList<>();
            for (final List<Integer> members : best) {
                final List<Set<Ingredient.Item>> checkOrder = checkOrder(p, members, mode);
                if (checkOrder == null) throw new IllegalStateException("solver kept an invalid channel");
                final List<Integer> sorted = new ArrayList<>(members);
                sorted.sort(
                    Comparator.<Integer>comparingInt(
                        i -> checkOrder.indexOf(
                            p.recipes()
                                .get(i)
                                .catalysts()))
                        .thenComparingInt(i -> i));
                out.add(new Channel(List.copyOf(sorted), List.copyOf(checkOrder), parts(p, members, mode)));
            }
            final Comparator<Set<Ingredient.Item>> sets = p.catalystOrder();
            out.sort(Comparator.comparing(c -> first(c), sets));
            Parts total = Parts.ZERO;
            for (final Channel c : out) total = total.plus(c.parts());
            final int machines = mode == Mode.COLOR ? out.size() : (out.size() + COLORS - 1) / COLORS;
            return new Solution(mode, List.copyOf(out), machines, total, channelsMinimal, blocksMinimal);
        }

        private Set<Ingredient.Item> first(final Channel c) {
            return p.recipes()
                .get(
                    c.members()
                        .getFirst())
                .catalysts();
        }

        private final class Model {

            final ExpressionsBasedModel m = new ExpressionsBasedModel();
            final Variable[][] x = new Variable[n][];

            Model(final int k, final Goal goal, @Nullable final int[] fixed, final int recipe, final int cap) {
                final long millis = Math.max(1, budget.remaining());
                m.options.time_abort = millis;
                m.options.time_suffice = millis;
                m.options.iterations_abort = limits.nodesPerSolve();
                m.options.iterations_suffice = limits.nodesPerSolve();
                m.options.integer(
                    IntegerStrategy.DEFAULT.withGapTolerance(NumberContext.of(12, 8))
                        .withCutConfiguration(new IntegerStrategy.CutConfiguration().withTypes()));
                m.options.parallelism(1);

                for (int i = 0; i < n; i++) {
                    x[i] = new Variable[Math.min(i + 1, k)];
                    final Expression one = row("assign_" + i).level(1);
                    for (int c = 0; c < x[i].length; c++) {
                        x[i][c] = m.addVariable("x_" + i + "_" + c)
                            .binary();
                        one.set(x[i][c], 1);
                        if (fixed != null && i < recipe) x[i][c].level(c == fixed[i] ? 1 : 0);
                        if (goal == Goal.CANONICAL && i == recipe) x[i][c].weight(c);
                    }
                }
                for (int i = 1; i < n; i++) {
                    for (int c = 1; c < x[i].length; c++) {
                        final Expression opened = row("open_" + i + "_" + c).upper(0)
                            .set(x[i][c], 1);
                        for (int j = c - 1; j < i; j++) opened.set(x[j][c - 1], -1);
                    }
                }
                if (goal == Goal.CHANNELS) {
                    for (int c = 0; c < k; c++) {
                        final Variable used = m.addVariable("used_" + c)
                            .binary()
                            .weight(1);
                        for (int i = c; i < n; i++) row("link_" + i + "_" + c).upper(0)
                            .set(x[i][c], 1)
                            .set(used, -1);
                    }
                }
                for (int q = 0; q < cliques.size(); q++) {
                    for (int c = 0; c < k; c++) {
                        final Expression row = row("clique_" + q + "_" + c).upper(1);
                        for (final int i : cliques.get(q)) if (c < x[i].length) row.set(x[i][c], 1);
                    }
                }
                int id = 0;
                for (final Cut cut : cuts) {
                    for (int c = 0; c < k; c++) {
                        if (!allReach(cut.together(), c)) continue;
                        final Expression row = row("cut_" + id++).upper(
                            cut.together()
                                .size() - 1);
                        for (final int i : cut.together()) row.set(x[i][c], 1);
                        for (final int r : cut.unless()) if (c < x[r].length) row.set(x[r][c], -1);
                    }
                }
                if (goal == Goal.BLOCKS) blockCosts(k, null);
                if (goal == Goal.CANONICAL) blockCosts(k, row("blocks").upper(cap));
            }

            private void blockCosts(final int k, @Nullable final Expression total) {
                final Map<Object, List<Integer>> buses = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    final Object key = busKey(p, i, mode);
                    if (key != null) buses.computeIfAbsent(key, _ -> new ArrayList<>())
                        .add(i);
                }
                int maxFluids = 0;
                for (final ChannelProblem.Recipe r : p.recipes()) maxFluids = Math.max(maxFluids, r.fluidInputs());
                for (int c = 0; c < k; c++) {
                    final Variable hatches = cost(
                        m.addVariable("hatches_" + c)
                            .integer()
                            .lower(0)
                            .upper((maxFluids + QUAD - 1) / QUAD),
                        total);
                    for (int i = c; i < n; i++) {
                        final int fluids = p.recipes()
                            .get(i)
                            .fluidInputs();
                        if (fluids > 0) row("hatch_" + i + "_" + c).upper(0)
                            .set(x[i][c], fluids)
                            .set(hatches, -QUAD);
                    }
                    int b = 0;
                    for (final List<Integer> members : buses.values()) {
                        final Variable bus = cost(
                            m.addVariable("bus_" + b + "_" + c)
                                .binary(),
                            total);
                        for (final int i : members) {
                            if (c < x[i].length) row("bus_" + b + "_" + i + "_" + c).upper(0)
                                .set(x[i][c], 1)
                                .set(bus, -1);
                        }
                        b++;
                    }
                }
            }

            private static Variable cost(final Variable v, @Nullable final Expression total) {
                if (total == null) return v.weight(1);
                total.set(v, 1);
                return v;
            }

            private boolean allReach(final List<Integer> recipes, final int c) {
                for (final int i : recipes) if (c >= x[i].length) return false;
                return true;
            }

            private Expression row(final String name) {
                return m.addExpression(name);
            }

            @Nullable
            List<List<Integer>> read() {
                final Map<Integer, List<Integer>> byChannel = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    int channel = -1;
                    for (int c = 0; c < x[i].length && channel < 0; c++) {
                        final Number v = x[i][c].getValue();
                        if (v != null && v.doubleValue() > 0.5) channel = c;
                    }
                    if (channel < 0) return null;
                    byChannel.computeIfAbsent(channel, _ -> new ArrayList<>())
                        .add(i);
                }
                return new ArrayList<>(byChannel.values());
            }
        }
    }
}
