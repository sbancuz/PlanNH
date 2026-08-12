package com.sbancuz.plannh.data.flowchart.balancer;

import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Variable;

/**
 * A fresh ojAlgo model with its variable arrays unwrapped. Models are the ONE per-stage object in
 * the pipeline; the data that feeds them (machines/ports/gates) lives once on {@link ModelData}
 * and only these variable handles are new for each solve.
 *
 * @param extentVars per machine
 * @param flowVars   per drawn edge
 * @param extVars    per connected port
 * @param gateVars   per gate (empty when the model did not create binaries)
 */
public record Handles(ExpressionsBasedModel model, Variable[] extentVars, Variable[] flowVars, Variable[] extVars,
    Variable[] gateVars) {}
