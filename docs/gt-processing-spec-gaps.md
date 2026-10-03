# What GregTech's ProcessingSpec does not yet tell PlanNH

PlanNH plans a GregTech multiblock only from the `ProcessingSpec` the machine declares
(`GTMachineSpec`). A multiblock without one shows "(no ProcessingSpec interface)" in the picker and
keeps the recipe's own numbers. Singleblocks plan through GregTech's own `OverclockDescriber`.
`/plannh_machines` writes every machine, and what drives its numbers, to `plannh-machines.md`.

Values a machine builds up while running (`ModifierKind.Source.RUNTIME`, such as the Industrial
Centrifuge's momentum) are planned at the spec's best and offer no row. The table's `assumes`
column lists them.

Every other value has one row, keyed by `GTSettings.structureKey` and named by the kind. An
untouched row opens on the chart's floor for that kind (coil and pipe casing have one), raised by
`ProcessingSpec.lowestPassing` to the lowest value that runs the recipe, and else on the spec's best.
A run's success chance and yield scale the node's outputs, and its start-up and per-run EU are
stated on the node.

## Machines without a spec

Most multiblocks declare no spec yet, and converting them is GregTech-side work. A spec can read
items in the machine, the amps that reach it, the recipe, and any structure value, so a machine
with no spec needs converting, not a new kind of input. Two groups need more than a conversion.

**Machines that never overclock in ways `noOverclock()` cannot state.** `noOverclock()` runs a
recipe at its own voltage, as `OverclockCalculator.ofNoOverclock` does. These differ:

- **Algae Farm** - recipe EU/t is 90% of the energy hatch's voltage, not the recipe's.
- **BEC I/O Node** - disables overclocks but keeps the hatch voltage as the cap; parallel is a
  GUI setting.
- **Transcendent Plasma Mixer** - wireless power; parallel is a GUI setting.
- **Liquid Fluoride Thorium Reactor** - a generator; power comes from each recipe's metadata.

**Machines with no ProcessingLogic.** Their own `checkProcessing` does not use `ProcessingLogic`,
so they must run their recipes through `ProcessingSpec.calculate`, as the Eye of Harmony does: the
eleven nanochip assembly modules, the Integrated Ore Factory, the Tree Growth Simulator and the
Steam Water Pump.

## PlanNH-side gaps

- **A recipe GregTech would refuse gets no machine numbers.** When a spec's requirement fails at
  the node's structure (a coil too cold, a steam multiblock below the recipe's voltage), the node
  keeps the recipe's own numbers and shows GregTech's reason in red. A chart that has not been
  solved yet has no balance to carry it, so it shows nothing there.
- **Steam multiblocks are planned in EU, not steam.** GregTech burns 2 L of steam per EU.
- **The Eye of Harmony's per-run EU is stated, not planned.** It moves through the wireless
  network once per run, which a node's EU/t does not model.
- **Charts saved before this change lose two things.** The Eye of Harmony's astral array count
  moved from `catalyst_astral_arrays` to the spec's `gt_tectech_astral_arrays` row. Nodes on the
  removed manual "GT Steam" profile have no profile. A coil saved as GregTech's coil level name is
  migrated to the coil tier on load.
