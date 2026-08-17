package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.BufferBuilder;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.menu.Menu;
import com.sbancuz.plannh.Config;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.client.ScreenEffect;
import com.sbancuz.plannh.client.UIBlurEffect;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.UndoHistory;
import com.sbancuz.plannh.data.flowchart.balancer.BalanceView;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.edge.ArrowWidget;
import com.sbancuz.plannh.gui.group.GroupWidget;
import com.sbancuz.plannh.gui.node.NodeWidget;
import com.sbancuz.plannh.gui.note.NoteWidget;
import com.sbancuz.plannh.layout.AutoLayout;
import com.sbancuz.plannh.nei.NEIPlanConfig;
import com.sbancuz.plannh.nei.NodeLookupContext;

import codechicken.lib.config.ConfigTag;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.IRecipeHandler;
import lombok.Getter;
import lombok.Setter;

public class CanvasWidget extends ParentWidget<CanvasWidget> implements Interactable, IViewport, IDraggable {

    public static final int GRID_SIZE = 20;
    private static final int GRID_MAJOR = 5;

    private static final int ARROW_COLOR_ITEM = PlannhColors.ARROW_ITEM.getColor();
    private static final int ARROW_COLOR_FLUID = PlannhColors.ARROW_FLUID.getColor();
    private static final int PREVIEW_COLOR = PlannhColors.PREVIEW_HIGHLIGHT.getColor();
    private static final int CLAMP_MARGIN = 4;
    private static final int NODE_W_ESTIMATE = 120;
    private static final int NODE_H_ESTIMATE = 80;
    private static final int AUTO_PLACE_GAP_X = 80;
    private static final int AUTO_PLACE_GAP_Y = 30;
    private static final int AUTO_PLACE_STAGGER = 20;
    private static final int HEADER_OFFSET = 24;
    private static final int PORT_S = 8;
    private static final int PORT_HALF = 4;
    private static final int PORT_GAP = 6;
    private static final int PORT_SPACING = 18;
    private static final int PORT_ORIGIN = 10;
    private static final int MIN_GRID_SPACING = 4;
    private static final int ARROW_SIZE = 6;
    private static final int ARROW_MIN_SIZE = 4;
    private static final int LINE_THICK_BASE = 2;
    private static final int LINE_THICK_MIN = 1;
    private static final float ARROW_HB_RATIO = 0.35f;
    private static final int EDGE_OUTLINE_EXTRA = 3;
    private static final int EDGE_MARGIN_BASE = 4;
    private static final int PORT_LABEL_MAX = 20;
    private static final int PORT_LABEL_TRUNC = 19;
    private static final int PORT_FONT_SIZE = 9;
    private static final float PORT_FONT_SCALE = 0.9f;
    private static final int PORT_LABEL_PAD = 2;

    /**
     * Gap in world units between a node's edge and the chip that hangs off it. Short on purpose:
     * every unit here is paid twice over in the layout, once by the node on each side of a corridor.
     */
    private static final int CHIP_GAP = 8;
    private static final int CHIP_H = 11;
    /**
     * Horizontal breathing room either side of the label, in world units.
     */
    private static final int CHIP_PAD_X = 3;
    /**
     * How far below the pin the chip hangs, in world units.
     */
    private static final int CHIP_DROP = 3;
    private static final float CHIP_TEXT_SCALE = 0.5f;
    /**
     * Below this zoom the labels are unreadable, so the chips are only clutter.
     */
    private static final float CHIP_MIN_ZOOM = 0.45f;

    private static final int GROUP_FIT_PAD = 12;
    private static final float ZOOM_STEP = 0.15f;
    private static final float ZOOM_MIN = 0.1f;
    private static final float ZOOM_MAX = 5.0f;

    // Orthogonal arrow routing (world-space units).
    private static final int ROUTE_CELL = 6;
    private static final int ROUTE_MARGIN = 12;
    private static final long ROUTE_HASH_SEED = 1125899906842597L;
    private static final ArrowRouter ARROW_ROUTER = new ArrowRouter(ROUTE_CELL, ROUTE_MARGIN);
    private static final Log log = LogFactory.getLog(CanvasWidget.class);

    @NotNull
    @Getter
    private Graph graph;
    private final Map<UUID, RecipeNodeWidget> nodeWidgets = new HashMap<>();
    @Getter
    private final Map<UUID, FlowchartWidget<?, ?>> flowchartWidgets = new HashMap<>();

    private boolean panning = false;
    private int panStartMouseX, panStartMouseY;
    private float panStartX, panStartY;

    private boolean creatingEdge = false;
    private UUID edgeSourceNodeId;
    private int edgeSourcePortIndex;
    private int edgeEndX, edgeEndY;
    private UUID edgeHoverNodeId;
    private int edgeHoverPortIndex;

    @Getter
    private boolean menuOpen;
    private final Menu<?> contextMenu;

    @Nullable
    private NodeLookupContext pendingLookup = null;
    @Setter
    @Nullable
    private Menu<?> targetEditorMenu = null;
    @Nullable
    private Node targetEditNode;
    private int targetEditOutput = -1;
    private boolean targetFocusPending;

    private final ModularPanel panel;

    private final ScreenEffect effect = new UIBlurEffect();
    private final ConfigTag showGrid;
    private final ConfigTag backgroundColor;

    /**
     * Cached arrow routes (world-space waypoints) keyed by edge id, plus the layout they were built for.
     */
    private final Map<UUID, List<int[]>> edgeRoutes = new HashMap<>();
    private long routeSignature = Long.MIN_VALUE;

    public CanvasWidget(Menu<?> menu, ModularPanel panel) {
        this.graph = Plan.getActiveGraph();
        this.panel = panel;

        showGrid = NEIClientConfig.getSetting(NEIPlanConfig.ConfigShowGrid.KEY);
        backgroundColor = NEIClientConfig.getSetting(NEIPlanConfig.ConfigBackgroundColor.KEY);

        full();
        marginBottom(18);

        contextMenu = menu;
        rebuildNoteWidgets();
        rebuildGroupWidgets();
        rebuildNodeWidgets();

        // TODO remove after testing
        child(new ArrowWidget());

        background(new DynamicDrawable(() -> new Rectangle().color(getBackgroundColor())));
    }

    public void setPendingLookup(@Nullable final NodeLookupContext lookup) {
        pendingLookup = lookup;
    }

    /**
     * Returns and clears the pending lookup: it wires at most one added recipe.
     */
    @Nullable
    public NodeLookupContext consumePendingLookup() {
        final NodeLookupContext result = pendingLookup;
        pendingLookup = null;
        return result;
    }

    /**
     * Positions an auto-wired node beside its lookup origin: producers left, consumers right,
     * in the first free slot down the column.
     */
    public void placeBesideOrigin(final Node added, final Node origin, final boolean addedFeedsOrigin) {
        final RecipeNodeWidget originWidget = nodeWidgets.get(origin.getId());
        final int originW = originWidget != null ? originWidget.worldWidth() : NODE_W_ESTIMATE;
        final int originH = originWidget != null ? originWidget.worldHeight() : NODE_H_ESTIMATE;
        final int baseX = Math.round(
            addedFeedsOrigin ? origin.getX() - originW - AUTO_PLACE_GAP_X : origin.getX() + originW + AUTO_PLACE_GAP_X);
        final int baseY = Math.round(origin.getY());
        int x;
        int y;
        int slot = 0;
        do {
            final int stagger = slot * AUTO_PLACE_STAGGER;
            x = addedFeedsOrigin ? baseX - stagger : baseX + stagger;
            y = baseY + slot * (originH + AUTO_PLACE_GAP_Y) + (slot + 1) * (PortGeometry.SPACING / 2);
            slot++;
        } while (overlapsAnyNode(x, y, originW, originH));
        added.setX(x);
        added.setY(y);
    }

