<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/plannhlogo4x.png">
  <img alt="PlanNH" src="src/main/resources/plannhlogo4x.png" width="606">
</picture>

In-game flowchart-based production planner for Minecraft 1.7.10 GTNH.

## Features

- **Group system.** Color picker, collapsible groups, cover children mode, and automatic membership when dropping nodes on a group.
- **Canvas.** Infinite 2D whiteboard to organize complex recipe chain.
- **Production balancer.** [ojAlgo](https://github.com/optimatika/ojAlgo) ILP solver with OUTPUT and INPUT modes, plus an ops mode toggle. Machine count is the single source of truth and a Fixed checkbox pins values in the solver.
- **Summary.** Collapsible and scrollable with extended resource consumption info including GT steam. A dirty flag caches results so the balancer only recomputes on changes.
- **Machine configuration.** Per-node overclock, parallel, voltage, heat, and laser OC settings with type-safe profiles. Easier config selection and better slot renaming.
- **Mod integrations.** GregTech, EnderIO, Thaumcraft, Botania, Forestry, Et Futurum Requiem and many more to come
- **Share and import.** Encode graphs as NEI item-link chat messages or clipboard with popup notifiers and preview shadows while dragging.
- **Mermaid export.** Export graphs as Mermaid.js flowcharts for [GuideNH](https://github.com/GTNewHorizons/GuideNH).
- **Client side only.** 

## Dependencies

- Minecraft **1.7.10** with Forge **10.13.4.1614+**
- **Not Enough Items**, **ModularUI**, **GTNHLib**, **Mixin**

## Credits

- **[TheYoingLad](https://github.com/TheYoingLad)** for the UI code 
- **[FlyToSpace](https://github.com/FlyToSpace)** for the logo design

## Building

```bash
./gradlew build
```

## License

MIT — see [LICENSE](LICENSE).
