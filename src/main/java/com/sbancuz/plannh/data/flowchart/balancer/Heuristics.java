package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * The strategy bundle of a balancer type: how a measure is weighted (externals, source tilt) and
 * how gate costs are packed into one lexicographic objective, plus the numeric settings of the solve.
 * Swapping the object swaps the heuristic - a new balancer type is a new combination, not an edit
 * to a big shared class.
 */
public final class Heuristics {

    /** How much one port's external counts in the "least excess" objective. */
    @FunctionalInterface
    public interface ExternalWeight {

        /** Weight for a port whose per-craft quantity is {@code qtyPerCraft}. */
        double weight(double qtyPerCraft);
    }

    /** The added cost of a SOURCE gate over a SINK gate, as a multiplier on a quantity. */
    @FunctionalInterface
    public interface ImportTilt {

        /** 1.0 for a pure sink, &#62; 1.0 for a source. */
        double tilt(boolean input);
    }

    @FunctionalInterface
    public interface GatePacker {

        /**
         * One lexicographic weight per gate. No level may charge more than 1 per gate and there are
         * only {@code gateCount} of them, so each level outweighs everything after it.
         */
        double[] pack(int gateCount, boolean[] gateInput, Numerics numerics);
    }

    private final Numerics numerics = new Numerics();
    private final ExternalWeight externalWeight;
    private final ImportTilt tilt;
    private final GatePacker gatePacker;

    private Heuristics(final ExternalWeight externalWeight, final ImportTilt tilt, final GatePacker gatePacker) {
        this.externalWeight = externalWeight;
        this.tilt = tilt;
        this.gatePacker = gatePacker;
    }

    /** AUTO's semantics: excess measured in crafts, sink over source, lexicographic packing. */
    public static Heuristics auto() {
        return new Heuristics(
            qty -> qty > 0 ? 1.0 / qty : 1.0,
            input -> input ? 1.0 + 1.0 / Numerics.GATE_WEIGHT_FLOOR : 1.0,
            Heuristics::packedGateWeights);
    }

    /** The non-gated modes: no excess weighting of note, no tilt, no gate binaries. */
    public static Heuristics counts() {
        return new Heuristics(qty -> 1.0, input -> 1.0, (n, in, numerics) -> new double[n]);
    }

    public Numerics numerics() {
        return numerics;
    }

    double[] gateWeights(final int gateCount, final boolean[] gateInput) {
        return gatePacker.pack(gateCount, gateInput, numerics);
    }

    public double externalWeight(final double qtyPerCraft) {
        return externalWeight.weight(qtyPerCraft);
    }

    public double importTilt(final boolean input) {
        return tilt.tilt(input);
    }

    /**
     * The two leading preferences - fewest gates, then fewest imports - packed per gate. The
     * legend for unit base {@code unit}: level 0 ("fewest gates") is worth {@code unit} per gate so
     * it outweighs everything after it, level 1 ("fewest imports") is worth 1 on an input gate,
     * 0 on a source - so sink gates weigh {@code unit}, sources {@code unit + 1}.
     */
    private static double[] packedGateWeights(final int gateCount, final boolean[] gateInput, final Numerics numerics) {
        final double unit = Math.max(numerics.gateWeightFloor, gateCount + 1.0);
        final double[] weights = new double[gateCount];
        for (int gate = 0; gate < weights.length; gate++) {
            weights[gate] = unit + (gateInput[gate] ? 1.0 : 0.0);
        }
        return weights;
    }
}
