# Dependency/Edge Creation System Plan

## Overview
The data model already supports edges with `sourceOutputIndex`/`targetInputIndex`, and arrows render in `CanvasWidget.drawArrows()`. But there's no UI to create them. Need to add visual connection ports on nodes, an edge-drawing interaction, and input/output item matching.

---

## Phase 1: Visual Connection Ports on RecipeNodeWidget

Draw small colored circles on the **left edge** (inputs) and **right edge** (outputs) of each node.

**Port position formula** (must match `drawArrows()` in CanvasWidget):
```
Local pixel Y = zq((index + 1) * 18 + 10)
```
- Output port X = `getArea().width` (right edge)
- Input port X = `0` (left edge)

**Drawing:** Use MUI2's `GuiDraw.drawCircle` or draw filled circles via `GuiDraw.drawRect`. Draw in pixel-space (after `glPopMatrix()` in NEI path, directly in fallback path — same area as close button).

**Hit-testing methods:**
- `getOutputPortAt(int localMx, int localMy)` → port index or -1
- `getInputPortAt(int localMx, int localMy)` → port index or -1
- Use `zq(6)` hit radius around the computed port center

**Visual states for ports:**
- Normal: green fill (outputs), blue fill (inputs)
- Compatible target: golden glow (when dragging and this port matches)
- Hover: brighter shade

---

## Phase 2: Edge Creation Interaction in CanvasWidget

**State fields on CanvasWidget:**
```java
private boolean creatingEdge = false;
private UUID edgeSourceNodeId;
private int edgeSourcePortIndex;  // which output port
private int edgeMouseX, edgeMouseY;  // current mouse position during drag
private UUID edgeHoverTargetNodeId;
private int edgeHoverTargetPortIndex;
```

**Mouse flow:**

1. **RecipeNodeWidget.onMousePressed:** Check if click is on a port first. If so, return `Result.IGNORE` (let CanvasWidget handle it). Otherwise proceed normally (close button check, drag start).

2. **CanvasWidget.onMousePressed:** Before the existing pan check, iterate `nodeWidgets` and call `getOutputPortAt()` for each. If a port is found:
   - Set `creatingEdge = true`, store source node/port
   - Return `Result.SUCCESS` (gets tracked for drag)

3. **CanvasWidget.onMouseDrag:** If `creatingEdge`:
   - Update `edgeMouseX/Y` to `getContext().getMouseX()` + node area offset
   - Check all other nodes for compatible input ports under cursor
   - Compatible = `ItemStack.areItemsEqual(sourceNode.outputs[port], targetNode.inputs[port])`

4. **CanvasWidget.draw():** If `creatingEdge`, draw a preview line from source port to current mouse position (or snapped target port).

5. **CanvasWidget.onMouseRelease:** If `creatingEdge`:
   - If hovering over a compatible port → `graph.addEdge(new FlowchartEdge(...))`
   - Clear creating-edge state
   - `updateNodePositions()` to refresh

**Preview line drawing:**
- Draw a dashed or semi-transparent right-angle arrow from the source port to the cursor
- Same right-angle routing as `drawArrow()` + orange color

---

## Phase 3: Port Compatibility & Item Matching

`ItemStack.areItemsEqual(stack1, stack2)` checks item ID + metadata.

**Compatibility check helper on CanvasWidget:**
```java
private boolean canConnect(FlowchartNode srcNode, int srcOutIdx, FlowchartNode dstNode, int dstInIdx) {
    if (srcNode == dstNode) return false;  // no self-connections
    ItemStack out = srcNode.outputs.get(srcOutIdx);
    ItemStack in = dstNode.inputs.get(dstInIdx);
    if (out == null || in == null) return false;
    return out.isItemEqual(in);
}
```

---

## Phase 4: Edge Deletion

Allow deleting edges by clicking on the arrow. Add hit-testing to CanvasWidget.

**Edge hit-test:** Check if the mouse click is near the arrow path. Use a simple bounding box: for each edge, check if the click is within `thickness + margin` pixels of the horizontal/vertical segments.

**CanvasWidget.onMousePressed:** If click is not on a node and not on empty pan area, check if it's on an edge. If so, remove it via `graph.removeEdge(edgeId)`.

---

## Phase 5: Arrow Position Alignment

