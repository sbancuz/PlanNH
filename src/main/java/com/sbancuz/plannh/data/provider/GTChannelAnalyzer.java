package com.sbancuz.plannh.data.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.sbancuz.plannh.data.channels.ChannelProblem;
import com.sbancuz.plannh.data.channels.ChannelProblem.Feed;
import com.sbancuz.plannh.data.channels.ChannelReport;
import com.sbancuz.plannh.data.channels.ChannelReport.Finding;
import com.sbancuz.plannh.data.channels.ChannelReport.Kind;
import com.sbancuz.plannh.data.channels.ChannelSolver;
import com.sbancuz.plannh.data.channels.ChannelSolver.Mode;
import com.sbancuz.plannh.data.channels.Ingredient;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.MachineGroup;
import com.sbancuz.plannh.data.flowchart.Node;

import gregtech.api.enums.Dyes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;

/**
 * Batch: each recipe's batch plus the pool's catalysts, and its fluids alone (what another color sees).
 * Passive: everything the pool's recipes use, at any amount.
 */
public final class GTChannelAnalyzer {

    private static final String CIRCUIT = "gregtech:gt.integrated_circuit";

    private GTChannelAnalyzer() {}

    private record Entry(GTRecipe recipe, RecipeMap<?> map, List<UUID> nodeIds) {}

    private static final class Pool {

        final String handler;
        final String machine;
        @Nullable
        final MachineGroup group;
        final Map<GTRecipe, Entry> entries = new IdentityHashMap<>();
        final List<Entry> order = new ArrayList<>();

        Pool(final String handler, final String machine, @Nullable final MachineGroup group) {
            this.handler = handler;
            this.machine = machine;
            this.group = group;
        }
    }

    public static ChannelReport analyze(@Nonnull final Graph graph, @Nonnull final Feed feed) {
        final Map<UUID, MachineGroup> groupOf = new HashMap<>();
        for (final Group g : graph.getGroups()) {
            if (g instanceof final MachineGroup mg) for (final UUID id : mg.getNodeIds()) groupOf.put(id, mg);
        }
        // MachineGroup or all ungrouped machines of a type
        final Map<Object, Pool> pools = new LinkedHashMap<>();
        for (final Node node : graph.getNodes()) {
            if (!(node.properties.get(GTProvider.GT_RECIPE) instanceof final GTRecipe recipe)
                || !(node.properties.get(GTProvider.RECIPE_MAP) instanceof final RecipeMap<?> map)) continue;
            final String handler = node.handlerName()
                .isEmpty() ? map.unlocalizedName : node.handlerName();
            final MachineGroup group = groupOf.get(node.id);
            final Pool pool = pools.computeIfAbsent(
                group != null ? group : handler,
                k -> new Pool(handler, node.machineName != null ? node.machineName : map.unlocalizedName, group));
            Entry e = pool.entries.get(recipe);
            if (e == null) {
                e = new Entry(recipe, map, new ArrayList<>());
                pool.entries.put(recipe, e);
                pool.order.add(e);
            }
            e.nodeIds()
                .add(node.id);
        }

        final List<ChannelReport.MachineReport> machines = new ArrayList<>();
        for (final Pool pool : pools.values()) {
            final ChannelReport.MachineReport report = analyzePool(pool, feed);
            if (report.recipes()
                .size() > 1 || pool.group != null
                || report.findings()
                    .stream()
                    .anyMatch(f -> f.kind() == Kind.INHERENT)) {
                machines.add(report);
            }
        }
        machines.sort(
            Comparator.comparing(ChannelReport.MachineReport::machine)
                .thenComparing(m -> Objects.toString(m.group(), "")));
        final List<ChannelReport.Dye> dyes = new ArrayList<>();
        for (int i = 0; i < ChannelSolver.COLORS; i++) {
            final Dyes d = Dyes.get(i);
            dyes.add(new ChannelReport.Dye(d.rgb, d.getLocalizedDyeName()));
        }
        return new ChannelReport(List.copyOf(machines), List.copyOf(dyes));
    }

