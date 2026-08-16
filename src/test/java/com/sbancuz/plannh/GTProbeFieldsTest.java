package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * The machine probe reads GregTech's overclock internals by name, so a rename upstream would turn
 * every probed machine into a fallback without anything failing. These assert the names against the
 * GregTech on the test classpath instead, where a rename fails the build.
 *
 * <p>
 * {@code GregTechAPI.METATILEENTITIES} is empty outside a client, so the probe itself cannot run
 * here - only its reflection surface is checkable. Its output is verified in the client harness.
 */
class GTProbeFieldsTest {

    /** 6 ProcessingLogic + 13 OverclockCalculator named in the CsvSources, plus processingLogic itself. */
    private static final long COVERED_FIELDS = 20;

    @ParameterizedTest
    @CsvSource({ "maxParallel,int", "maxParallelSupplier,java.util.function.Supplier", "euModifier,double",
        "euModSupplier,java.util.function.Supplier", "speedBoost,double",
        "speedBoostSupplier,java.util.function.Supplier" })
    void processingLogicStillDeclares(final String name, final String type) {
        assertField(ProcessingLogic.class, name, type);
    }

    @ParameterizedTest
    @CsvSource({ "durationModifier,double", "eutModifier,double", "eutIncreasePerOC,double",
        "durationDecreasePerOC,double", "maxTierSkip,int", "heatOC,boolean", "heatDiscount,boolean", "machineHeat,int",
        "recipeHeat,int", "recipeEUt,long", "duration,int", "noOverclock,boolean", "laserOC,boolean" })
    void overclockCalculatorStillDeclares(final String name, final String type) {
        assertField(OverclockCalculator.class, name, type);
    }

    @Test
    void theMultiblockStillCarriesABuiltProcessingLogic() {
        assertField(MTEMultiBlockBase.class, "processingLogic", ProcessingLogic.class.getName());
    }

    @Test
    void theHooksTheProbeCallsStillResolve() throws NoSuchMethodException {
        final Method setup = MTEMultiBlockBase.class.getDeclaredMethod("setupProcessingLogic", ProcessingLogic.class);
        assertNotNull(setup);
        final Method calculator = ProcessingLogic.class.getDeclaredMethod("createOverclockCalculator", GTRecipe.class);
        assertEquals(OverclockCalculator.class, calculator.getReturnType());
    }

    /**
     * The probe replicates the supplier-to-field step out of {@code process()}, which is the only
     * place GregTech does it. If it moves, machines that scale with their structure would read as
     * whatever their constructor set.
     */
    @Test
    void theSuppliersStillCarryTheTypesTheProbeUnboxes() {
        assertEquals(Supplier.class, declared(ProcessingLogic.class, "maxParallelSupplier").getType());
        assertEquals(Supplier.class, declared(ProcessingLogic.class, "euModSupplier").getType());
        assertEquals(Supplier.class, declared(ProcessingLogic.class, "speedBoostSupplier").getType());
    }

    /** Unlimited tier skips are an in-band sentinel, so the probe reads that value back as the flag. */
    @Test
    void unlimitedTierSkipsIsStillIntegerMaxValue() throws ReflectiveOperationException {
        final OverclockCalculator calculator = new OverclockCalculator().setUnlimitedTierSkips();
        final Field maxTierSkip = declared(OverclockCalculator.class, "maxTierSkip");
        maxTierSkip.setAccessible(true);
        assertEquals(Integer.MAX_VALUE, maxTierSkip.getInt(calculator));
    }

    private static void assertField(final Class<?> owner, final String name, final String type) {
        final Field found = declared(owner, name);
        assertTrue(
            found.getType()
                .getName()
                .equals(type),
            owner.getSimpleName() + "." + name + " is now a " + found.getType());
    }

    /**
     * The one assertion that asks the probe itself rather than re-deriving its list. Everything above
     * checks names the test also holds; this checks that the resolver those names exist for actually
     * succeeded, which catches a failure for any reason the name list does not cover.
     */
    @Test
    void theProbeResolvedItsGregTechInternals() throws ReflectiveOperationException {
        final Field resolved = probeFields().getDeclaredField("RESOLVED");
        resolved.setAccessible(true);

        assertNotNull(
            resolved.get(null),
            "ProbeFields gave up on GregTech, so the probe is off and every machine falls back");
    }

    private static Class<?> probeFields() throws ClassNotFoundException {
        return Class.forName(
            "com.sbancuz.plannh.data.provider.gregtech.probe.ProbeFields",
            true,
            GTProbeFieldsTest.class.getClassLoader());
    }

    /**
     * The two lists above and ProbeFields' own members are separate authorities, so a field added to
     * the probe without a row here would be resolved at runtime and never asserted. This counts them.
     */
    @Test
    void everyFieldTheProbeResolvesIsCoveredAbove() throws ClassNotFoundException {
        final long resolved = Arrays.stream(probeFields().getDeclaredFields())
            .filter(f -> f.getType() == Field.class)
            .count();

        assertEquals(
            COVERED_FIELDS,
            resolved,
            "ProbeFields resolves a field this test does not name - add the row, then bump the count");
    }

    private static Field declared(final Class<?> owner, final String name) {
        try {
            return owner.getDeclaredField(name);
        } catch (final NoSuchFieldException e) {
            return fail(owner.getSimpleName() + " no longer declares " + name, e);
        }
    }
}
