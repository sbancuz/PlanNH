package com.sbancuz.plannh.nei;

import java.util.UUID;

/**
 * The port the user last ran an NEI recipe/usage lookup from.
 * ItemStacks stay at the NEI boundary and wiring happens via {@code Port#canConnect}.
 */
public record NodeLookupContext(UUID nodeId, boolean output, int portIndex) {}