    private static ChannelReport.MachineReport analyzePool(final Pool pool, final Feed feed) {
        final List<Entry> entries = pool.order;
        final Map<Ingredient, String> names = new HashMap<>();
        final Map<Ingredient.Item, ItemStack> catalystStacks = new LinkedHashMap<>();
        final List<ChannelProblem.Recipe> recipes = new ArrayList<>();
        final List<ChannelReport.PlanRecipe> planRecipes = new ArrayList<>();
        final Map<GTRecipe, Integer> planIndex = new IdentityHashMap<>();
        final Set<RecipeMap<?>> maps = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            final GTRecipe r = e.recipe();
            final Set<Ingredient.Item> catalysts = new HashSet<>();
            final Set<Ingredient> inputs = new HashSet<>();
            for (final ItemStack s : items(r)) {
                final Ingredient.Item key = key(s);
                names.putIfAbsent(key, itemName(s));
                if (s.stackSize != 0) {
                    inputs.add(key);
                    continue;
                }
                catalysts.add(key);
                final ItemStack one = s.copy();
                one.stackSize = 1;
                catalystStacks.putIfAbsent(key, one);
            }
            fluids(r).forEach(f -> {
                final Ingredient.Fluid key = key(f);
                names.putIfAbsent(key, f.getLocalizedName());
                inputs.add(key);
            });
            recipes.add(new ChannelProblem.Recipe(Set.copyOf(catalysts), Set.copyOf(inputs)));
            planRecipes.add(new ChannelReport.PlanRecipe(label(r), Set.copyOf(catalysts), List.copyOf(e.nodeIds())));
            planIndex.put(r, i);
            maps.add(e.map());
        }

        final List<Finding> findings = new ArrayList<>();
        final ChannelProblem problem = feed == Feed.BATCH
            ? batchProblem(entries, recipes, catalystStacks, planIndex, maps, findings)
            : passiveProblem(entries, recipes, planIndex, maps, findings);

