package com.sbancuz.plannh.data.provider.gregtech.probe;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * An energy hatch that exists only to answer {@code getMaxInputVoltage()}.
 *
 * <p>
 * Most multiblocks compute their parallel count from the voltage reaching them, which they read by
 * summing their energy hatches. A prototype has none, so it reports zero volts and one parallel - a
 * machine that scales six per tier looks like a machine that does not scale at all. Giving the probe
 * clone one hatch of the tier being asked about is what makes voltage just another setting.
 *
 * <p>
 * The hatch is built rather than taken from GregTech's registry so that every tier is available and
 * the registry prototype is never touched. One hatch and no exotic hatches also puts
 * {@code setProcessingLogicPower} on its single-amp path, which is the honest reading for planning.
 */
final class FakeEnergyHatch {

    private FakeEnergyHatch() {}

    private static final Map<Integer, MTEHatchEnergy> BY_TIER = new HashMap<>();

    /**
     * Gives the machine one energy hatch of this tier, false when it did not take. {@code ValidMTEList}
     * silently removes a hatch it considers invalid, so an unchecked failure reads as zero volts - that
     * is, as "voltage does not matter here" rather than as a failure.
     */
    static boolean attach(final MTEMultiBlockBase machine, final int voltageTier) {
        final MTEHatchEnergy hatch = forTier(voltageTier);
        machine.mEnergyHatches.clear();
        if (hatch == null) return false;
        machine.mEnergyHatches.add(hatch);
        return machine.getMaxInputVoltage() == GTValues.V[voltageTier];
    }

    @Nullable
    private static MTEHatchEnergy forTier(final int voltageTier) {
        if (voltageTier < 0 || voltageTier >= GTValues.V.length) return null;
        return BY_TIER.computeIfAbsent(voltageTier, FakeEnergyHatch::build);
    }

    @Nullable
    private static MTEHatchEnergy build(final int voltageTier) {
        try {
            final MTEHatchEnergy hatch = new MTEHatchEnergy("plannh.probe", voltageTier, new String[0], null);
            // A MetaTileEntity only counts as valid once it and its tile entity point at each other.
            final BaseMetaTileEntity tile = new BaseMetaTileEntity();
            tile.setMetaTileEntity(hatch);
            hatch.setBaseMetaTileEntity(tile);
            return hatch.isValid() ? hatch : null;
        } catch (final RuntimeException | LinkageError e) {
            return null;
        }
    }
}