    private boolean overlapsAnyNode(final int x, final int y, final int w, final int h) {
        for (final RecipeNodeWidget widget : nodeWidgets.values()) {
            final Node n = widget.getNode();
            if (x < n.getX() + widget.worldWidth() && n.getX() < x + w
                && y < n.getY() + widget.worldHeight()
                && n.getY() < y + h) {
                return true;
            }
        }
        return false;
    }

    public void setGraph(final Graph newGraph) {
        this.graph = newGraph;
        rebuildWidgets();
    }

    /**
     * One entry point, not three: removeAll() drops every child, so a partial rebuild loses the rest.
     */
    public void rebuildWidgets() {
        removeAll();
        flowchartWidgets.clear();
        rebuildNoteWidgets();
        rebuildGroupWidgets();
        rebuildNodeWidgets();
    }

    public void undoGraph() {
        // Ends any text-edit session first: its bracket must commit before the graph is swapped,
        // and committing after the redo snapshot is taken would clear the redo stack again.
        getContext().removeFocus();
        final UndoHistory history = PlanAPI.undoHistory();
        if (!history.canUndo()) return;
        adoptRestoredGraph(history.undo(graph));
    }

    public void redoGraph() {
        getContext().removeFocus();
        final UndoHistory history = PlanAPI.undoHistory();
        if (!history.canRedo()) return;
        adoptRestoredGraph(history.redo(graph));
    }

    // View and mode settings are not part of an edit, so they carry over from the live graph.
    // The active slot must adopt the restored graph, or the next save writes the pre-undo state back.
    private void adoptRestoredGraph(final Graph restored) {
        restored.setZoom(graph.getZoom());
        restored.setPanX(graph.getPanX());
        restored.setPanY(graph.getPanY());
        restored.setSnapToGrid(graph.isSnapToGrid());
        restored.setBalanceMode(graph.getBalanceMode());
        restored.setOpsMode(graph.isOpsMode());
        restored.setUndoHistory(graph.getUndoHistory());
        final Plan plan = Plan.getInstance();
        plan.getGraphs()
            .set(plan.getActiveIndex(), restored);
        setGraph(restored);
        PlanAPI.save();
    }

    // ── Target-rate editor ──
    // The node config panel is immediate-mode drawing, so it cannot host a text widget; the
    // editor is a screen-level menu (same pattern as the context menu) that this widget opens
    // and positions, with the value bridged through the two methods below.

    public boolean isTargetEditorOpen() {
        return targetEditNode != null;
    }

    public void openTargetEditor(final Node node, final int outputIndex) {
        targetEditNode = node;
        targetEditOutput = outputIndex;
        if (targetEditorMenu != null) {
            targetEditorMenu.pos(getContext().getAbsMouseX(), getContext().getAbsMouseY());
        }
        targetFocusPending = true;
    }

    /**
     * True exactly once per editor opening, and only while the editor is still open.
     */
    public boolean consumeTargetEditorFocus() {
        if (!targetFocusPending || targetEditNode == null) return false;
        targetFocusPending = false;
        return true;
    }

    public void closeTargetEditor() {
        targetEditNode = null;
        targetEditOutput = -1;
    }

    public double editedTargetRate() {
        if (targetEditNode == null) return 0;
        return targetEditNode.getTargetOutputRates()
            .getOrDefault(targetEditOutput, 0.0);
    }

    /**
     * Commits the typed rate as one undo step and closes the editor; 0 clears the pin.
     */
    public void setEditedTargetRate(final double rate) {
        final Node node = targetEditNode;
        final int out = targetEditOutput;
        if (node == null) return;
        PlanAPI.recordEdit(graph, () -> {
            if (rate <= 0) node.getTargetOutputRates()
                .remove(out);
            else node.getTargetOutputRates()
                .put(out, rate);
        });
        graph.markDirty();
        PlanAPI.save();
        closeTargetEditor();
    }

    public void moveGroupNodes(final UUID groupId, final int deltaX, final int deltaY) {
        /*
         * final Group group = graph.groups.get(groupId);
         * if (group == null) return;
         * for (final UUID nodeId : group.nodeIds) {
         * final Node node = graph.nodes.get(nodeId);
         * if (node == null) continue;
         * node.x += deltaX;
         * node.y += deltaY;
         * final RecipeNodeWidget w = nodeWidgets.get(nodeId);
         * if (w != null) {
         * // w.syncTransform(zoom, panX, graph.getPanY());
         * }
         * }
         */
    }

    public void setGroupNodesVisible(final UUID groupId, final boolean visible) {
        /*
         * final Group group = graph.groups.get(groupId);
         * if (group == null) return;
         * for (final UUID nodeId : group.nodeIds) {
         * if (visible) {
         * if (nodeWidgets.containsKey(nodeId)) continue;
         * final Node node = graph.nodes.get(nodeId);
         * if (node == null) continue;
         * addNodeWidget(node);
         * } else {
         * final RecipeNodeWidget w = nodeWidgets.remove(nodeId);
         * if (w != null) remove(w);
         * }
         * }
         */
    }

    public void recheckMembershipAndFit() {
        /*
         * for (final Node node : graph.getNodes()) {
         * updateNodeGroupMembership(node);
         * }
         * autoFitGroups();
         */
    }

    public void autoLayoutNodes() {
        if (graph.getNodes()
            .isEmpty()) return;

        // The widgets ARE the layout input (they implement LayoutNode); measure them first
        // because MUI2 culls off-viewport widgets and unmeasured nodes report stub sizes.
        int anchorX = Integer.MAX_VALUE;
        int anchorY = Integer.MAX_VALUE;
        for (final Node node : graph.getNodes()
            .values()) {
            final RecipeNodeWidget widget = nodeWidgets.get(node.getId());
            if (widget != null) widget.ensureRecipeHandler();
            anchorX = Math.min(anchorX, node.getX());
            anchorY = Math.min(anchorY, node.getY());
        }

        // ELK reports bad option/graph combinations by throwing, and its node placement recurses
        // per path, so a pathological chart can exhaust the stack. Both would otherwise leave a
        // mouse handler and crash the client with the chart unsaved; the chart is worth more than
        // the layout, so log and keep the current positions.
        final Map<UUID, int[]> positions;
        try {
            positions = AutoLayout.layout(
                nodeWidgets.values(),
                graph.getEdges()
                    .values(),
                chipMargins());
        } catch (final RuntimeException | StackOverflowError e) {
            PlanNH.LOG.error("Auto-layout failed; node positions left unchanged", e);
            return;
        }
        if (positions.isEmpty()) return;

        // Anchor the new layout's top-left where the chart's top-left used to be.
        int layoutMinX = Integer.MAX_VALUE;
        int layoutMinY = Integer.MAX_VALUE;
        for (final int[] pos : positions.values()) {
            layoutMinX = Math.min(layoutMinX, pos[0]);
            layoutMinY = Math.min(layoutMinY, pos[1]);
        }
        final int offsetX = anchorX - layoutMinX;
        final int offsetY = anchorY - layoutMinY;

        // Bracketed only from here: a layout that threw or produced nothing left the chart alone,
        // and an undo entry for a no-op move would make the button look like it did something.
        PlanAPI.recordEdit(graph, () -> {
            for (final Node node : graph.getNodes()
                .values()) {
                final int[] pos = positions.get(node.getId());
                if (pos == null) continue;
                node.setX(pos[0] + offsetX);
                node.setY(pos[1] + offsetY);
            }
            applyNodePositions();
        });
    }

