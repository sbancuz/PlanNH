<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/plannhlogo4x.png">
  <img alt="PlanNH" src="src/main/resources/plannhlogo4x.png" width="606">
</picture>

In-game flowchart-based production planner for Minecraft 1.7.10 for the GTNH pack.

# Disclaimer

We are currently in the middle of a big rewrite, master brach may not show everything, as a replacement you could try to compile the balancer branch that has a couple of bandaid fixes that were used just for testing, but you must add this [commit](https://github.com/sbancuz/PlanNH/commit/4061adb9a9dd2e2a15a31ba4f6ede2e6dfb10f3d) manually

Also in the jar releases if you manage to launch in game, there is an already fixed bug: items with burn time will not show in the nodes

## Features

- **Canvas.** Infinite 2D whiteboard to organize complex recipe chain.
- **NEI Recipes.** Recipes can be added using NEI and are shown through the normal recipe widgets.
- **Groups.** Collapsible and customizable groups to keep the recipes organized
- **Sticky notes.** To provide extra context or to just draw a smily face
- **Production balancer.** You can balance chains for optimal throuput by pinning recipe nodes, uses [ojAlgo](https://github.com/optimatika/ojAlgo) to provide a clean solution 
- **Summary.** Shows the overall demands and outputs of a recipe chains, ranging from items, fluids, energy and even magic resources like mana
- **Mod integrations.** GregTech, EnderIO, Thaumcraft, Botania, Forestry, Et Futurum Requiem and many more to come
- **Machine configuration.** Mod-specific configuration options to calculate recipe throughput like voltage, overcloks or speed modifiers
- **Share and import.** Share your charts to other players either via the in-game chat or externally via the clipboard
- **Mermaid export.** Export graphs as Mermaid.js flowcharts for [GuideNH](https://github.com/GTNewHorizons/GuideNH).
- **Client side only.** No server binary required!

## Dependencies

- Minecraft **1.7.10** with Forge
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
