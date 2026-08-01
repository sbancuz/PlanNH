package com.sbancuz.plannh.data.flowchart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface FlowData {

    List<Port<?>> getInputs();

    List<Port<?>> getOutputs();

    /**
     * Effective per-port output amounts, keyed by port index, used by the summary
     * netting. Node recipes override this with the balancer-scaled amounts; the
     * default returns the raw port amounts (steps/sinks, whose amount is already
     * expressed in its final per-second units).
     */
    default Map<Integer, Float> effectiveOutputs(final Balancer.BalanceResult balance) {
        return amountByPort(getOutputs());
    }

    default Map<Integer, Float> effectiveInputs(final Balancer.BalanceResult balance) {
        return amountByPort(getInputs());
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

    private static Map<Integer, Float> amountByPort(final List<Port<?>> ports) {
        final Map<Integer, Float> result = new HashMap<>();
        for (int i = 0; i < ports.size(); i++) {
            result.put(
                i,
                (float) ports.get(i)
                    .getAmount());
        }
        return result;
    }
}
