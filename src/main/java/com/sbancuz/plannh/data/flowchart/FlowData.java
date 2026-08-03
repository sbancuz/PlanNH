package com.sbancuz.plannh.data.flowchart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface FlowData {

    List<Port<?>> getInputs();

    List<Port<?>> getOutputs();

    /**
     * Effective per-port output amounts for one full balance cycle, keyed by port
     * index, used by the summary netting. Node recipes override this with the
     * balancer-scaled per-cycle totals; the default converts the raw per-second
     * port amounts (steps/sinks) to per-cycle by multiplying by the cycle duration.
     */
    default Map<Integer, Float> effectiveOutputs(final Balancer.BalanceResult balance) {
        return perCycleAmount(getOutputs(), balance);
    }

    default Map<Integer, Float> effectiveInputs(final Balancer.BalanceResult balance) {
        return perCycleAmount(getInputs(), balance);
    }

    /**
     * Runtime in seconds of a single cycle/operation for this participant. Node
     * recipes report their (effect-modified) recipe duration; steps report 1.0,
     * because their port amounts are already expressed per second, so the value
     * acts as a no-op when converting between per-second and per-cycle space.
     */
    default float secondsPerCycle() {
        return 1.0f;
    }

    private static Map<Integer, Float> perCycleAmount(final List<Port<?>> ports, final Balancer.BalanceResult balance) {
        final float cycleSecs = balance.totalDurationTicks() > 0 ? (float) balance.totalDurationTicks() / 20f : 1f;
        final Map<Integer, Float> result = new HashMap<>();
        for (int i = 0; i < ports.size(); i++) {
            result.put(
                i,
                ports.get(i)
                    .getAmount() * cycleSecs);
        }
        return result;
    }
}
