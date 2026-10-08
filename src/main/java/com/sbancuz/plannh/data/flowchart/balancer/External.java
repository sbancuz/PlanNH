package com.sbancuz.plannh.data.flowchart.balancer;

/**
 * An external attached to a port, in ingredient units per second. The {@link SolutionView} rebuilds
 * these the same way the committed point's externals read, so a test can check the two surfaces
 * agree field by field.
 */
public record External(PortRef port, double ratePerSecond) {}
