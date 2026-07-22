package com.sbancuz.plannh.harness;

import java.io.InputStream;
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
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Node;

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
 * <li>{@code target: {ingredient: rate}} - a desired output rate; approximated by fixing the
 * machine count to {@code ceil(target / perMachineRate)}, and surfaced on the
 * {@link LoadedChart} so the future AUT solver mode can treat it as a real constraint.</li>
 * </ul>
 */
public final class GtnhFlowLoader {

    public record Pin(String kind, int machineIndex, String machineName, String ingredient, double value) {}

    public record LoadedChart(String name, Graph graph, List<Node> machines, List<Pin> pins) {

        public Node machine(final int index) {
            return machines.get(index);
        }
    }

    private static final int TICKS_PER_SECOND = 20;

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
                    (s, ctx) -> new MachineProfile.EffectResult(ctx.recipeDuration(), 0, 1)));
        }
    }

    private GtnhFlowLoader() {}

    /** Convenience for the bundled corpus: loads {@code /gtnh-flow/<name>.yaml} from test resources. */
    public static LoadedChart load(final String name) {
        final InputStream in = GtnhFlowLoader.class.getResourceAsStream("/gtnh-flow/" + name + ".yaml");
        Objects.requireNonNull(in, "missing gtnh-flow fixture: " + name);
        return load(name, in);
    }

    /**
     * Loads a gtnh-flow YAML chart from any stream. {@code name} seeds the deterministic
     * node/edge ids, so the same chart loads to the same ids wherever it comes from.
     */
    public static LoadedChart load(final String name, final InputStream in) {
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

            final Node node = new Node(nodeId(name, machineIndex), 0, 0);
            node.machineName = String.valueOf(entry.get("m"));
            node.durationTicks = (int) Math.round(asDouble(entry.get("dur"), 1.0) * TICKS_PER_SECOND);

            for (final Map.Entry<String, Double> io : ioMap(entry.get("I")).entrySet()) {
                consumers.computeIfAbsent(io.getKey(), k -> new ArrayList<>())
                    .add(new int[] { machineIndex, node.inputs.size() });
                node.inputs.add(TestIngredients.port(io.getKey(), io.getValue()));
            }
            for (final Map.Entry<String, Double> io : ioMap(entry.get("O")).entrySet()) {
                producers.computeIfAbsent(io.getKey(), k -> new ArrayList<>())
                    .add(new int[] { machineIndex, node.outputs.size() });
                node.outputs.add(TestIngredients.port(io.getKey(), io.getValue()));
            }

            if (entry.containsKey("number")) {
                final int count = (int) asDouble(entry.get("number"), 1.0);
                node.machineConfig.setMachineCount(count);
                node.setMachineCountFixed(true);
                pins.add(new Pin("number", machineIndex, node.machineName, null, count));
            }
            if (entry.get("target") instanceof final Map<?, ?> targets) {
                for (final Map.Entry<?, ?> t : targets.entrySet()) {
                    pins.add(
                        new Pin(
                            "target",
                            machineIndex,
                            node.machineName,
                            String.valueOf(t.getKey()),
                            asDouble(t.getValue(), 0)));
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
                            machines.get(src[0]).id,
                            machines.get(dst[0]).id,
                            src[1],
                            dst[1]));
                }
            }
        }

        applyTargetPins(machines, pins);

        return new LoadedChart(name, graph, machines, pins);
    }

    /**
     * Approximates a target pin the way a player would: fix the machine count to
     * {@code ceil(target / perMachineRate)} so OUTPUT/INPUT mode solves anchor on it. Only valid
     * for those modes - the future AUT solver mode replaces this with a real target constraint,
     * which is why the pins stay surfaced on {@link LoadedChart#pins()}.
     */
    private static void applyTargetPins(final List<Node> machines, final List<Pin> pins) {
        for (final Pin pin : pins) {
            if (!"target".equals(pin.kind())) continue;
            final Node node = machines.get(pin.machineIndex());
            double perOp = 0;
            for (final var port : node.outputs) {
                if (TestIngredients.nameOf(port)
                    .equals(pin.ingredient())) {
                    perOp = TestIngredients.quantityOf(port);
                    break;
                }
            }
            if (perOp <= 0 || node.durationTicks <= 0) continue;
            final double perMachineRate = perOp * TICKS_PER_SECOND / node.durationTicks;
            node.machineConfig.setMachineCount((int) Math.ceil(pin.value() / perMachineRate));
            node.setMachineCountFixed(true);
        }
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
