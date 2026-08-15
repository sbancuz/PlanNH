package com.sbancuz.plannh.data.provider.gregtech;

import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.COIL;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.ELECTRODE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.ITEM_PIPE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.MODE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.PIPE_CASING;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.SAWBLADE;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.SOLENOID;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.STRUCTURE_TIER;
import static com.sbancuz.plannh.data.provider.gregtech.GTMachinePreset.Knob.WIDTH;
import static com.sbancuz.plannh.data.provider.gregtech.GTStructureTiers.at;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import bartworks.common.configs.Configuration;
import gregtech.api.enums.HeatingCoilLevel;

/**
 * Per-machine overclock parameters, keyed by MetaTileEntity class name.
 *
 * <p>
 * Values are read from GT5-Unofficial at tag <b>5.09.52.613</b>, the version pinned in
 * dependencies.gradle. They drift between GT releases - the Industrial Centrifuge grew a momentum
 * mechanic after this tag, and gtnh-flow's three-year-old copy of this table is wrong for most rows -
 * so each entry cites the class it was read from and {@code GTMachineIndex} warns about multiblocks
 * with no entry. Where GregTech keeps its numbers somewhere a class can read them, this table does
 * not copy them: see {@link GTStructureTiers}.
 *
 * <p>
 * Keys are class-name strings rather than class literals on purpose: an entry for a mod that is not
 * installed then costs nothing instead of throwing NoClassDefFoundError, and the table can be
 * evaluated in headless tests without loading a single MetaTileEntity.
 */
public final class GTMachinePresets {

    private GTMachinePresets() {}

    private static final String GT_MULTI = "gregtech.common.tileentities.machines.multi.";
    private static final String GTPP = "gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.";

    private static final Map<String, GTMachinePreset> BY_CLASS = new HashMap<>();

    private static void put(final String className, final GTMachinePreset.Builder preset) {
        BY_CLASS.put(className, preset.build());
    }

    private static int coilHeat(final int coilTier) {
        return (int) HeatingCoilLevel.getFromTier((byte) clampCoil(coilTier))
            .getHeat();
    }

    private static int clampCoil(final int coilTier) {
        return Math.max(0, Math.min(GTStructureTiers.MAX_COIL_TIER, coilTier));
    }

    /**
     * Every Mega multiblock uses bartworks' shared parallel count. Read per call rather than captured,
     * because it is a config field: nothing stops a GT version from making it tunable again.
     */
    private static int megaParallels() {
        return Configuration.Multiblocks.megaMachinesMax;
    }