The current `drawArrows()` Y formula `(index + 1) * 18 * zoom + 10 * zoom` must match the port positions drawn in Phase 1. They use the same formula, so they should align. Verify at all zoom levels.

---

## Files Changed

| File | Changes |
|---|---|
| `RecipeNodeWidget.java` | Add port drawing, port hit-test methods, modify `onMousePressed` to pass port clicks through |
| `CanvasWidget.java` | Add edge-creation state, preview line drawing, port-detection in press/drag/release, edge deletion |

## Files Not Changed

| File | Reason |
|---|---|
| `FlowchartEdge.java` | Already complete |
| `FlowchartGraph.java` | `addEdge`/`removeEdge` already exist |
| `FlowchartNode.java` | Already has `inputs`/`outputs` lists |
| `FlowchartSerializer.java` | Already handles edges fully |
| `FlowchartOverlayHandler.java` | Already extracts inputs/outputs correctly |
| `FlowchartScreen.java` | Rework summary sidebar: remove time, show net inputs/outputs per cycle |

## Summary Rework

### Goal
Replace the current summary sidebar (node count, total time, total EU) with a **net input/output** summary showing what the flowchart consumes and produces per cycle.

### Key Concepts

| Concept | Definition |
|---------|-----------|
| **Fulfilled input** | A node's input at index `i` that has an edge targeting `(nodeId, i)` from some previous node's output. |
| **Net input** | Unfulfilled input — not connected by any edge. This item must be supplied externally. |
| **Consumed output** | A node's output at index `i` that has an edge sourcing from `(nodeId, i)` to a later node's input. |
| **Net output** | Unconsumed output — no outgoing edge. This item is a final product/byproduct of the flowchart. |
| **Per cycle** | One run of each recipe node in the graph. Count = `ItemStack.stackSize` of each input/output. |

### What changes

**1. New class `FlowchartSummary`** (`data/FlowchartSummary.java`)
- Inner record/class `SummaryLine`: `ItemStack stack`, `int totalCount`
- Fields: `List<SummaryLine> netInputs`, `List<SummaryLine> netOutputs`, `long totalEu`

**2. New method `FlowchartGraph.calculateSummary()`**
Algorithm:
```
for each edge:
    mark (edge.targetNodeId, edge.targetInputIndex) as fulfilled
    mark (edge.sourceNodeId, edge.sourceOutputIndex) as consumed

for each node:
    for each input at index i:
        if (node.id, i) NOT in fulfilled:
            add to net inputs, aggregated by item (isItemEqual)
    for each output at index i:
        if (node.id, i) NOT in consumed:
            add to net outputs, aggregated by item (isItemEqual)

sum totalEu across all nodes
```

**Item aggregation:** When the same item appears as multiple unfulfilled inputs (or unconsumed outputs), sum their `stackSize` into a single `SummaryLine`.

**3. Rework `FlowchartScreen` summary overlay**

Remove:
- `"Time: Xt"` and `"X.Xs"` lines
- Per-second rates

Add:
- `"Inputs (` + count + `)"` header
  - For each net input: `"Nx ItemName"` (using `GuiDraw.drawText`)
- `"Outputs (` + count + `)"` header
  - For each net output: `"Nx ItemName"`
- Keep `"EU: X"` line if > 0
- Keep zoom and instructions

**Rendering strategy:** The sidebar is 200px wide — use text-only for now. If items don't fit, use a smaller font or scroll (not needed for first pass).

### Edge Cases

| Case | Handling |
|------|----------|
| No inputs (e.g. creative-only recipes) | Show `"No external inputs"` |
| No outputs (e.g. void recipes) | Show `"No final outputs"` |
| Very long item names | Clip with `...` or use `drawText` truncation |
| EU is always 0 | `totalEu` is never populated by `FlowchartOverlayHandler.buildNode()` — this is a pre-existing bug, not fixed here |
| Graph changes dynamically | `calculateSummary()` is called every frame in the overlay lambda — always reflects current state |

### Files Changed

| File | Changes |
|------|---------|
| `FlowchartSummary.java` (NEW) | Data class for calculated summary |
| `FlowchartGraph.java` | Add `calculateSummary()` method |
| `FlowchartScreen.java` | Replace summary sidebar overlay content |
| `plan.md` | This section |

## Amounts Fix

