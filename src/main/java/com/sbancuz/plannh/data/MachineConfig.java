package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

import gregtech.api.util.OverclockCalculator;

public class MachineConfig {

    public long machineVoltage = 0;
    public long machineAmperage = 1;
    public int speedBoostPercent = 100;
    public int maxParallel = 1;
    public int machineCount = 1;
    public boolean perfectOC = false;

    public final Map<Integer, Float> inputConsumption = new HashMap<>();
    public final Map<Integer, Float> outputProductivity = new HashMap<>();

    public float speedFactor() {
        return speedBoostPercent / 100f;
    }

    public float inputMultiplier(int inputIndex) {
        return inputConsumption.getOrDefault(inputIndex, 1.0f);
    }

    public float outputMultiplier(int outputIndex) {
        return outputProductivity.getOrDefault(outputIndex, 1.0f);
    }

    public OverclockResult computeOverclock(long recipeEUt, int recipeDuration) {
        if (machineVoltage <= 0 || recipeEUt <= 0 || recipeDuration <= 0) {
            return new OverclockResult(recipeEUt, recipeDuration, 0);
        }
        OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(recipeEUt)
            .setEUt(machineVoltage)
            .setDuration(recipeDuration)
            .setAmperage(machineAmperage)
            .setDurationModifier(100.0 / speedBoostPercent)
            .setParallel(maxParallel)
            .setAmperageOC(true);
        if (perfectOC) calc.enablePerfectOC();
        calc.calculate();
        return new OverclockResult(calc.getConsumption(), calc.getDuration(), calc.getPerformedOverclocks());
    }

    public record OverclockResult(long consumptionEUt, int durationTicks, int performedOC) {}

    public float durationPerCycle(float baseDuration) {
        return baseDuration / speedFactor();
    }

    public long energyPerCycle(long baseTotalEU) {
        return baseTotalEU;
    }

    public int throughputFactor() {
        return maxParallel * machineCount;
    }

    public boolean hasAnyBoost() {
        return machineVoltage > 0 || machineAmperage != 1
            || speedBoostPercent != 100
            || maxParallel > 1
            || machineCount > 1
            || perfectOC
            || !inputConsumption.isEmpty()
            || !outputProductivity.isEmpty();
    }

    public MachineConfig copy() {
        MachineConfig c = new MachineConfig();
        c.machineVoltage = machineVoltage;
        c.machineAmperage = machineAmperage;
        c.speedBoostPercent = speedBoostPercent;
        c.maxParallel = maxParallel;
        c.machineCount = machineCount;
        c.perfectOC = perfectOC;
        c.inputConsumption.putAll(inputConsumption);
        c.outputProductivity.putAll(outputProductivity);
        return c;
    }
}