    private void applyNodePositions() {
        for (final RecipeNodeWidget widget : nodeWidgets.values()) {
            widget.pos(
                widget.getNode()
                    .getX(),
                widget.getNode()
                    .getY());
        }
        recheckMembershipAndFit();
    }

    @Nullable
    public Group getGroupForNode(final UUID nodeId) {
        /*
         * for (final Group g : graph.getGroups()) {
         * if (g.nodeIds.contains(nodeId)) return g;
         * }
         */
        return null;
    }

    public void clampNodeToGroup(final Node node) {
        /*
         * final Group group = getGroupForNode(node.id);
         * if (group == null || !group.clampNodes) return;
         * if (node.x < group.x + CLAMP_MARGIN) node.x = group.x + CLAMP_MARGIN;
         * if (node.y < group.y + CLAMP_MARGIN + HEADER_OFFSET) node.y = group.y + CLAMP_MARGIN + HEADER_OFFSET;
         * if (node.x + NODE_W_ESTIMATE > group.x + group.width - CLAMP_MARGIN)
         * node.x = group.x + group.width - CLAMP_MARGIN - NODE_W_ESTIMATE;
         * if (node.y + NODE_H_ESTIMATE > group.y + group.height - CLAMP_MARGIN)
         * node.y = group.y + group.height - CLAMP_MARGIN - NODE_H_ESTIMATE;
         */
    }

    private void autoFitGroups() {
        /*
         * for (final Group group : graph.getGroups()) {
         * if (!group.autoResize || group.nodeIds.isEmpty()) continue;
         * fitGroupToChildren(group);
         * }
         */
    }

    public void fitGroupToChildren(final Group group) {
        /*
         * if (group.nodeIds.isEmpty()) return;
         * int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
         * int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
         * for (final UUID nid : group.nodeIds) {
         * final Node n = graph.nodes.get(nid);
         * if (n == null) continue;
         * if (n.x < minX) minX = n.x;
         * if (n.y < minY) minY = n.y;
         * if (n.x + NODE_W_ESTIMATE > maxX) maxX = n.x + NODE_W_ESTIMATE;
         * if (n.y + NODE_H_ESTIMATE > maxY) maxY = n.y + NODE_H_ESTIMATE;
         * }
         * if (minX == Integer.MAX_VALUE) return;
         * group.x = minX - GROUP_FIT_PAD;
         * group.y = minY - GROUP_FIT_PAD;
         * group.width = maxX - minX + GROUP_FIT_PAD * 2;
         * group.height = maxY - minY + GROUP_FIT_PAD * 2;
         * final GroupWidget gw = groupWidgets.get(group.id);
         * if (gw != null) {
         * // gw.syncTransform(zoom, panX, graph.getPanY());
         * }
         * // Move all nodes into the group if auto-sizing
         * for (final UUID nid : group.nodeIds) {
         * final Node n = graph.nodes.get(nid);
         * if (n == null) continue;
         * clampNodeToGroup(n);
         * final RecipeNodeWidget w = nodeWidgets.get(nid);
         * // if (w != null) w.syncTransform(zoom, panX, graph.getPanY());
         * }
         */
    }

    private void updateNodeGroupMembership(final Node node) {
        /*
         * for (final Group group : graph.getGroups()) {
         * if (group.collapsed) continue;
         * final boolean inside = node.x >= group.x && node.x < group.x + group.width
         * && node.y >= group.y
         * && node.y < group.y + group.height;
         * final boolean contained = group.nodeIds.contains(node.id);
         * if (inside && !contained) {
         * group.nodeIds.add(node.id);
         * } else if (!inside && contained) {
         * group.nodeIds.remove(node.id);
         * }
         * }
         */
    }

    public void rebuildNoteWidgets() {
        for (final Note note : graph.getNotes()
            .values()) child(new NoteWidget(this, note));
    }

    public void rebuildGroupWidgets() {
        for (final Group group : graph.getGroups()
            .values()) child(new GroupWidget(this, group));
    }

    public boolean isOutputPortHit(final int worldMx, final int worldMy) {
        for (final RecipeNodeWidget w : nodeWidgets.values()) {
            final int localMx = worldMx - Math.round(
                w.getNode()
                    .getX());
            final int localMy = worldMy - Math.round(
                w.getNode()
                    .getY());
            if (w.getOutputPortAt(localMx, localMy) >= 0) return true;
        }
        return false;
    }

    public void rebuildNodeWidgets() {
        for (final Node node : graph.getNodes()
            .values()) child(new NodeWidget(this, node));
    }

    public int getBackgroundColor() {
        return backgroundColor.getHexValue(PlannhColors.BACKGROUND.getColor());
    }

    public void setBackgroundColor(int color) {
        backgroundColor.setHexValue(color);
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        final int width = getArea().width;
        final int height = getArea().height;
        final int x = getArea().x;
        final int y = getArea().y;
        if (width <= 0 || height <= 0) return;

        super.draw(context, widgetTheme);
        effect.execute(x, y, width, height);
        if (showGrid.getIntValue(NEIPlanConfig.ConfigShowGrid.ON) == NEIPlanConfig.ConfigShowGrid.ON) {
            drawGrid(width, height);
        }

        drawArrows();
        drawExternalChips();

        if (creatingEdge) {
            drawPreviewLine();
        }

        drawHoveredPortLabels();
    }

    private void drawGrid(final int w, final int h) {
        final float spacing = GRID_SIZE * graph.getZoom();
        if (spacing < MIN_GRID_SPACING) return;

        final int gridColor = PlannhColors.GRID_LINE.getColor();
        final int majorColor = PlannhColors.GRID_MAJOR.getColor();

        final int firstKX = (int) Math.ceil(-graph.getPanX() / spacing);
        final float startX = graph.getPanX() + firstKX * spacing;
        for (float x = startX; x < w; x += spacing) {
            final int k = firstKX + Math.round((x - startX) / spacing);
            GuiDraw.drawRect(Math.round(x), 0, 1, h, k % GRID_MAJOR == 0 ? majorColor : gridColor);
        }

        final int firstKY = (int) Math.ceil(-graph.getPanY() / spacing);
        final float startY = graph.getPanY() + firstKY * spacing;
        for (float y = startY; y < h; y += spacing) {
            final int k = firstKY + Math.round((y - startY) / spacing);
            GuiDraw.drawRect(0, Math.round(y), w, 1, k % GRID_MAJOR == 0 ? majorColor : gridColor);
        }
    }

    private int widgetX(final RecipeNodeWidget w) {
        return Math.round(
            w.getNode()
                .getX() * graph.getZoom() + graph.getPanX());
    }

    private int widgetY(final RecipeNodeWidget w) {
        return Math.round(
            w.getNode()
                .getY() * graph.getZoom() + graph.getPanY());
    }

    private int portY(final int index) {
        return Math.round(((index + 1) * PORT_SPACING + PORT_ORIGIN) * graph.getZoom());
    }

    /**
     * World-space (un-zoomed) Y of a port relative to the node's top-left corner.
     */
    private static int portWorldY(final int index) {
        return (index + 1) * PORT_SPACING + PORT_ORIGIN;
    }