    static {
        // ── GT core multiblocks ────────────────────────────────────────────────────────────────
        // MTEIndustrialCentrifuge: PARALLEL_PER_TIER 6, SPEED 2.25f, EU_EFFICIENCY 0.9f
        put(
            GT_MULTI + "MTEIndustrialCentrifuge",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(6)
                .speed(1 / 2.25)
                .eu(0.9));

        // MTEIndustrialFormingPress
        put(
            GT_MULTI + "MTEIndustrialFormingPress",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(6)
                .speed(1 / 6.0));

        // MTEIndustrialForgeHammer: 6 * solenoidLevel * vTier
        put(
            GT_MULTI + "MTEIndustrialForgeHammer",
            GTMachinePreset.builder()
                .parallel(s -> 6 * s.solenoidTier() * s.voltageTier())
                .speed(1 / 2.0)
                .knobs(SOLENOID));

        // MTEIndustrialElectrolyzer: PARALLEL_PER_TIER 4, SPEED 2.8f, 0.9f
        put(
            GT_MULTI + "MTEIndustrialElectrolyzer",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(4)
                .speed(1 / 2.8)
                .eu(0.9));

        // MTEIndustrialMacerator: parallel reads controllerTier, speed reads structureTier. They
        // only diverge if the casings are rebuilt under an upgraded controller, so one knob drives
        // both here.
        put(
            GT_MULTI + "MTEIndustrialMacerator",
            GTMachinePreset.builder()
                .parallel(s -> Math.max(1, (s.structureTier() >= 2 ? 8 : 2) * Math.max(1, s.voltageTier())))
                .speed(s -> 1 / (s.structureTier() >= 2 ? 6.4 : 1.6))
                .knobs(STRUCTURE_TIER));

        // MTEIndustrialWireMill: SPEED_INCREASE_TIER 0.5f * itemPipeTier
        put(
            GT_MULTI + "MTEIndustrialWireMill",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(4)
                .speed(s -> 1 / (0.5 * Math.max(1, s.itemPipeTier())))
                .eu(0.75)
                .knobs(ITEM_PIPE));

        // MTEIndustrialMixer: 1 / (1 + (itemPipeTier + 1))
        put(
            GT_MULTI + "MTEIndustrialMixer",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(8)
                .speed(s -> 1.0 / (s.itemPipeTier() + 2))
                .knobs(ITEM_PIPE));

        put(
            GT_MULTI + "MTEIndustrialSifter",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(4)
                .speed(1 / 5.0)
                .eu(0.75));

        // MTEIndustrialThermalCentrifuge: coil drives both speed and EU, solenoid adds parallel.
        put(
            GT_MULTI + "MTEIndustrialThermalCentrifuge",
            GTMachinePreset.builder()
                .parallel(s -> 8 * s.voltageTier() + 2 * s.solenoidTier())
                .speed(s -> 1 / (2.5 + 0.05 * clampCoil(s.coilTier())))
                .eu(s -> 0.8 * Math.pow(0.95, clampCoil(s.coilTier())))
                .knobs(COIL, SOLENOID));

        put(
            GT_MULTI + "MTEOreWashingPlant",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(4)
                .speed(1 / 5.0)
                .knobs(MODE));

        put(
            GT_MULTI + "MTEIndustrialChemicalBath",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(4)
                .speed(1 / 5.0));

        put(
            GT_MULTI + "MTEIndustrialExtruder",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(6)
                .speed(1 / 3.5));

        // MTEIndustrialCuttingMachine: every number comes from the sawblade in the controller slot.
        final GTStructureTiers.Sawblades sawblades = GTStructureTiers.SAWBLADES;
        if (sawblades != null) {
            put(
                GT_MULTI + "MTEIndustrialCuttingMachine",
                GTMachinePreset.builder()
                    .parallel(s -> at(sawblades.parallelPerVoltageTier(), s.sawbladeTier()) * s.voltageTier())
                    .speed(s -> at(sawblades.durationModifier(), s.sawbladeTier()))
                    .eu(s -> at(sawblades.euModifier(), s.sawbladeTier()))
                    .knobs(SAWBLADE));
        }

        put(
            GT_MULTI + "MTEIndustrialRockBreaker",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(8)
                .speed(1 / 3.0)
                .eu(0.75));

        // MTEIndustrialCokeOven: no speed bonus at all; coil only discounts EU. structureTier is
        // the casing (0 Heat Resistant, 1 Heat Proof), width is the extra slices.
        put(
            GT_MULTI + "MTEIndustrialCokeOven",
            GTMachinePreset.builder()
                .parallel(s -> {
                    final boolean proof = s.structureTier() >= 1;
                    return (proof ? 32 : 16) + s.width() * (proof ? 16 : 8);
                })
                .eu(s -> Math.pow(0.98, clampCoil(s.coilTier()) + 1))
                .knobs(COIL, WIDTH, STRUCTURE_TIER));

        // MTEMultiFurnace hand-rolls checkProcessing with a fixed 4 EU/t over 128t and a coil-derived
        // parallel of 4 << (ordinal - 1); getTier() is ordinal - 2, hence the +1.
        put(
            GT_MULTI + "MTEMultiFurnace",
            GTMachinePreset.builder()
                .parallel(s -> 4 << (clampCoil(s.coilTier()) + 1))
                .recipeOverride(4, 128)
                .knobs(COIL));

        // MTEPyrolyseOven: coil is a speed bonus, not heat. No parallel.
        put(
            GT_MULTI + "MTEPyrolyseOven",
            GTMachinePreset.builder()
                .speed(s -> 2.0 / (1 + clampCoil(s.coilTier())))
                .knobs(COIL));

        put(
            GT_MULTI + "MTEIndustrialFishingPond",
            GTMachinePreset.builder()
                .parallel(s -> 2 * (s.voltageTier() + 1))
                .knobs(MODE));

        put(
            GT_MULTI + "MTEFrothFlotationCell",
            GTMachinePreset.builder()
                .perfectOC());

        // MTEElectricBlastFurnace: machine heat is the coil PLUS 100K per voltage tier over MV.
        put(
            GT_MULTI + "MTEElectricBlastFurnace",
            GTMachinePreset.builder()
                .heatOC(s -> coilHeat(s.coilTier()) + 100 * (s.voltageTier() - 2))
                .heatDiscount()
                .knobs(COIL));

        put(
            GT_MULTI + "MTELargeChemicalReactor",
            GTMachinePreset.builder()
                .perfectOC());

        // The Mega variants extend MTEExtendedPowerMultiBlockBase, not the machine they scale up, so
        // the superclass walk cannot reach them and each needs its own entry. Without one they fell
        // back to a bare calculator and overclocked like a generic machine.
        put(
            GT_MULTI + "MTEMegaChemicalReactor",
            GTMachinePreset.builder()
                .perfectOC()
                .unlimitedTierSkips()
                .parallel(s -> megaParallels()));

        // Both Mega tower modes are slower per operation than the machine they replace; the parallel
        // count is what pays for it. Distillery mode also scales with structure height.
        put(
            GT_MULTI + "MTEMegaDistillationTower",
            GTMachinePreset.builder()
                .parallel(s -> s.mode() == 1 ? megaParallels() * (1 + s.width() / 2) : megaParallels())
                .speed(s -> s.mode() == 1 ? 1.5 : 1.2)
                .eu(s -> s.mode() == 1 ? 0.5 : 0.9)
                .unlimitedTierSkips()
                .knobs(MODE, WIDTH));

        put(
            GT_MULTI + "MTEMegaOilCracker",
            GTMachinePreset.builder()
                .parallel(s -> megaParallels())
                .eu(s -> Math.pow(0.9, clampCoil(s.coilTier()) + 1))
                .unlimitedTierSkips()
                .knobs(COIL));

        // MTEPlasmaForge: coil heat alone, no voltage term - the one place it differs from the EBF.
        // Its perfect OC is state-dependent (convergence + full catalyst discount), so it is not
        // modelled; the catalyst discount ramps over 576000 ticks of uptime.
        put(
            GT_MULTI + "MTEPlasmaForge",
            GTMachinePreset.builder()
                .heatOC(s -> coilHeat(s.coilTier()))
                .unlimitedTierSkips()
                .knobs(COIL));

        // ── GT++ ───────────────────────────────────────────────────────────────────────────────
        // MTEAdvEBF (Volcanus): fixed 8 parallel, and heat from the coil alone.
        put(
            GTPP + "processing.advanced.MTEAdvEBF",
            GTMachinePreset.builder()
                .parallel(8)
                .speed(1 / 2.2)
                .eu(0.9)
                .heatOC(s -> coilHeat(s.coilTier()))
                .heatDiscount()
                .knobs(COIL));

        // MTEAdvDistillationTower (Dangote): mode 0 tower, mode 1 distillery. Distillery's parallel
        // uses the structure height, tower mode is a flat 12.
        put(
            GTPP + "processing.advanced.MTEAdvDistillationTower",
            GTMachinePreset.builder()
                .parallel(s -> s.mode() == 1 ? (int) (2 * Math.floor((s.width() + 1) / 3f)) * s.voltageTier() : 12)
                .speed(s -> s.mode() == 1 ? 1 / 2.0 : 1 / 3.5)
                .eu(s -> s.mode() == 1 ? 0.15 : 1.0)
                .knobs(MODE, WIDTH));

        put(
            GTPP + "processing.MTEIndustrialFluidHeater",
            GTMachinePreset.builder()
                .parallelPerVoltageTier(8)
                .speed(1 / 2.2)
                .eu(0.9));

        // MTEIndustrialDehydrator (Utupu-Tanuri): fixed 4 parallel plus EBF-style heat.
        put(
            GTPP + "processing.MTEIndustrialDehydrator",
            GTMachinePreset.builder()
                .parallel(4)
                .speed(1 / 2.2)
                .eu(0.5)
                .heatOC(s -> coilHeat(s.coilTier()))
                .heatDiscount()
                .knobs(COIL, MODE));

        put(
            GTPP + "processing.MTEIsaMill",
            GTMachinePreset.builder()
                .perfectOC());

        // MTEIndustrialAlloySmelter: mLevel = coilTier + 1. Heat OC against a fixed floor of 0 with
        // machine heat doubled, which is GT's way of making it fire every 900K instead of 1800K.
        // No heat discount here.
        put(
            GTPP + "processing.MTEIndustrialAlloySmelter",
            GTMachinePreset.builder()
                .parallel(s -> (clampCoil(s.coilTier()) + 1) * s.voltageTier())
                .speed(s -> 100.0 / (100 + 5 * (clampCoil(s.coilTier()) + 1)))
                .heatOC(s -> coilHeat(s.coilTier()) * 2)
                .recipeHeat(0)
                .knobs(COIL));

        // MTEChemicalPlant: pipe casing is parallel, coil is speed - same curve as the Pyrolyse Oven.
        put(
            GTPP + "production.chemplant.MTEChemicalPlant",
            GTMachinePreset.builder()
                .parallel(s -> 2 * Math.max(1, s.pipeCasingTier()))
                .speed(s -> 2.0 / (1 + clampCoil(s.coilTier())))
                .knobs(COIL, PIPE_CASING));

        put(
            GTPP + "production.MTEAutoCrafter",
            GTMachinePreset.builder()
                .parallel(s -> 2 * Math.max(1, s.voltageTier()))
                .speed(1 / 3.0));

        // ── Other addons ───────────────────────────────────────────────────────────────────────
        put(
            "bartworks.common.tileentities.multis.MTECircuitAssemblyLine",
            GTMachinePreset.builder()
                .perfectOC()
                .knobs(MODE));

        // MTEIndustrialArcFurnace: the electrode supplies speed, parallel, EU and both OC factors,
        // and the machine explicitly forbids tier skipping.
        final GTStructureTiers.Electrodes electrodes = GTStructureTiers.ELECTRODES;
        if (electrodes != null) {
            put(
                "kubatech.tileentity.gregtech.multiblock.MTEIndustrialArcFurnace",
                GTMachinePreset.builder()
                    .parallel(s -> at(electrodes.parallel(), s.electrodeTier()))
                    .speed(s -> at(electrodes.durationModifier(), s.electrodeTier()))
                    .eu(s -> at(electrodes.euModifier(), s.electrodeTier()))
                    .overclock(
                        s -> at(electrodes.durationDecreasePerOC(), s.electrodeTier()),
                        s -> at(electrodes.eutIncreasePerOC(), s.electrodeTier()))
                    .maxTierSkips(0)
                    .knobs(ELECTRODE, MODE));
        }
    }

    /**
     * Walks the class and its superclasses, so tiered and {@code *Legacy} subclasses inherit their
     * parent's entry. Returns null when nothing matches, which the caller reports as an uncovered
     * machine rather than silently guessing.
     */
    @Nullable
    public static GTMachinePreset lookup(@Nonnull final Class<?> mteClass) {
        for (Class<?> c = mteClass; c != null; c = c.getSuperclass()) {
            final GTMachinePreset preset = BY_CLASS.get(c.getName());
            if (preset != null) return preset;
        }
        return null;
    }

    /** Every keyed class name, for the test that asserts they all still resolve. */
    @Nonnull
    public static Iterable<String> keys() {
        return BY_CLASS.keySet();
    }
}
