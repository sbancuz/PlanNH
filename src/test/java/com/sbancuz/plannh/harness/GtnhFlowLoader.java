package com.sbancuz.plannh.harness;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.yaml.snakeyaml.Yaml;

import com.sbancuz.plannh.data.MachineProfile;
import com.sbancuz.plannh.data.MachineProfileRegistry;
import com.sbancuz.plannh.data.effect.EffectResult;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.setting.Settings;

import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * Loads gtnh-flow style YAML charts into PlanNH {@link Graph}s.
 *
 * <p>
 * The corpus describes machines with pooled ingredients: any producer of an ingredient can feed
 * any consumer. PlanNH graphs are machine-only with explicit port-to-port wiring, so the loader
 * materializes the pool as one edge per producer-consumer pair. Ports that end up with no edges
 * are the chart's terminals (raw inputs / final products).
 *
 * <p>
 * Pin semantics from gtnh-flow:
 * <ul>
 * <li>{@code number: N} - the machine count is fixed to N (maps to PlanNH's fixed machine
 * count).</li>
 * <li>{@code target: {ingredient: rate}} - a desired output rate; maps to the node's target
 * output rate pin, which AUTO holds exactly. Also surfaced on {@link LoadedChart#pins()} for
 * tests that want to rescale or clear it.</li>
 * </ul>
 */
public final class GtnhFlowLoader {

    /** {@code outputIndex} is the resolved output port for target pins, -1 for number pins. */
    public record Pin(String kind, int machineIndex, String machineName, String ingredient, double value,
        int outputIndex) {}

    /**
     * Duration key for headless charts; the real one lives in {@code RecipePropertyAPI} but its
     * static init touches Minecraft's FluidRegistry, which no headless test may do.
     */
    public static final RecipeProperty<Integer> DURATION_TICKS = RecipeProperty.builder("duration_ticks", 0)
        .build();

    public record LoadedChart(String name, Graph graph, List<Node> machines, List<Pin> pins) {

        public Node machine(final int index) {
            return machines.get(index);
        }
    }

    private static final int TICKS_PER_SECOND = 20;

    /** The bundled corpus, one entry per fixture under {@code /gtnh-flow/}. */
    public static final String[] CORPUS = { "mk1", "mk1_tiberium", "loopGraph", "light_fuel",
        "light_fuel_hydrogen_loop", "230_platline", "palladium_line", "nanocircuits", "cetane", "jet_fuel",
        "microsheep", "palladium", "twoslack", "excess_choice", "symmetric_choice", "two_decisions" };

    /**
     * Machine profiles are normally registered during mod init; headless tests need the default
     * profile present before any MachineConfig is constructed. The record is built directly
     * because MachineProfile.Builder reads NEIClientConfig in its constructor, which needs a
     * running client. Tests that build {@link Node}s by hand (instead of via {@link #load}) must
     * call this first.
     */
    public static void ensureDefaultMachineProfile() {
        if (MachineProfileRegistry.get(MachineProfileRegistry.defaultId()) == null) {
            MachineProfileRegistry.register(
                new MachineProfile(
                    MachineProfileRegistry.defaultId(),
                    "Default",
                    List.of(Settings.MACHINES.def(), Settings.TICK_MODIFIER.def()),
                    (s, ctx) -> new EffectResult(ctx.getOrDefault(DURATION_TICKS, 1), 0, 1)));
        }
    }

    private GtnhFlowLoader() {}

    /**
     * Loads {@code /gtnh-flow/<name>.yaml} from the test classpath (src/test/resources), so the
     * corpus resolves identically under gradle, IDE runners and CI. getResourceAsStream returns
     * null instead of throwing when the resource is missing, hence the guard.
     */
    public static LoadedChart load(final String name) {
        try (InputStream in = GtnhFlowLoader.class.getResourceAsStream("/gtnh-flow/" + name + ".yaml")) {
            Objects.requireNonNull(in, "missing gtnh-flow fixture: " + name);
            return load(name, in);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads a gtnh-flow YAML chart from any stream. {@code name} seeds the deterministic
     * node/edge ids, so the same chart loads to the same ids wherever it comes from.
     */
    public static LoadedChart load(final String name, final InputStream in) {
        Objects.requireNonNull(in, "null yaml stream for chart: " + name);
        ensureDefaultMachineProfile();
        final List<Map<String, Object>> raw = new Yaml().load(in);

        final Graph graph = new Graph();
        final List<Node> machines = new ArrayList<>();
        final List<Pin> pins = new ArrayList<>();

        // ingredient name -> producing (node, output index) / consuming (node, input index)
        final Map<String, List<int[]>> producers = new LinkedHashMap<>();
        final Map<String, List<int[]>> consumers = new LinkedHashMap<>();

        int index = 0;
        for (final Map<String, Object> entry : raw) {
            if (entry == null || !entry.containsKey("m")) continue;
            final int machineIndex = index++;

            // todo fix
            final Node node = new Node(new TemplateRecipeHandler() {
                @Override
                public String getGuiTexture() {
                    return "";
                }

                @Override
                public String getRecipeName() {
                    return "";
                }
            }, 0);
//            node.machineName = String.valueOf(entry.get("m"));
            node.getProperties().put(DURATION_TICKS,
                (int) Math.round(asDouble(entry.get("dur"), 1.0) * TICKS_PER_SECOND));

            for (final Map.Entry<String, Double> io : ioMap(entry.get("I")).entrySet()) {
                consumers.computeIfAbsent(io.getKey(), k -> new ArrayList<>())
                    .add(new int[] { machineIndex, node.getInputs().size() });
                node.getInputs().add(TestIngredients.port(io.getKey(), io.getValue()));
            }
            for (final Map.Entry<String, Double> io : ioMap(entry.get("O")).entrySet()) {
                producers.computeIfAbsent(io.getKey(), k -> new ArrayList<>())
                    .add(new int[] { machineIndex, node.getOutputs().size() });
                node.getOutputs().add(TestIngredients.port(io.getKey(), io.getValue()));
            }

            if (entry.containsKey("number")) {
                final int count = (int) asDouble(entry.get("number"), 1.0);
                node.getMachineConfig().setMachineCount(count);
                node.setMachineCountFixed(true);
                pins.add(new Pin("number", machineIndex, node.getMachineName(), null, count, -1));
            }
            if (entry.get("target") instanceof final Map<?, ?> targets) {
                for (final Map.Entry<?, ?> t : targets.entrySet()) {
                    final String ingredient = String.valueOf(t.getKey());
                    final double rate = asDouble(t.getValue(), 0);
                    for (int out = 0; out < node.getOutputs().size(); out++) {
                        if (TestIngredients.nameOf(node.getOutputs().get(out))
                            .equals(ingredient)) {
                            node.getTargetOutputRates().put(out, rate);
                            pins.add(new Pin("target", machineIndex, node.getMachineName(), ingredient, rate, out));
                        }
                    }
                }
            }

            machines.add(node);
            graph.addNode(node);
        }

        // Materialize ingredient pools as explicit producer -> consumer edges.
        int edgeIndex = 0;
        for (final Map.Entry<String, List<int[]>> pool : producers.entrySet()) {
            final List<int[]> sinks = consumers.get(pool.getKey());
            if (sinks == null) continue;
            for (final int[] src : pool.getValue()) {
                for (final int[] dst : sinks) {
                    graph.addEdge(
                        new Edge(
                            edgeId(name, edgeIndex++),
                            machines.get(src[0]).getId(),
                            machines.get(dst[0]).getId(),
                            src[1],
                            dst[1]));
                }
            }
        }

        return new LoadedChart(name, graph, machines, pins);
    }

    /**
     * Clears every target-rate pin, for tests that need the unpinned chart or want to re-pin at
     * a different scale. Leaves {@code number:} pins (fixed counts) alone.
     */
    public static void clearTargetPins(final LoadedChart chart) {
        for (final Pin pin : chart.pins()) {
            if (!"target".equals(pin.kind())) continue;
            chart.machines()
                .get(pin.machineIndex())
                .getTargetOutputRates()
                .clear();
        }
    }

    /** Removes every edge delivering into the given machine input; returns how many there were. */
    public static int removeEdgesInto(final LoadedChart chart, final Node machine, final int inputIndex) {
        final List<UUID> ids = chart.graph()
            .getEdges()
            .values()
            .stream()
            .filter(e -> e.targetNodeId.equals(machine.getId()) && e.targetInputIndex == inputIndex)
            .map(e -> e.id)
            .toList();
        ids.forEach(
            id -> chart.graph()
                .removeEdge(id));
        return ids.size();
    }

    private static Map<String, Double> ioMap(final Object raw) {
        final Map<String, Double> result = new LinkedHashMap<>();
        if (raw instanceof final Map<?, ?> map) {
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                final double quantity = asDouble(entry.getValue(), 0);
                if (quantity > 0) result.put(String.valueOf(entry.getKey()), quantity);
            }
        }
        return result;
    }

    private static double asDouble(final Object value, final double fallback) {
        if (value instanceof final Number n) return n.doubleValue();
        if (value instanceof final String s) return Double.parseDouble(s);
        return fallback;
    }

    private static UUID nodeId(final String chart, final int index) {
        return UUID.nameUUIDFromBytes((chart + "#node" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID edgeId(final String chart, final int index) {
        return UUID.nameUUIDFromBytes((chart + "#edge" + index).getBytes(StandardCharsets.UTF_8));
    }
}