    private int worldWidth(final RecipeNodeWidget w) {
        return w.worldWidth();
    }

    private int worldHeight(final RecipeNodeWidget w) {
        return w.worldHeight();
    }

    /**
     * World-space rectangles the chips occupy, for the router to route around.
     */
    private List<ArrowRouter.Rect> chipRects() {
        final List<ArrowRouter.Rect> rects = new ArrayList<>();
        for (final BalanceView.Boundary flow : graph.boundary()) {
            final RecipeNodeWidget widget = nodeWidgets.get(
                flow.port()
                    .nodeId());
            if (widget == null) continue;
            final int index = flow.port()
                .portIndex();
            final int width = chipWorldWidth(flow);
            final boolean input = flow.port()
                .input();
            final int x = input ? widget.getNode()
                .getX() - CHIP_GAP
                - width
                : widget.getNode()
                    .getX() + worldWidth(widget)
                    + CHIP_GAP;
            final int y = widget.getNode()
                .getY() + portWorldY(index)
                + chipOffset(flow.kind(), CHIP_H, CHIP_DROP);
            rects.add(new ArrowRouter.Rect(x, y, width, CHIP_H));
        }
        return rects;
    }

    /**
     * Chip width in world units - the same measurement the drawing and the layout margin use.
     */
    private static int chipWorldWidth(final BalanceView.Boundary flow) {
        return CHIP_PAD_X * 2 + Math.round(
            Minecraft.getMinecraft().fontRenderer.getStringWidth(
                flow.label()
                    .render())
                * CHIP_TEXT_SCALE);
    }

    /**
     * World-space room to keep clear beside each node for its boundary chips, {@code {left, right}}
     * by node id. The chips are drawn, not laid out, so the layout would otherwise put the next
     * column exactly where "533.33mB/s Air" goes.
     */
    private Map<UUID, int[]> chipMargins() {
        final Map<UUID, int[]> margins = new HashMap<>();
        for (final BalanceView.Boundary flow : graph.boundary()) {
            final int width = CHIP_GAP + chipWorldWidth(flow);
            final int side = flow.port()
                .input() ? 0 : 1;
            final int[] margin = margins.computeIfAbsent(
                flow.port()
                    .nodeId(),
                k -> new int[2]);
            margin[side] = Math.max(margin[side], width);
        }
        return margins;
    }

    /**
     * Stub source and sink markers for everything crossing the chart's boundary. Derived from the
     * solve and never stored: they are not {@link Node}s and take no part in layout or routing.
     */
    private void drawExternalChips() {
        if (graph.getZoom() < CHIP_MIN_ZOOM) return;
        for (final BalanceView.Boundary flow : graph.boundary()) {
            drawChip(flow);
        }
    }

    /**
     * Where a chip sits relative to its pin. Terminals stay level with it - nothing else is
     * competing for that line. The two kinds that hang off a CONNECTED port step out of the way of
     * the edge already using it, and step opposite ways so the two are told apart at a glance:
     * a surplus leaving drops below, a shortfall arriving rides above.
     */
    private static int chipOffset(final BalanceView.Kind kind, final int chipHeight, final int drop) {
        return switch (kind) {
            case EXCESS -> drop;
            case IMPORT -> -drop - chipHeight;
            default -> -chipHeight / 2;
        };
    }

    /**
     * The wire colour for a boundary flow's ingredient, matching the edges that carry it.
     */
    private int leadColor(final BalanceView.Boundary flow) {
        final Node node = graph.getNodes()
            .get(
                flow.port()
                    .nodeId());
        if (node == null) return ARROW_COLOR_ITEM;
        final List<Port<?>> ports = flow.port()
            .input() ? node.getInputs() : node.getOutputs();
        final int index = flow.port()
            .portIndex();
        return index < 0 || index >= ports.size() ? ARROW_COLOR_ITEM
            : ports.get(index)
                .getArrowColor();
    }

    private void drawChip(final BalanceView.Boundary flow) {
        final RecipeNodeWidget widget = nodeWidgets.get(
            flow.port()
                .nodeId());
        if (widget == null) return;
        final boolean input = flow.port()
            .input();
        final int index = flow.port()
            .portIndex();

        // Kind decides the ink, the ingredient decides the frame, and the fill is the same
        // almost-opaque near-black plate for all of them (PORT_LABEL_BG): a per-kind translucent
        // tint over a dark canvas leaves green-on-charcoal text unreadable.
        final int textColor;
        switch (flow.kind()) {
            case EXCESS -> textColor = PlannhColors.ACCENT_GREEN2.getColor();
            case IMPORT -> textColor = PlannhColors.ACCENT_AMBER.getColor();
            case PRODUCT -> textColor = PlannhColors.ACCENT_GREEN2.getColor();
            default -> textColor = PlannhColors.ACCENT_BLUE2.getColor();
        }

        final float zoom = graph.getZoom();
        final float textScale = CHIP_TEXT_SCALE * zoom;
        final int textW = Math.round(
            Minecraft.getMinecraft().fontRenderer.getStringWidth(
                flow.label()
                    .render())
                * textScale);
        final int textH = Math.round(Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT * textScale);
        final int chipW = textW + Math.round(CHIP_PAD_X * 2 * zoom);
        final int chipH = Math.round(CHIP_H * zoom);
        final int thickness = Math.max(1, Math.round(zoom));

        final int gap = Math.round(CHIP_GAP * zoom);
        final int y = widgetY(widget) + portY(index) + chipOffset(flow.kind(), chipH, Math.round(CHIP_DROP * zoom));
        final int nodeRight = widgetX(widget) + Math.round(widget.getArea().width * zoom);
        final int x = input ? widgetX(widget) - gap - chipW : nodeRight + gap;

        // The stub reads as attached rather than floating: a lead from the pin to the chip edge,
        // in the ingredient's own wire colour so it matches the edges carrying the same thing. The
        // label keeps its kind colour - the line says WHAT, the text says what is happening to it.
        //
        // Drawn the way a machine-to-machine edge is drawn, contrast underlay and all: an oak-wood
        // brown hairline over a night-time world is invisible without one, and the lead was the
        // only wire on the canvas not getting that treatment.
        final int leadColor = leadColor(flow);
        final int outline = IngredientColors.outlineFor(leadColor);
        final float leadThick = Math.max(LINE_THICK_MIN, LINE_THICK_BASE * zoom);
        final int leadX = input ? x + chipW : nodeRight;
        final int pinY = widgetY(widget) + portY(index);
        final int[] leadXs = { leadX, leadX + gap };
        final int[] leadYs = { pinY, pinY };
        drawLineStrip(leadXs, leadYs, outline, leadThick + EDGE_OUTLINE_EXTRA);
        drawLineStrip(leadXs, leadYs, leadColor, leadThick);

        GuiDraw.drawRect(x, y, chipW, chipH, PlannhColors.CHIP_BG.getColor());
        // Framed in the ingredient's wire colour so the chip, its lead and the edges carrying the
        // same thing read as one run. No contrast ring around it: the frame already sits against an
        // opaque plate, so unlike the hairline lead it was never in danger of disappearing.
        GuiHelper.drawRectBorder(x, y, chipW, chipH, thickness, leadColor);
        // Centred in the box on both axes, measured rather than nudged: the label is what sizes
        // the chip, so the padding either side is the same number the width was built from.
        GuiDraw.drawText(
            flow.label()
                .render(),
            x + (chipW - textW) / 2,
            y + (chipH - textH) / 2,
            textScale,
            textColor,
            false);
    }