### Root Cause
Multiple bugs cascade to produce wrong or missing item counts:

| # | Bug | File | Lines | Effect |
|---|-----|------|-------|--------|
| 1 | `extractThroughput()` re-queries NEI handler instead of reading saved node data | `RecipeNodeWidget.java` | 96-120 | Throughput display can disagree with stored data if handler state drifts |
| 2 | Byproducts dropped: `getOtherStacks` only read when `result == null` | `RecipeNodeWidget.java` | 108-119 | Secondary outputs invisible in throughput display even though stored correctly in node |
| 3 | `durationTicks = recipeHeight * 20` (pixels × 20, not real time) | `FlowchartOverlayHandler.java` | 97-98 | `perSec` values in throughput display are meaningless (`count / recipeHeight`, a count per pixel) |
| 4 | Output aggregation in `calculateSummary()` lacks `stackSize > 0` guard | `FlowchartGraph.java` | ~73 | Zero-size outputs could inflate net totals |
| 5 | `extractThroughput()` also lacks `stackSize > 0` guard (unlike `buildNode`) | `RecipeNodeWidget.java` | 102-106 | Zero-size items shown in throughput |

### Fixes

**1. Extract throughput from saved node data, not from handler**
Replace `extractThroughput()` to iterate `node.inputs` and `node.outputs` directly instead of calling `handler.getIngredientStacks()` / `handler.getResultStack()` / `handler.getOtherStacks()`.

This guarantees:
- Throughput display always matches saved node data
- Works even when no handler is loaded (non-NEI fallback display)
- All byproducts in `node.outputs` are visible (fixes bug #2)

```java
private void extractThroughput() {
    float sec = node.durationTicks / 20f;
    recipeDurationTicks = node.durationTicks;
    recipeTotalEu = node.totalEu;

    for (ItemStack stack : node.inputs) {
        if (stack != null && stack.stackSize > 0) {
            inputLines.add(new ThroughputLine(stack, stack.stackSize, sec));
        }
    }
    for (ItemStack stack : node.outputs) {
        if (stack != null && stack.stackSize > 0) {
            outputLines.add(new ThroughputLine(stack, stack.stackSize, sec));
        }
    }
}
```

The `ThroughputLine` class stays the same for now, but `perSec` will be derived from `node.durationTicks` which may still be wrong (bug #3). Since we can't fix duration properly without GT machine data, we'll just **remove the perSec display** from `drawThroughputInfo()` so no wrong numbers are shown.

**2. Remove perSec display from `drawThroughputInfo()`**
Remove the `perSec > 0` suffix from both input and output lines. The per-second rate is meaningless because `durationTicks` is set to pixel height, not actual time.

```java
// Before:
String label = line.count + "x " + line.stack.getDisplayName();
if (line.perSec > 0) label += "  " + String.format("%.2f/s", line.perSec);

// After:
String label = line.count + "x " + line.stack.getDisplayName();
```

**3. Guard outputs with `stackSize > 0` in `calculateSummary()`**
```java
if (stack != null && stack.stackSize > 0 && !consumedOutputs.contains(...)) {
```

**4. Remove call to `extractThroughput(handler, ref.recipeIndex)` from `ensureRecipeHandler`**
Instead call the new parameterless `extractThroughput()` that reads from node data.

### Files Changed

| File | Changes |
|------|---------|
| `RecipeNodeWidget.java` | Rewrite `extractThroughput()` to read from `node.inputs`/`node.outputs`; remove perSec display in `drawThroughputInfo()`; call new parameterless version from `ensureRecipeHandler` |
| `FlowchartGraph.java` | Add `stackSize > 0` guard for outputs in `calculateSummary()` |
| `RecipeNodeWidget.java` | Clean up unused `IRecipeHandler` import (no longer needed in extractThroughput) |

### Not Changed

| Aspect | Reason |
|--------|--------|
| `buildNode()` output capture | Already correct — captures both result + other stacks with proper stackSizes |
| `calculateSummary()` input aggregation | Already correct — has `stackSize > 0` guard |
| Summary sidebar display | Already correct — shows `totalCount` which is accumulated raw sum |
| `ThroughputLine.perSec` field | Keep field for now (no harm), just don't display it |
| Duration fix | Requires GT machine data access — out of scope for this pass |
