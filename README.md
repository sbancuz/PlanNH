<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/plannhlogo4x.png">
  <img alt="PlanNH" src="src/main/resources/plannhlogo4x.png" width="606">
</picture>

In-game flowchart-based production planner for Minecraft 1.7.10 for the GTNH pack.

# Disclaimer

We are currently in the middle of a big rewrite of the UI, master brach has a lot of UI bugs that will be resolved in the rewrite, please do not report any bugs relative do this

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