    /**
     * Converts a canvas-space X coordinate to world space.
     */
    private int toWorldX(final int cx) {
        return Math.round((cx - graph.getPanX()) / graph.getZoom());
    }

    /**
     * Converts a canvas-space Y coordinate to world space.
     */
    private int toWorldY(final int cy) {
        return Math.round((cy - graph.getPanY()) / graph.getZoom());
    }

    public int getCanvasMouseX() {
        return Math.round((getContext().getAbsMouseX() - getArea().x - graph.getPanX()) / graph.getZoom());
    }

    public int getCanvasMouseY() {
        return Math.round((getContext().getAbsMouseY() - getArea().y - graph.getPanY()) / graph.getZoom());
    }

    public int getCanvasScreenCenterX() {
        return Math.round(((float) getArea().width / 2 - getArea().x - graph.getPanX()) / graph.getZoom());
    }

    public int getCanvasScreenCenterY() {
        return Math.round(((float) getArea().height / 2 - getArea().y - graph.getPanY()) / graph.getZoom());
    }

    private static boolean containsPoint(final Area a, final int mx, final int my) {
        return mx >= a.x && mx < a.x + a.width && my >= a.y && my < a.y + a.height;
    }

    private static boolean containsPointInclusive(final Area a, final int mx, final int my) {
        return mx >= a.x && mx <= a.x + a.width && my >= a.y && my <= a.y + a.height;
    }

    /**
     * Recomputes orthogonal arrow routes when the graph layout changes.
     * Routes are stored in world space so panning does not invalidate them.
     */
    private void ensureRoutes() {
        final long signature = computeRouteSignature();
        if (signature == routeSignature) return;
        routeSignature = signature;

        edgeRoutes.clear();
        final List<ArrowRouter.Rect> obstacles = new ArrayList<>();
        for (final RecipeNodeWidget w : nodeWidgets.values()) {
            obstacles.add(
                new ArrowRouter.Rect(
                    w.getNode()
                        .getX(),
                    w.getNode()
                        .getY(),
                    worldWidth(w),
                    worldHeight(w)));
        }
        final List<ArrowRouter.Request> requests = new ArrayList<>();
        for (final Edge edge : graph.getEdges()
            .values()) {
            final RecipeNodeWidget src = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dst = nodeWidgets.get(edge.targetNodeId);
            if (src == null || dst == null) continue;
            final int sx = src.getNode()
                .getX() + worldWidth(src);
            final int sy = src.getNode()
                .getY() + portWorldY(edge.sourceOutputIndex);
            final int dx = dst.getNode()
                .getX();
            final int dy = dst.getNode()
                .getY() + portWorldY(edge.targetInputIndex);
            requests.add(new ArrowRouter.Request(edge.id, sx, sy, dx, dy));
        }

        // Chips are no-turn zones rather than obstacles: a chip sits directly on the approach to
        // its own port, so blocking it would seal the only way in and drop the edge to a
        // straight-line fallback that ignores everything. Passing behind a label is fine; turning
        // under one is what reads as the arrow terminating there.
        final Set<UUID> fellBack = new HashSet<>();
        edgeRoutes.putAll(ARROW_ROUTER.route(obstacles, chipRects(), requests, fellBack));

        // A fallback ignores every obstacle, so it is the one route that can end up crossing a
        // node or cornering under a label however the zones are set up. Worth saying out loud
        // rather than leaving someone to infer it from a screenshot.
        if (!fellBack.isEmpty()) {
            PlanNH.LOG
                .info("Arrow routing fell back for {} of {} edges: {}", fellBack.size(), requests.size(), fellBack);
        }

        if (!Config.debugRouteDump) return;

        // The routing input replays headlessly (ArrowRouter is Minecraft-free); dump it on
        // every recompute so any bad-looking route can be rebuilt from the dev log.
        PlanNH.LOG.info(reproDump(obstacles, requests));

        // A route several times longer than its direct distance means the router wrapped
        // around the chart; call it out so the dump above gets looked at.
        for (final ArrowRouter.Request q : requests) {
            final List<int[]> path = edgeRoutes.get(q.key());
            if (path == null) continue;
            int len = 0;
            for (int i = 1; i < path.size(); i++) {
                len += Math.abs(path.get(i)[0] - path.get(i - 1)[0]) + Math.abs(path.get(i)[1] - path.get(i - 1)[1]);
            }
            final int direct = Math.abs(q.dx() - q.sx()) + Math.abs(q.dy() - q.sy());
            if (len <= direct * 3 + 200) continue;
            PlanNH.LOG.info(
                "Arrow route wrapped: edge {} ({},{})->({},{}) len={} direct={}",
                q.key(),
                q.sx(),
                q.sy(),
                q.dx(),
                q.dy(),
                len,
                direct);
        }
    }

    private static String reproDump(final List<ArrowRouter.Rect> obstacles, final List<ArrowRouter.Request> requests) {
        final StringBuilder sb = new StringBuilder("Route repro: obstacles=");
        for (final ArrowRouter.Rect r : obstacles) {
            sb.append(r.x())
                .append(',')
                .append(r.y())
                .append(',')
                .append(r.w())
                .append(',')
                .append(r.h())
                .append(';');
        }
        sb.append(" requests=");
        for (final ArrowRouter.Request r : requests) {
            sb.append(r.sx())
                .append(',')
                .append(r.sy())
                .append(',')
                .append(r.dx())
                .append(',')
                .append(r.dy())
                .append(';');
        }
        return sb.toString();
    }

    private long computeRouteSignature() {
        long sig = ROUTE_HASH_SEED;
        // The chips are obstacles, so a re-solve that moves or renames one has to invalidate the
        // routes with it. The balance object's identity is the cheap proxy for "the chips changed":
        // it is memoized and replaced wholesale whenever the chart is re-solved, where rebuilding
        // every chip rectangle to hash it would run on each frame.
        sig = mixRouteHash(sig, System.identityHashCode(graph.balance()));
        for (final Edge edge : graph.getEdges()
            .values()) {
            final RecipeNodeWidget src = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dst = nodeWidgets.get(edge.targetNodeId);
            if (src == null || dst == null) continue;
            sig = mixRouteHash(sig, edge.id.getMostSignificantBits());
            sig = mixRouteHash(sig, edge.id.getLeastSignificantBits());
            sig = mixRouteHash(sig, edge.sourceOutputIndex);
            sig = mixRouteHash(sig, edge.targetInputIndex);
            sig = mixRouteHash(
                sig,
                src.getNode()
                    .getX());
            sig = mixRouteHash(
                sig,
                src.getNode()
                    .getY());
            sig = mixRouteHash(sig, worldWidth(src));
            sig = mixRouteHash(sig, worldHeight(src));
            sig = mixRouteHash(
                sig,
                dst.getNode()
                    .getX());
            sig = mixRouteHash(
                sig,
                dst.getNode()
                    .getY());
            sig = mixRouteHash(sig, worldWidth(dst));
            sig = mixRouteHash(sig, worldHeight(dst));
        }
        return sig;
    }

    private static long mixRouteHash(final long hash, final long value) {
        return hash * 31 + value;
    }

