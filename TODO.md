# Implementation Plan: NEI Flowchart

## Phase 1: Data Model (Foundation)

- [ ] **1.1 FlowchartNode** — Data class: position (x,y), NEI recipe reference (itemStack input/output, machine, duration), unique ID
- [ ] **1.2 FlowchartGraph** — Holds list of nodes + list of edges (source ID → target ID). Provides cost/time aggregation methods
- [ ] **1.3 FlowchartSerializer** — Serialize/deserialize `FlowchartGraph` to/from base64 (JSON → GZIP → base64, or similar)

## Phase 2: NEI Integration

- [ ] **2.1 RecipeLookup** — Wrapper around NEI's `RecipeRegistry` to get `CachedRecipe` objects for a given item/output. Returns inputs/outputs/machine/duration
- [ ] **2.2 NEIFlowchartPlugin** — Implement `INEIConfig` / `INEIGuiHandler` to register a new NEI "tab" or button that opens the flowchart panel

## Phase 3: MUI2 GUI (Core)

- [ ] **3.1 FlowchartScreen** — MUI2 `ModularScreen` implementing the main flowchart editor. Uses MUI2 theming. Canvas area with pan/zoom
- [ ] **3.2 RecipeNodeWidget** — Custom MUI2 `Widget` rendering a recipe card: input slots, output slots, machine icon, duration text. Draggable
- [ ] **3.3 ArrowWidget** — Draws bezier/lines between node outputs and inputs. Updates when nodes move
- [ ] **3.4 ZoomController** — Zoom/pan via scrollwheel + drag. Affine transform applied to the canvas
- [ ] **3.5 InfoPanel** — Sidebar/dialog showing aggregated cost & timing for the selected node or entire graph

## Phase 4: MUI2 GUI (Interaction)

- [ ] **4.1 RecipePicker** — MUI2 window triggered by "Add Node" button. Uses NEI's `ResourcePack` or MUI2 `NEIRecipeWidget` to browse/search recipes. Selecting one creates a new node
- [ ] **4.2 ArrowTool** — Click-drag from a node's output port to another node's input port to create an edge. Right-click to delete
- [ ] **4.3 NodeContextMenu** — Right-click node: delete node, view recipe detail, copy node
- [ ] **4.4 Save/Load + Share** — Buttons in toolbar: Save → serialize graph to base64 → persist to file. Share → copy base64 string to clipboard. Load → paste base64 import

## Phase 5: Persistence & Polish

- [ ] **5.1 WorldSaveHandler** — Save/load flowchart data per-world (or per-player via `ExtendedEntityProperties`). Hook into `FMLServerStoppingEvent` for save
- [ ] **5.2 ClipboardIO** — System clipboard integration for base64 import/export
- [ ] **5.3 L10n/Config** — Add config options (default zoom, grid snap, theme toggle). Localize strings
- [ ] **5.4 Testing** — Manual testing with actual GTNH recipes (e.g., PCB factory chain)