        final Map<Mode, ChannelSolver.Solution> solutions = new EnumMap<>(Mode.class);
        for (final Mode mode : Mode.values()) solutions.put(mode, ChannelSolver.solve(problem, mode));
        return new ChannelReport.MachineReport(
            pool.handler,
            pool.machine,
            pool.group != null ? pool.group.getHeader() : null,
            pool.group != null ? pool.group.getMachineCapacity() : 0,
            List.copyOf(planRecipes),
            Map.copyOf(names),
            Map.copyOf(solutions),
            List.copyOf(findings),
            (int) recipes.stream()
                .map(ChannelProblem.Recipe::catalysts)
                .distinct()
                .count());
    }

    private static ChannelProblem batchProblem(final List<Entry> entries, final List<ChannelProblem.Recipe> recipes,
        final Map<Ingredient.Item, ItemStack> catalystStacks, final Map<GTRecipe, Integer> planIndex,
        final Set<RecipeMap<?>> maps, final List<Finding> findings) {
        final ItemStack[] catalysts = catalystStacks.values()
            .toArray(new ItemStack[0]);
        final List<List<ChannelProblem.BatchConflict>> byVictim = new ArrayList<>();
        for (int ai = 0; ai < entries.size(); ai++) {
            final GTRecipe a = entries.get(ai)
                .recipe();
            final Set<Ingredient.Item> catalystsA = recipes.get(ai)
                .catalysts();
            final List<ItemStack> consumed = new ArrayList<>();
            for (final ItemStack s : items(a)) if (s.stackSize > 0) consumed.add(s.copy());
            final FluidStack[] fluids = fluids(a).map(FluidStack::copy)
                .toArray(FluidStack[]::new);
            final ItemStack[] batch = Stream.concat(consumed.stream(), Stream.of(catalysts))
                .toArray(ItemStack[]::new);
            final Set<GTRecipe> hits = find(maps, batch, fluids, true);
            final Set<GTRecipe> fluidHits = find(maps, catalysts, fluids, true);
            hits.remove(a);

            final List<ChannelProblem.BatchConflict> own = new ArrayList<>();
            for (final GTRecipe b : hits) {
                final Set<Ingredient.Item> needs = catalystNeeds(b, consumed, catalystStacks);
                final List<Set<Ingredient>> shown = needs.stream()
                    .<Set<Ingredient>>map(Set::of)
                    .toList();
                final int plan = planIndex.getOrDefault(b, -1);
                final long[] k = exactMultiple(a, b);
                if (k != null) {
                    findings.add(tolerated(ai, a, b, k, shown, plan >= 0));
                } else if (catalystsA.containsAll(needs)) {
                    findings.add(new Finding(Kind.INHERENT, ai, label(b), shown, plan >= 0, "", b.mEUt, 0));
                } else {
                    own.add(new ChannelProblem.BatchConflict(needs, fluidHits.contains(b), plan));
                    findings.add(new Finding(Kind.CONFLICT, ai, label(b), shown, plan >= 0, "", b.mEUt, 0));
                }
            }
            byVictim.add(own);
        }
        return ChannelProblem.batch(recipes, byVictim, KEY_ORDER);
    }

    private static ChannelProblem passiveProblem(final List<Entry> entries, final List<ChannelProblem.Recipe> recipes,
        final Map<GTRecipe, Integer> planIndex, final Set<RecipeMap<?>> maps, final List<Finding> findings) {
        final Map<Ingredient.Item, ItemStack> items = new LinkedHashMap<>();
        final Map<Ingredient.Fluid, FluidStack> fluids = new LinkedHashMap<>();
        for (final Entry e : entries) {
            for (final ItemStack s : items(e.recipe())) {
                final ItemStack copy = s.copy();
                copy.stackSize = Math.max(1, copy.stackSize);
                items.putIfAbsent(key(s), copy);
            }
            fluids(e.recipe()).forEach(f -> fluids.putIfAbsent(key(f), f.copy()));
        }
        final Set<GTRecipe> hits = find(
            maps,
            items.values()
                .toArray(new ItemStack[0]),
            fluids.values()
                .toArray(new FluidStack[0]),
            false);

        final List<ChannelProblem.PassiveConflict> conflicts = new ArrayList<>();
        for (final GTRecipe x : hits) {
            final List<Set<Ingredient>> needs = passiveNeeds(x, items, fluids);
            int owner = planIndex.getOrDefault(x, -1);
            final boolean plan = owner >= 0;
            if (!plan) {
                for (int i = 0; i < entries.size() && owner < 0; i++) {
                    final long[] k = exactMultiple(
                        entries.get(i)
                            .recipe(),
                        x);
                    if (k == null) continue;
                    owner = i;
                    findings.add(
                        tolerated(
                            i,
                            entries.get(i)
                                .recipe(),
                            x,
                            k,
                            needs,
                            false));
                }
            }
            final ChannelProblem.PassiveConflict conflict = new ChannelProblem.PassiveConflict(needs, owner);
            boolean inherent = false;
            for (int i = 0; i < recipes.size(); i++) {
                if (i == owner || !conflict.metBy(
                    recipes.get(i)
                        .held()))
                    continue;
                findings.add(new Finding(Kind.INHERENT, i, label(x), needs, plan, "", x.mEUt, 0));
                inherent = true;
            }
            if (inherent) continue;
            conflicts.add(conflict);
            if (owner < 0) findings.add(new Finding(Kind.CONFLICT, -1, label(x), needs, false, "", x.mEUt, 0));
        }
        return ChannelProblem.passive(recipes, conflicts, KEY_ORDER);
    }

    private static Finding tolerated(final int victim, final GTRecipe a, final GTRecipe b, final long[] k,
        final List<Set<Ingredient>> needs, final boolean plan) {
        final double time = a.mDuration > 0 ? (double) b.mDuration * k[1] / k[0] / a.mDuration : 0;
        return new Finding(Kind.TOLERATED, victim, label(b), needs, plan, scale(k), b.mEUt, time);
    }

    private static final Comparator<Ingredient.Item> KEY_ORDER = Comparator.<Ingredient.Item>comparingInt(
        i -> CIRCUIT.equals(i.id()) ? i.meta() : Integer.MAX_VALUE)
        .thenComparing(Ingredient.Item::id)
        .thenComparingInt(Ingredient.Item::meta)
        .thenComparing(i -> Objects.toString(i.nbt(), ""));

    private static Set<GTRecipe> find(final Set<RecipeMap<?>> maps, final ItemStack[] items, final FluidStack[] fluids,
        final boolean respectAmounts) {
        final Set<GTRecipe> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final RecipeMap<?> map : maps) {
            map.findRecipeQuery()
                .items(items)
                .fluids(fluids)
                .dontCheckStackSizes(!respectAmounts)
                .findAll()
                .filter(r -> r.mEnabled && !r.mFakeRecipe)
                .forEach(out::add);
        }
        return out;
    }

    private static Set<Ingredient.Item> catalystNeeds(final GTRecipe b, final List<ItemStack> victimConsumed,
        final Map<Ingredient.Item, ItemStack> catalysts) {
        final Set<Ingredient.Item> needs = new HashSet<>();
        for (final ItemStack in : items(b)) {
            if (victimConsumed.stream()
                .anyMatch(v -> GTUtility.areStacksEqual(in, v))) continue;
            for (final Map.Entry<Ingredient.Item, ItemStack> c : catalysts.entrySet()) {
                if (GTUtility.areStacksEqual(in, c.getValue())) {
                    needs.add(c.getKey());
                    break;
                }
            }
        }
        return Set.copyOf(needs);
    }

    private static List<Set<Ingredient>> passiveNeeds(final GTRecipe x, final Map<Ingredient.Item, ItemStack> items,
        final Map<Ingredient.Fluid, FluidStack> fluids) {
        final List<Set<Ingredient>> needs = new ArrayList<>();
        for (final ItemStack in : items(x)) {
            final Set<Ingredient> any = new LinkedHashSet<>();
            for (final Map.Entry<Ingredient.Item, ItemStack> e : items.entrySet()) {
                if (GTUtility.areStacksEqual(in, e.getValue()) || GTUtility.areUnificationsEqual(in, e.getValue())) {
                    any.add(e.getKey());
                }
            }
            if (!any.isEmpty()) needs.add(Set.copyOf(any));
        }
        final FluidStack[] ins = x.mFluidInputs == null ? new FluidStack[0] : x.mFluidInputs;
        for (int j = 0; j < ins.length; j++) {
            if (ins[j] == null || ins[j].getFluid() == null) continue;
            final Set<Ingredient> any = new LinkedHashSet<>();
            addFluid(any, ins[j], fluids);
            if (x.mAltFluidInputs != null && j < x.mAltFluidInputs.length && x.mAltFluidInputs[j] != null) {
                for (final FluidStack alt : x.mAltFluidInputs[j]) addFluid(any, alt, fluids);
            }
            if (!any.isEmpty()) needs.add(Set.copyOf(any));
        }
        return List.copyOf(needs);
    }

    private static void addFluid(final Set<Ingredient> any, @Nullable final FluidStack f,
        final Map<Ingredient.Fluid, FluidStack> fluids) {
        if (f == null || f.getFluid() == null) return;
        final Ingredient.Fluid key = key(f);
        if (fluids.containsKey(key)) any.add(key);
    }

    /**
     * {num, den} if {@code b} is exactly num/den times {@code a}: the same consumed inputs and outputs
     * (with the same chances), every amount scaled by one factor. Null otherwise.
     */
    @Nullable
    static long[] exactMultiple(final GTRecipe a, final GTRecipe b) {
        final Map<String, Long> aIn = consumed(a), bIn = consumed(b);
        final Map<String, Long> aOut = outputs(a), bOut = outputs(b);
        if (aIn.isEmpty() || aOut.isEmpty()
            || !aIn.keySet()
                .equals(bIn.keySet())
            || !aOut.keySet()
                .equals(bOut.keySet())) {
            return null;
        }
        final String ref = aIn.keySet()
            .iterator()
            .next();
        final long num = bIn.get(ref), den = aIn.get(ref);
        if (num <= 0 || den <= 0 || !scaled(aIn, bIn, num, den) || !scaled(aOut, bOut, num, den)) return null;
        final long g = gcd(num, den);
        return new long[] { num / g, den / g };
    }

    private static boolean scaled(final Map<String, Long> a, final Map<String, Long> b, final long num,
        final long den) {
        for (final Map.Entry<String, Long> e : a.entrySet()) {
            if (b.get(e.getKey()) * den != num * e.getValue()) return false;
        }
        return true;
    }

    private static long gcd(final long a, final long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static String scale(final long[] k) {
        return k[1] == 1 ? String.valueOf(k[0]) : k[0] + "/" + k[1];
    }

    private static Map<String, Long> consumed(final GTRecipe r) {
        final Map<String, Long> out = new HashMap<>();
        for (final ItemStack s : items(r)) {
            if (s.stackSize > 0) out.merge(key(s).toString(), (long) s.stackSize, Long::sum);
        }
        fluids(r).forEach(f -> out.merge(key(f).toString(), (long) f.amount, Long::sum));
        return out;
    }

    private static Map<String, Long> outputs(final GTRecipe r) {
        final Map<String, Long> out = new HashMap<>();
        if (r.mOutputs != null) {
            for (int i = 0; i < r.mOutputs.length; i++) {
                final ItemStack s = r.mOutputs[i];
                if (s == null || s.getItem() == null) continue;
                out.merge(key(s) + "@" + chance(r.mOutputChances, i), (long) s.stackSize, Long::sum);
            }
        }
        if (r.mFluidOutputs != null) {
            for (int i = 0; i < r.mFluidOutputs.length; i++) {
                final FluidStack f = r.mFluidOutputs[i];
                if (f == null || f.getFluid() == null) continue;
                out.merge(key(f) + "@" + chance(r.mFluidOutputChances, i), (long) f.amount, Long::sum);
            }
        }
        return out;
    }

    private static int chance(final int[] chances, final int i) {
        return chances != null && i < chances.length ? chances[i] : (int) GTProvider.GT_CHANCE_SCALE;
    }

    private static List<ItemStack> items(final GTRecipe r) {
        final List<ItemStack> out = new ArrayList<>();
        if (r.mInputs != null) for (final ItemStack s : r.mInputs) if (s != null && s.getItem() != null) out.add(s);
        return out;
    }

    private static Stream<FluidStack> fluids(final GTRecipe r) {
        return r.mFluidInputs == null ? Stream.empty()
            : Stream.of(r.mFluidInputs)
                .filter(f -> f != null && f.getFluid() != null);
    }

    private static Ingredient.Item key(final ItemStack s) {
        return new Ingredient.Item(
            Item.itemRegistry.getNameForObject(s.getItem()),
            s.getItemDamage(),
            s.hasTagCompound() ? s.getTagCompound()
                .toString() : null);
    }

    private static Ingredient.Fluid key(final FluidStack f) {
        return new Ingredient.Fluid(
            f.getFluid()
                .getName());
    }

    private static String itemName(final ItemStack s) {
        final String id = Item.itemRegistry.getNameForObject(s.getItem());
        if (CIRCUIT.equals(id)) return "#" + s.getItemDamage();
        return displayName(s);
    }

    private static String label(final GTRecipe r) {
        final List<String> names = new ArrayList<>();
        if (r.mOutputs != null) for (final ItemStack s : r.mOutputs) {
            if (s != null && s.getItem() != null) names.add(displayName(s));
        }
        if (r.mFluidOutputs != null) for (final FluidStack f : r.mFluidOutputs) {
            if (f != null && f.getFluid() != null) names.add(f.getLocalizedName());
        }
        if (names.isEmpty()) return "?";
        final String head = String.join(", ", names.subList(0, Math.min(2, names.size())));
        return names.size() > 2 ? head + ", ..." : head;
    }

    private static String displayName(final ItemStack s) {
        try {
            return s.getDisplayName();
        } catch (final RuntimeException e) {
            return String.valueOf(s);
        }
    }
}