    private void drawArrows() {
        ensureRoutes();
        for (final Edge edge : graph.getEdges()
            .values()) {
            final RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            final Node srcNode = graph.getNodes()
                .get(edge.sourceNodeId);
            final int color = edgeColor(srcNode, edge.sourceOutputIndex);

            final List<int[]> route = edgeRoutes.get(edge.id);
            if (route != null && route.size() >= 2) {
                drawRoutedArrow(route, color);
                continue;
            }

            final float z2 = graph.getZoom();
            final int srcX = widgetX(srcWidget) + Math.round(worldWidth(srcWidget) * z2);
            final int srcY = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            final int dstX = widgetX(dstWidget);
            final int dstY = widgetY(dstWidget) + portY(edge.targetInputIndex);

            drawArrow(srcX, srcY, dstX, dstY, color);
        }
    }

    /** Edge color follows the ingredient flowing through it; type color as fallback. */
    private static int edgeColor(@Nullable final Node srcNode, final int outputIndex) {
        if (srcNode == null || outputIndex < 0
            || outputIndex >= srcNode.getOutputs()
                .size()) {
            return ARROW_COLOR_ITEM;
        }
        return srcNode.getOutputs()
            .get(outputIndex)
            .getArrowColor();
    }

    /**
     * Draws a multi-segment orthogonal arrow from cached world-space waypoints.
     */
    private void drawRoutedArrow(final List<int[]> route, final int color) {
        final int n = route.size();
        final int[] sx = new int[n];
        final int[] sy = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = Math.round(route.get(i)[0] * graph.getZoom() + graph.getPanX());
            sy[i] = Math.round(route.get(i)[1] * graph.getZoom() + graph.getPanY());
        }

        final float as = Math.max(ARROW_MIN_SIZE, ARROW_SIZE * graph.getZoom());
        final int x2 = sx[n - 1];
        final int y2 = sy[n - 1];
        // Stop the line at the arrow base so it does not poke through the head (last segment is horizontal).
        sx[n - 1] = Math.round(x2 - as);

