package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Serializer;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceResult;
import com.sbancuz.plannh.data.provider.gregtech.GTSettings;
import com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers;

import gregtech.api.enums.GTValues;

/**
 * The structure a chart plans with. It decides what every untouched node in that chart opens on, so
 * it has to survive a save, and the recipe-driven part of it has to land on a coil that can actually
 * run the recipe.
 */
class ChartMinimumsTest {

    private static final String COIL = Settings.GT_COIL.key();
    private static final String PIPE_CASING = Settings.GT_PIPE_CASING.key();
    private static final String VOLTAGE = Settings.VOLTAGE.key();

    /** A chart that has said nothing plans at the best the game offers, which is today's behaviour. */
    @Test
    void aFreshChartHasNoMinimums() {
        final Graph graph = new Graph("Slot 1");

        assertTrue(
            graph.getMinimums()
                .isEmpty());
        assertEquals(Graph.NO_MINIMUM, graph.getMinimum(COIL));
        assertEquals(Graph.NO_MINIMUM, graph.getMinimum(PIPE_CASING));
        assertEquals(Graph.NO_MINIMUM, graph.getMinimum(VOLTAGE));
    }

    @Test
    void everyMinimumSurvivesASave() {
        final Plan plan = Plan.createEmpty();
        final Graph graph = plan.getGraphs()
            .getFirst();
        graph.setMinimum(COIL, 3);
        graph.setMinimum(PIPE_CASING, 2);
        graph.setMinimum(VOLTAGE, 5);

        final Graph decoded = Serializer.decodePlan(Serializer.encodePlan(plan))
            .getGraphs()
            .getFirst();

        assertEquals(3, decoded.getMinimum(COIL));
        assertEquals(2, decoded.getMinimum(PIPE_CASING));
        assertEquals(5, decoded.getMinimum(VOLTAGE));
    }

    /** Charts saved before minimums existed carry none, and must open the way they always did. */
    @Test
    void aSaveWithoutTheKeysKeepsTheDefaults() {
        final Graph decoded = Serializer.decodePlan(Serializer.encodePlan(Plan.createEmpty()))
            .getGraphs()
            .getFirst();

        assertTrue(
            decoded.getMinimums()
                .isEmpty(),
            "a chart that set nothing must not come back holding sentinels");
        assertEquals(Graph.NO_MINIMUM, decoded.getMinimum(COIL));
    }

    /**
     * Minimums first shipped as three fields named after the GregTech things they bounded. Charts
     * saved that way are in the wild, so their floors have to land on the settings those fields turned
     * out to be, rather than being silently dropped back to the best the game offers.
     */
    @Test
    void aChartSavedWithTheOldNamedFieldsStillOpensAtItsFloors() {
        final Graph decoded = Serializer.decode(
            reEncode(
                asJson(new Graph("Slot 1")),
                "\"minCoilTier\": 3, \"minPipeCasingTier\": 2, \"minVoltageTier\": 5,"));

        assertEquals(3, decoded.getMinimum(COIL));
        assertEquals(2, decoded.getMinimum(PIPE_CASING));
        assertEquals(5, decoded.getMinimum(VOLTAGE));
    }

    /** The old format wrote its sentinel on every save, so reading it back as a floor would pin every chart. */
    @Test
    void theUnsetSentinelInAnOldSaveDoesNotBecomeAFloor() {
        final Graph decoded = Serializer.decode(
            reEncode(
                asJson(new Graph("Slot 1")),
                "\"minCoilTier\": -1, \"minPipeCasingTier\": -1, \"minVoltageTier\": -1,"));

        assertTrue(
            decoded.getMinimums()
                .isEmpty());
    }

    /** The graph JSON a save carries, which is gzipped and base64'd inside {@link Serializer#encode}. */
    @Nonnull
    private static String asJson(final Graph graph) {
        try (GZIPInputStream gzip = new GZIPInputStream(
            new ByteArrayInputStream(
                Base64.getDecoder()
                    .decode(Serializer.encode(graph))))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The same JSON with {@code injected} spliced in after the opening brace, packed the way a save is. */
    @Nonnull
    private static String reEncode(final String json, final String injected) {
        final String patched = json.replaceFirst("\\{", "{" + injected);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(patched.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder()
            .encodeToString(out.toByteArray());
    }

    /**
     * A minimum changes what an untouched node runs at, so it changes that node's parallel count and
     * every rate downstream of it. A chart that did not re-solve would keep showing the old numbers.
     */
    @Test
    void settingAMinimumMakesTheChartResolveAgain() {
        final Graph graph = new Graph("Slot 1");
        final BalanceResult solved = graph.balance();
        assertSame(solved, graph.balance(), "an untouched chart keeps the solve it already has");

        graph.setMinimum(COIL, 0);

        assertNotSame(solved, graph.balance());
    }

    /** Zero and below is "no requirement", which is what a recipe with no heat carries. */
    @ParameterizedTest
    @ValueSource(ints = { -1, 0 })
    void aRecipeThatAsksForNoHeatAsksForNoCoil(final int heat) {
        assertEquals(0, GTSettings.coilTierForHeat(heat));
    }

    @Test
    void aRecipeHotterThanEveryCoilLandsOnTheHottest() {
        assertEquals(
            GTStructureTiers.MAX_COIL_TIER,
            GTSettings.coilTierForHeat(GTStructureTiers.coilHeat(GTStructureTiers.MAX_COIL_TIER) + 1));
    }

    /**
     * The property that matters, checked over the whole range rather than at a chosen tier: the coil
     * picked reaches the heat, and the one below it does not. One tier too low is a node that opens
     * on a structure its own recipe cannot run in.
     */
    @Test
    void everyHeatLandsOnTheWeakestCoilThatReachesIt() {
        for (int tier = 0; tier <= GTStructureTiers.MAX_COIL_TIER; tier++) {
            for (final int heat : new int[] { GTStructureTiers.coilHeat(tier) - 1, GTStructureTiers.coilHeat(tier) }) {
                if (heat <= 0) continue;
                final int picked = GTSettings.coilTierForHeat(heat);

                assertTrue(
                    GTStructureTiers.coilHeat(picked) >= heat,
                    "coil " + picked + " runs at " + GTStructureTiers.coilHeat(picked) + " for " + heat);
                assertTrue(
                    picked == 0 || GTStructureTiers.coilHeat(picked - 1) < heat,
                    "coil " + (picked - 1) + " already reached " + heat);
            }
        }
    }

    /**
     * A recipe the chart's own hatch cannot power still gets one that can. Outside a game there is no
     * chart to read, so this is the recipe's floor on its own - which is also what it must be for any
     * chart that has set no voltage.
     */
    @Test
    void aRecipeTooExpensiveForTheChartRaisesItsOwnNode() {
        final long hv = GTValues.V[3];

        assertEquals(3, GTSettings.defaultVoltageTier(hv));
        assertEquals(0, GTSettings.defaultVoltageTier(0));
    }
}
