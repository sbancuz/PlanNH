package com.sbancuz.plannh.data.flowchart;

import lombok.Getter;
import lombok.Setter;

/**
 * A group whose recipes run on the same machines. A plain {@link Group} frames a part of the chart
 * and says nothing about the hardware; this one is a claim about it, so it holds one machine type
 * only (by recipe handler, never by the display name a player can rewrite), its members share one
 * configuration, and their machine counts add up into a single pool the solver can be asked to fit.
 *
 * <p>
 * A separate type rather than a flag on {@link Group}: the capacity, the single-type rule and the
 * shared settings mean nothing for an ordinary group, and the canvas draws this one differently so
 * the two are not mistaken for each other.
 */
@Getter
@Setter
public class MachineGroup extends Group {

    public static final String TYPE = "machine_group";

    /**
     * How many machines the pool is, or 0 for as many as it takes. A positive capacity caps the
     * group's summed machine time in the solve, so the chart is balanced to fit the hardware that
     * exists instead of being measured after the fact.
     */
    private int machineCapacity;

    public MachineGroup() {
        // GraphData names a fresh chart element after its type, which spells this one "Machine_group".
        setHeader("Machine Group");
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