        final float thickness = Math.max(LINE_THICK_MIN, LINE_THICK_BASE * graph.getZoom());
        // Contrast underlay so the colored line stays readable over any world background.
        final int outline = IngredientColors.outlineFor(color);
        drawLineStrip(sx, sy, outline, thickness + EDGE_OUTLINE_EXTRA);
        drawArrowHead(x2, y2, sx[n - 1], as * ARROW_HB_RATIO + EDGE_OUTLINE_EXTRA / 2f, outline);
        drawLineStrip(sx, sy, color, thickness);
        drawArrowHead(x2, y2, sx[n - 1], as * ARROW_HB_RATIO, color);
    }

    private void drawArrow(final int x1, final int y1, final int x2, final int y2, final int color) {
        final float as = Math.max(ARROW_MIN_SIZE, ARROW_SIZE * graph.getZoom());
        final int ex = Math.round(x2 - as);
        final float thickness = Math.max(LINE_THICK_MIN, LINE_THICK_BASE * graph.getZoom());
        final int outline = IngredientColors.outlineFor(color);
        drawOrthogonalLine(x1, y1, x2, y2, ex, outline, thickness + EDGE_OUTLINE_EXTRA);
        drawArrowHead(x2, y2, ex, as * ARROW_HB_RATIO + EDGE_OUTLINE_EXTRA / 2f, outline);
        drawOrthogonalLine(x1, y1, x2, y2, ex, color, thickness);
        drawArrowHead(x2, y2, ex, as * ARROW_HB_RATIO, color);
    }

    private void drawPreviewLine() {
        final RecipeNodeWidget srcWidget = nodeWidgets.get(edgeSourceNodeId);
        if (srcWidget == null) return;

        final int x1 = widgetX(srcWidget) + Math.round(worldWidth(srcWidget) * graph.getZoom());
        final int y1 = widgetY(srcWidget) + portY(edgeSourcePortIndex);

        int x2 = edgeEndX;
        int y2 = edgeEndY;

        if (edgeHoverNodeId != null) {
            final RecipeNodeWidget dstWidget = nodeWidgets.get(edgeHoverNodeId);
            if (dstWidget != null) {
                x2 = widgetX(dstWidget);
                y2 = widgetY(dstWidget) + portY(edgeHoverPortIndex);
            }
        }

        drawOrthogonalLine(
            x1,
            y1,
            x2,
            y2,
            x2,
            PREVIEW_COLOR,
            Math.max(LINE_THICK_MIN, LINE_THICK_BASE * graph.getZoom()));
    }

    private void drawOrthogonalLine(final int x1, final int y1, final int x2, final int y2, final int xEnd,
        final int color, final float thickness) {
        final int midX = (x1 + x2) / 2;
        drawLineStrip(new int[] { x1, midX, midX, xEnd }, new int[] { y1, y1, y2, y2 }, color, thickness);
    }

    private static void drawLineStrip(final int[] xs, final int[] ys, final int color, final float thickness) {
        final int r = Color.getRed(color);
        final int g = Color.getGreen(color);
        final int b = Color.getBlue(color);
        final int a = Color.getAlpha(color);
        Platform.setupDrawColor();
        GL11.glLineWidth(thickness);
        Platform.startDrawing(Platform.DrawMode.LINE_STRIP, Platform.VertexFormat.POS_COLOR, buf -> {
            for (int i = 0; i < xs.length; i++) {
                addColoredVertex(buf, xs[i], ys[i], r, g, b, a);
            }
        });
        GL11.glLineWidth(1);
    }

    private static void drawArrowHead(final int tipX, final int tipY, final int baseX, final float halfBase,
        final int color) {
        final int r = Color.getRed(color);
        final int g = Color.getGreen(color);
        final int b = Color.getBlue(color);
        final int a = Color.getAlpha(color);
        Platform.setupDrawColor();
        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR, buf -> {
            addColoredVertex(buf, tipX, tipY, r, g, b, a);
            addColoredVertex(buf, baseX, Math.round(tipY - halfBase), r, g, b, a);
            addColoredVertex(buf, baseX, Math.round(tipY + halfBase), r, g, b, a);
        });
    }

    private static void addColoredVertex(final BufferBuilder buf, final int x, final int y, final int r, final int g,
        final int b, final int a) {
        buf.pos(x, y, 0)
            .color(r, g, b, a)
            .endVertex();
    }

    @Nullable
    private Edge getEdgeAt(final int absMx, final int absMy) {
        ensureRoutes();
        final int margin = Math.max(EDGE_MARGIN_BASE, Math.round(EDGE_MARGIN_BASE * graph.getZoom()));
        final int cmx = absMx - getArea().x;
        final int cmy = absMy - getArea().y;
        for (final Edge edge : graph.getEdges()
            .values()) {
            final List<int[]> route = edgeRoutes.get(edge.id);
            if (route == null || route.size() < 2) continue;
            for (int i = 0; i < route.size() - 1; i++) {
                final int x1 = Math.round(route.get(i)[0] * graph.getZoom() + graph.getPanX());
                final int y1 = Math.round(route.get(i)[1] * graph.getZoom() + graph.getPanY());
                final int x2 = Math.round(route.get(i + 1)[0] * graph.getZoom() + graph.getPanX());
                final int y2 = Math.round(route.get(i + 1)[1] * graph.getZoom() + graph.getPanY());
                // Segments are axis-aligned, so this is a simple inflated-rectangle test.
                if (cmx >= Math.min(x1, x2) - margin && cmx <= Math.max(x1, x2) + margin
                    && cmy >= Math.min(y1, y2) - margin
                    && cmy <= Math.max(y1, y2) + margin) {
                    return edge;
                }
            }
        }
        return null;
    }

    private boolean canConnect(final Node srcNode, final int srcOutIdx, final Node dstNode, final int dstInIdx) {
        if (srcNode == dstNode) return false;
        if (srcOutIdx < 0 || dstInIdx < 0) return false;
        if (srcOutIdx >= srcNode.getOutputs()
            .size() || dstInIdx
                >= dstNode.getInputs()
                    .size())
            return false;
        return srcNode.getOutputs()
            .get(srcOutIdx)
            .canConnect(
                dstNode.getInputs()
                    .get(dstInIdx));
    }

    @Override
    public @NotNull Result onMousePressed(final int mouseButton) {
        final int absMx = getContext().getAbsMouseX();
        final int absMy = getContext().getAbsMouseY();

        // Close context menu on any click
        menuOpen = false;

        if (mouseButton == 0) {
            final int cmx = absMx - getArea().x;
            final int cmy = absMy - getArea().y;
            final float z = graph.getZoom();
            final int worldMx = Math.round((cmx - graph.getPanX()) / z);
            final int worldMy = Math.round((cmy - graph.getPanY()) / z);
            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                final int localMx = worldMx - Math.round(
                    widget.getNode()
                        .getX());
                final int localMy = worldMy - Math.round(
                    widget.getNode()
                        .getY());
                final int port = widget.getOutputPortAt(localMx, localMy);
                if (port >= 0) {
                    creatingEdge = true;
                    edgeSourceNodeId = widget.getNode()
                        .getId();
                    edgeSourcePortIndex = port;
                    edgeEndX = cmx;
                    edgeEndY = cmy;
                    edgeHoverNodeId = null;
                    edgeHoverPortIndex = -1;
                    return Result.SUCCESS;
                }
            }

            if (!isMouseOverAnyNode(absMx, absMy) && !isMouseOverAnyGroup(absMx, absMy)) {
                final Edge clicked = getEdgeAt(absMx, absMy);
                if (clicked != null) {
                    PlanAPI.recordEdit(graph, () -> graph.removeEdge(clicked.id));
                    return Result.SUCCESS;
                }
                return Result.ACCEPT;
            }
            return Result.IGNORE;
        }
        if (mouseButton == 1) {
            final int cmx = absMx - getArea().x;
            final int cmy = absMy - getArea().y;

            /*
             * // Check if over a group header (pass click through for its own right-click menu)
             * for (final GroupWidget gw : groupWidgets.values()) {
             * final Area a = gw.getArea();
             * if (containsPoint(a, absMx, absMy)) {
             * showGroupContextMenu(gw, absMx, absMy);
             * return Result.SUCCESS;
             * }
             * }
             * // Check if over a node
             * for (final RecipeNodeWidget widget : nodeWidgets.values()) {
             * final Area a = widget.getArea();
             * if (containsPoint(a, absMx, absMy)) {
             * showNodeContextMenu(widget, absMx, absMy);
             * return Result.SUCCESS;
             * }
             * }
             */

            // Empty canvas -> open context menu
            openContextMenu();
            return Result.SUCCESS;
        }
        return Result.IGNORE;
    }

    private void openContextMenu() {
        contextMenu.pos(getContext().getAbsMouseX(), getContext().getAbsMouseY());
        menuOpen = true;
    }

    @Override
    public boolean onMouseRelease(final int mouseButton) {
        if (creatingEdge) {
            if (edgeHoverNodeId != null) {
                final Node srcNode = graph.getNodes()
                    .get(edgeSourceNodeId);
                final Node dstNode = graph.getNodes()
                    .get(edgeHoverNodeId);
                if (srcNode != null && dstNode != null) {
                    PlanAPI.recordEdit(
                        graph,
                        () -> graph.addEdge(
                            new Edge(
                                UUID.randomUUID(),
                                edgeSourceNodeId,
                                edgeHoverNodeId,
                                edgeSourcePortIndex,
                                edgeHoverPortIndex)));
                }
            }
            creatingEdge = false;
            edgeHoverNodeId = null;
            edgeHoverPortIndex = -1;
            return true;
        }
        return true;
    }

    @Override
    public void onMouseDrag(final int mouseButton, final long timeSinceClick) {
        if (creatingEdge) {
            final int cmx = getContext().getAbsMouseX() - getArea().x;
            final int cmy = getContext().getAbsMouseY() - getArea().y;
            edgeEndX = cmx;
            edgeEndY = cmy;

            edgeHoverNodeId = null;
            edgeHoverPortIndex = -1;

            final RecipeNodeWidget srcWidget = nodeWidgets.get(edgeSourceNodeId);
            if (srcWidget == null) return;

            final float z = graph.getZoom();
            final int worldDragMx = Math.round((cmx - graph.getPanX()) / z);
            final int worldDragMy = Math.round((cmy - graph.getPanY()) / z);
            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                if (widget == srcWidget) continue;
                final int localMx = worldDragMx - Math.round(
                    widget.getNode()
                        .getX());
                final int localMy = worldDragMy - Math.round(
                    widget.getNode()
                        .getY());
                final int port = widget.getInputPortAt(localMx, localMy);
                if (port >= 0 && canConnect(srcWidget.getNode(), edgeSourcePortIndex, widget.getNode(), port)) {
                    edgeHoverNodeId = widget.getNode()
                        .getId();
                    edgeHoverPortIndex = port;
                    break;
                }
            }
        }
    }

    @Override
    public boolean onMouseScroll(final UpOrDown direction, final int amount) {
        // Close context menu on any scroll ?
        // menuOpen = false;

        float delta = direction == UpOrDown.UP ? ZOOM_STEP : -ZOOM_STEP;
        delta *= Math.max(1, Math.abs(amount));
        final float oldZoom = graph.getZoom();
        graph.setZoom(Math.clamp(graph.getZoom() + delta, ZOOM_MIN, ZOOM_MAX));
        final float ratio = graph.getZoom() / oldZoom;

        final float mxRel = getContext().getAbsMouseX() - getArea().x;
        final float myRel = getContext().getAbsMouseY() - getArea().y;

        graph.setPanX(mxRel - (mxRel - graph.getPanX()) * ratio);
        graph.setPanY(myRel - (myRel - graph.getPanY()) * ratio);

        // updatePositions();
        return true;
    }

    private boolean isMouseOverAnyGroup(final int mx, final int my) {
        /*
         * for (final GroupWidget gw : groupWidgets.values()) {
         * final Area a = gw.getArea();
         * if (containsPointInclusive(a, mx, my)) {
         * return true;
         * }
         * }
         */
        return false;
    }

    private boolean isMouseOverAnyNode(final int mx, final int my) {
        /*
         * for (final RecipeNodeWidget widget : nodeWidgets.values()) {
         * final Area a = widget.getArea();
         * if (containsPointInclusive(a, mx, my)) {
         * return true;
         * }
         * }
         */
        return false;
    }

    public void addNote(int x, int y) {
        PlanAPI.recordEdit(graph, () -> {
            Note note = new Note();
            note.setX(x);
            note.setY(y);

            graph.getNotes()
                .put(note.getId(), note);
            child(new NoteWidget(this, note));
        });

        menuOpen = false;
    }

    public void addGroup(int x, int y) {
        PlanAPI.recordEdit(graph, () -> {
            Group group = new Group();
            group.setX(x);
            group.setY(y);

            graph.getGroups()
                .put(group.getId(), group);
            child(new GroupWidget(this, group));
        });

        menuOpen = false;
    }

    public void addNode(int x, int y, IRecipeHandler handler, int recipeIndex) {
        PlanAPI.recordEdit(graph, () -> {
            Node node = new Node(handler, recipeIndex);
            node.setX(x);
            node.setY(y);

            graph.getNodes()
                .put(node.getId(), node);

            child(new NodeWidget(this, node));
        });

        menuOpen = false;
    }

    private void showGroupContextMenu(final GroupWidget gw, final int cmx, final int cmy) {
        /*
         * final Group group = gw.getGroup();
         * final List<ContextMenuWidget.MenuItem> items = new ArrayList<>();
         * items.add(new ContextMenuWidget.MenuItem("Rename Group", () -> startEditingGroup(group.id)));
         * items.add(new ContextMenuWidget.MenuItem("Customize Color", () -> openColorPickerFor(group)));
         * items.add(
         * new ContextMenuWidget.MenuItem(
         * group.clampNodes ? "Disable Clamp" : "Enable Clamp",
         * () -> group.clampNodes = !group.clampNodes));
         * items.add(
         * new ContextMenuWidget.MenuItem(
         * group.autoResize ? "Disable Auto-Resize" : "Enable Auto-Resize",
         * () -> group.autoResize = !group.autoResize));
         * items.add(new ContextMenuWidget.MenuItem("Delete Group", () -> removeGroup(group.id)));
         * contextMenuAt(cmx, cmy, items);
         */
    }

    private void showNodeContextMenu(final RecipeNodeWidget nw, final int cmx, final int cmy) {
        /*
         * final Node node = nw.getNode();
         * final List<ContextMenuWidget.MenuItem> items = new ArrayList<>();
         * final Group currentGroup = getGroupForNode(node.getId());
         * if (currentGroup != null) {
         * items.add(
         * new ContextMenuWidget.MenuItem(
         * "Remove from \"" + currentGroup.title + "\"",
         * () -> currentGroup.nodeIds.remove(node.getId())));
         * }
         * final List<Group> otherGroups = new ArrayList<>();
         * for (final Group g : graph.getGroups()) {
         * if (g != currentGroup) otherGroups.add(g);
         * }
         * if (!otherGroups.isEmpty()) {
         * for (final Group g : otherGroups) {
         * items.add(new ContextMenuWidget.MenuItem("Add to \"" + g.title + "\"", () -> g.nodeIds.add(node.getId())));
         * }
         * }
         * if (items.isEmpty()) return;
         * contextMenuAt(cmx, cmy, items);
         */
    }

    private void drawHoveredPortLabels() {
        final int mouseX = getContext().getMouseX();
        final int mouseY = getContext().getMouseY();
        final float z = graph.getZoom();
        final float px = graph.getPanX();
        final float py = graph.getPanY();
        final int ps = Math.max(1, Math.round(PORT_S * z));

        for (final RecipeNodeWidget w : nodeWidgets.values()) {
            final Node node = w.getNode();
            final int worldW = w.worldWidth();

            // Output ports (right side)
            for (int i = 0; i < node.getOutputs()
                .size(); i++) {
                final int sx = Math.round((node.getX() + worldW - PORT_S) * z + px);
                final int sy = Math.round((node.getY() + portWorldY(i) - PORT_HALF) * z + py);
                if (mouseX >= sx && mouseX < sx + ps && mouseY >= sy && mouseY < sy + ps) {
                    final String label = portLabel(
                        node.getOutputs()
                            .get(i));
                    if (label != null) {
                        final int ax = Math.round((node.getX() + worldW) * z + px);
                        final int ay = Math.round((node.getY() + portWorldY(i)) * z + py);
                        drawPortLabel(label, ax, ay, true, z);
                    }
                    return;
                }
            }

            // Input ports (left side)
            for (int i = 0; i < node.getInputs()
                .size(); i++) {
                final int sx = Math.round(node.getX() * z + px);
                final int sy = Math.round((node.getY() + portWorldY(i) - PORT_HALF) * z + py);
                if (mouseX >= sx && mouseX < sx + ps && mouseY >= sy && mouseY < sy + ps) {
                    final String label = portLabel(
                        node.getInputs()
                            .get(i));
                    if (label != null) {
                        final int ax = Math.round(node.getX() * z + px);
                        final int ay = Math.round((node.getY() + portWorldY(i)) * z + py);
                        drawPortLabel(label, ax, ay, false, z);
                    }
                    return;
                }
            }
        }
    }

    @Nullable
    private static String portLabel(final Port port) {
        final String name = port.getDisplayName();
        return name.isEmpty() ? null : name;
    }

    private static void drawPortLabel(String name, final int anchorX, final int centerY, final boolean rightSide,
        final float z) {
        if (name.length() > PORT_LABEL_MAX) name = name.substring(0, PORT_LABEL_TRUNC) + "…";
        final int tw = Minecraft.getMinecraft().fontRenderer.getStringWidth(name);
        final int fh = Math.round(PORT_FONT_SIZE * z * PORT_FONT_SCALE);
        final int gap = Math.round(PORT_GAP * z);
        final int labelX = rightSide ? anchorX + gap : anchorX - tw - gap;
        final int labelY = centerY - fh / 2;
        final int pad = Math.round(PORT_LABEL_PAD * z);
        GuiDraw.drawRect(labelX - pad, labelY - pad, tw + pad * 2, fh + pad * 2, PlannhColors.PORT_LABEL_BG.getColor());
        GuiDraw.drawText(name, labelX, labelY, z * 0.9f, PlannhColors.TEXT_WHITE.getColor(), false);
    }

    @Override
    public void transformChildren(IViewportStack stack) {
        stack.translate(graph.getPanX(), graph.getPanY());
        stack.scale(graph.getZoom(), graph.getZoom());
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean transformed) {
        if (!transformed) {
            Stencil.applyAtZero(getArea(), context);
        }
    }

    @Override
    public void postDraw(ModularGuiContext context, boolean transformed) {
        if (!transformed) {
            Stencil.remove();
        }
    }

    @Override
    public void drawMovingState(ModularGuiContext context, float partialTicks) {}

    @Override
    public boolean onDragStart(int button) {
        if (button == 0 || button == 2) {
            panStartX = graph.getPanX();
            panStartY = graph.getPanY();
            panStartMouseX = getContext().getAbsMouseX();
            panStartMouseY = getContext().getAbsMouseY();
            return true;
        }
        return false;
    }

    @Override
    public void onDragEnd(boolean successful) {
        if (!successful) {
            graph.setPanX(panStartX);
            graph.setPanY(panStartY);
        }
    }

    @Override
    public void onDrag(int mouseButton, long timeSinceLastClick) {
        final int dx = getContext().getAbsMouseX() - panStartMouseX;
        final int dy = getContext().getAbsMouseY() - panStartMouseY;
        graph.setPanX(panStartX + dx);
        graph.setPanY(panStartY + dy);
    }

    @Override
    public @Nullable Area getMovingArea() {
        return null;
    }

    @Override
    public boolean isMoving() {
        return panning;
    }

    @Override
    public void setMoving(boolean moving) {
        panning = moving;
    }

    public boolean isMouseInsideCanvas() {
        ModularGuiContext context = getContext();
        Area area = getArea();
        return isInside(context, context.getAbsMouseX() - area.x, context.getAbsMouseY() - area.y, false);
    }

    @Override
    public @NotNull ModularPanel getPanel() {
        return panel;
    }
}
