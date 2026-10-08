package com.sbancuz.plannh.data.effect;

public class EffectResult {

    private int durationTicks;
    private long costPerT;
    private int throughputFactor;

    public EffectResult(int durationTicks, long costPerT, int throughputFactor) {
        this.durationTicks = durationTicks;
        this.costPerT = costPerT;
        this.throughputFactor = throughputFactor;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public long energyPerT() {
        return costPerT;
    }

    public int throughputFactor() {
        return throughputFactor;
    }

    public void durationTicks(int value) {
        this.durationTicks = value;
    }

    public void energyPerT(long value) {
        this.costPerT = value;
    }

    public void throughputFactor(int value) {
        this.throughputFactor = value;
    }
}
