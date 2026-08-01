package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

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
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.client.ScreenEffect;
import com.sbancuz.plannh.client.UIBlurEffect;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.GraphData;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.data.flowchart.Plan;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.Step;
import com.sbancuz.plannh.gui.step.StepWidget;
import com.sbancuz.plannh.nei.NEIPlanConfig;

import codechicken.lib.config.ConfigTag;
import codechicken.nei.NEIClientConfig;
import lombok.Getter;

public class CanvasWidget extends ParentWidget<CanvasWidget> implements Interactable, IViewport, IDraggable {

    public static final int GRID_SIZE = 20;
    private static final int GRID_MAJOR = 5;

    private static final int ARROW_COLOR_ITEM = PlannhColors.ARROW_ITEM.getColor();
    private static final int ARROW_COLOR_FLUID = PlannhColors.ARROW_FLUID.getColor();
    private static final int PREVIEW_COLOR = PlannhColors.PREVIEW_HIGHLIGHT.getColor();
    private static final int CLAMP_MARGIN = 4;
    private static final int NODE_W_ESTIMATE = 120;
    private static final int NODE_H_ESTIMATE = 80;
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
    private static final int EDGE_MARGIN_BASE = 4;
    private static final int PORT_LABEL_MAX = 20;
    private static final int PORT_LABEL_TRUNC = 19;
    private static final int PORT_FONT_SIZE = 9;
    private static final float PORT_FONT_SCALE = 0.9f;
    private static final int PORT_LABEL_PAD = 2;

    private static final int GROUP_FIT_PAD = 12;
    private static final float ZOOM_STEP = 0.15f;
    private static final float ZOOM_MIN = 0.1f;
    private static final float ZOOM_MAX = 5.0f;

    // Orthogonal arrow routing (world-space units).
    private static final int ROUTE_CELL = 6;
    private static final int ROUTE_MARGIN = 12;
    private static final long ROUTE_HASH_SEED = 1125899906842597L;
    private static final ArrowRouter ARROW_ROUTER = new ArrowRouter(ROUTE_CELL, ROUTE_MARGIN);

    @NotNull
    @Getter
    private Graph graph;
    private final Map<UUID, RecipeNodeWidget> nodeWidgets = new HashMap<>();
    private final Map<UUID, StepWidget> sinkWidgets = new HashMap<>();
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
    private final Menu<?> contextMenu2;

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

        contextMenu2 = menu;
        rebuildGroupWidgets();
        rebuildNodeWidgets();
        rebuildSinkWidgets();

        background(new DynamicDrawable(() -> new Rectangle().color(getBackgroundColor())));
    }

    public void removeNode(final UUID nodeId) {
        graph.removeNode(nodeId);
        for (final Group group : graph.getGroups()
            .values()) {
            group.getNodeIds()
                .remove(nodeId);
        }
        rebuildNodeWidgets();
    }

    public void setGraph(final Graph newGraph) {
        this.graph = newGraph;
        removeAll();
        flowchartWidgets.clear();
        rebuildGroupWidgets();
        rebuildNodeWidgets();
        rebuildSinkWidgets();
    }

    public void moveGroupNodes(final UUID groupId, final int deltaX, final int deltaY) {
        final Group group = graph.getGroups()
            .get(groupId);
        if (group == null) return;
        for (final UUID nodeId : group.getNodeIds()) {
            final Node node = graph.getNodes()
                .get(nodeId);
            if (node == null) continue;
            node.x += deltaX;
            node.y += deltaY;
            final RecipeNodeWidget w = nodeWidgets.get(nodeId);
            if (w != null) {
                w.pos(node.x, node.y);
            }
        }
    }

    public void setGroupNodesVisible(final UUID groupId, final boolean visible) {
        final Group group = graph.getGroups()
            .get(groupId);
        if (group == null) return;
        for (final UUID nodeId : group.getNodeIds()) {
            if (visible) {
                if (nodeWidgets.containsKey(nodeId)) continue;
                final Node node = graph.getNodes()
                    .get(nodeId);
                if (node == null) continue;
                addNodeWidget(node);
            } else {
                final RecipeNodeWidget w = nodeWidgets.remove(nodeId);
                if (w != null) remove(w);
            }
        }
    }

    public void recheckMembershipAndFit() {
        for (final Node node : graph.getNodes()
            .values()) {
            updateNodeGroupMembership(node);
        }
        autoFitGroups();
    }

    @Nullable
    public Group getGroupForNode(final UUID nodeId) {
        for (final Group g : graph.getGroups()
            .values()) {
            if (g.getNodeIds()
                .contains(nodeId)) return g;
        }
        return null;
    }

    public void clampNodeToGroup(final Node node) {
        final Group group = getGroupForNode(node.id);
        if (group == null || !group.isClampNodes()) return;
        if (node.x < group.getX() + CLAMP_MARGIN) node.x = group.getX() + CLAMP_MARGIN;
        if (node.y < group.getY() + CLAMP_MARGIN + HEADER_OFFSET) node.y = group.getY() + CLAMP_MARGIN + HEADER_OFFSET;
        if (node.x + NODE_W_ESTIMATE > group.getX() + group.getWidth() - CLAMP_MARGIN)
            node.x = group.getX() + group.getWidth() - CLAMP_MARGIN - NODE_W_ESTIMATE;
        if (node.y + NODE_H_ESTIMATE > group.getY() + group.getHeight() - CLAMP_MARGIN)
            node.y = group.getY() + group.getHeight() - CLAMP_MARGIN - NODE_H_ESTIMATE;
    }

    private void autoFitGroups() {
        for (final Group group : graph.getGroups()
            .values()) {
            if (!group.isCoverChildren() || group.getNodeIds()
                .isEmpty()) continue;
            fitGroupToChildren(group);
        }
    }

    public void fitGroupToChildren(final Group group) {
        if (group.getNodeIds()
            .isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (final UUID nid : group.getNodeIds()) {
            final Node n = graph.getNodes()
                .get(nid);
            if (n == null) continue;
            if (n.x < minX) minX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.x + NODE_W_ESTIMATE > maxX) maxX = n.x + NODE_W_ESTIMATE;
            if (n.y + NODE_H_ESTIMATE > maxY) maxY = n.y + NODE_H_ESTIMATE;
        }
        // Also consider GraphData children (notes, nested groups)
        for (final GraphData child : group.getChildren()
            .values()) {
            if (child.getX() < minX) minX = child.getX();
            if (child.getY() < minY) minY = child.getY();
        }
        if (minX == Integer.MAX_VALUE) return;
        group.setX(minX - GROUP_FIT_PAD);
        group.setY(minY - GROUP_FIT_PAD);
        group.setWidth(maxX - minX + GROUP_FIT_PAD * 2);
        group.setHeight(maxY - minY + GROUP_FIT_PAD * 2);
        // Clamp all member nodes
        for (final UUID nid : group.getNodeIds()) {
            final Node n = graph.getNodes()
                .get(nid);
            if (n == null) continue;
            clampNodeToGroup(n);
        }
    }

    private void updateNodeGroupMembership(final Node node) {
        for (final Group group : graph.getGroups()
            .values()) {
            if (group.isCollapsed()) continue;
            final boolean inside = node.x >= group.getX() && node.x < group.getX() + group.getWidth()
                && node.y >= group.getY()
                && node.y < group.getY() + group.getHeight();
            final boolean contained = group.getNodeIds()
                .contains(node.id);
            if (inside && !contained) {
                group.getNodeIds()
                    .add(node.id);
            } else if (!inside && contained) {
                group.getNodeIds()
                    .remove(node.id);
            }
        }
    }

    public void rebuildNodeWidgets() {
        for (final RecipeNodeWidget w : nodeWidgets.values()) {
            remove(w);
        }
        nodeWidgets.clear();
        for (final Node node : graph.getNodes()
            .values()) {
            if (isNodeInCollapsedGroup(node.id)) continue;
            addNodeWidget(node);
            updateNodeGroupMembership(node);
        }
        rebuildNoteWidgets();
    }

    private boolean isNodeInCollapsedGroup(final UUID nodeId) {
        for (final Group group : graph.getGroups()
            .values()) {
            if (group.isCollapsed() && group.getNodeIds()
                .contains(nodeId)) return true;
        }
        return false;
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
            final int localMx = worldMx - Math.round(w.getNode().x);
            final int localMy = worldMy - Math.round(w.getNode().y);
            if (w.getOutputPortAt(localMx, localMy) >= 0) return true;
        }
        return false;
    }

    private void addNodeWidget(final Node node) {
        final RecipeNodeWidget widget = new RecipeNodeWidget(node, this);
        widget.pos(node.x, node.y);
        nodeWidgets.put(node.id, widget);
        child(widget);
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
        return Math.round(w.getNode().x * graph.getZoom() + graph.getPanX());
    }

    private int widgetY(final RecipeNodeWidget w) {
        return Math.round(w.getNode().y * graph.getZoom() + graph.getPanY());
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
        return w.getWorldWidth();
    }

    private int worldHeight(final RecipeNodeWidget w) {
        return w.getWorldHeight();
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
        return Math.round((getArea().width / 2 - getArea().x - graph.getPanX()) / graph.getZoom());
    }

    public int getCanvasScreenCenterY() {
        return Math.round((getArea().height / 2 - getArea().y - graph.getPanY()) / graph.getZoom());
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
            obstacles.add(new ArrowRouter.Rect(w.getNode().x, w.getNode().y, worldWidth(w), worldHeight(w)));
        }
        for (final StepWidget w : sinkWidgets.values()) {
            obstacles.add(
                new ArrowRouter.Rect(
                    w.getData()
                        .getX(),
                    w.getData()
                        .getY(),
                    w.getWorldWidth(),
                    w.getWorldHeight()));
        }
        final List<ArrowRouter.Request> requests = new ArrayList<>();
        for (final Edge edge : graph.getEdges()
            .values()) {
            final RecipeNodeWidget srcNode = nodeWidgets.get(edge.sourceId);
            final StepWidget srcSink = srcNode != null ? null : sinkWidgets.get(edge.sourceId);
            final RecipeNodeWidget dstNode = nodeWidgets.get(edge.targetId);
            final StepWidget dstSink = dstNode != null ? null : sinkWidgets.get(edge.targetId);
            if (srcNode == null && srcSink == null) continue;
            if (dstNode == null && dstSink == null) continue;

            final int sx = srcNode != null ? srcNode.getNode().x + worldWidth(srcNode)
                : srcSink.getData()
                    .getX() + srcSink.getWorldWidth();
            final int sy = srcNode != null ? srcNode.getNode().y + portWorldY(edge.sourceOutputIndex)
                : srcSink.getData()
                    .getY() + srcSink.getWorldHeight() / 2;

            final int dx = dstNode != null ? dstNode.getNode().x
                : dstSink.getData()
                    .getX();
            final int dy = dstNode != null ? dstNode.getNode().y + portWorldY(edge.targetInputIndex)
                : dstSink.getData()
                    .getY() + dstSink.getWorldHeight() / 2;

            requests.add(new ArrowRouter.Request(edge.id, sx, sy, dx, dy));
        }

        edgeRoutes.putAll(ARROW_ROUTER.route(obstacles, requests));
    }

    private long computeRouteSignature() {
        long sig = ROUTE_HASH_SEED;
        for (final Edge edge : graph.getEdges()
            .values()) {
            final RecipeNodeWidget srcNode = nodeWidgets.get(edge.sourceId);
            final StepWidget srcSink = srcNode != null ? null : sinkWidgets.get(edge.sourceId);
            final RecipeNodeWidget dstNode = nodeWidgets.get(edge.targetId);
            final StepWidget dstSink = dstNode != null ? null : sinkWidgets.get(edge.targetId);
            if (srcNode == null && srcSink == null) continue;
            if (dstNode == null && dstSink == null) continue;

            sig = mixRouteHash(sig, edge.id.getMostSignificantBits());
            sig = mixRouteHash(sig, edge.id.getLeastSignificantBits());
            sig = mixRouteHash(sig, edge.sourceOutputIndex);
            sig = mixRouteHash(sig, edge.targetInputIndex);

            if (srcNode != null) {
                sig = mixRouteHash(sig, srcNode.getNode().x);
                sig = mixRouteHash(sig, srcNode.getNode().y);
                sig = mixRouteHash(sig, worldWidth(srcNode));
                sig = mixRouteHash(sig, worldHeight(srcNode));
            } else {
                sig = mixRouteHash(
                    sig,
                    srcSink.getData()
                        .getX());
                sig = mixRouteHash(
                    sig,
                    srcSink.getData()
                        .getY());
                sig = mixRouteHash(sig, srcSink.getWorldWidth());
                sig = mixRouteHash(sig, srcSink.getWorldHeight());
            }

            if (dstNode != null) {
                sig = mixRouteHash(sig, dstNode.getNode().x);
                sig = mixRouteHash(sig, dstNode.getNode().y);
                sig = mixRouteHash(sig, worldWidth(dstNode));
                sig = mixRouteHash(sig, worldHeight(dstNode));
            } else {
                sig = mixRouteHash(
                    sig,
                    dstSink.getData()
                        .getX());
                sig = mixRouteHash(
                    sig,
                    dstSink.getData()
                        .getY());
                sig = mixRouteHash(sig, dstSink.getWorldWidth());
                sig = mixRouteHash(sig, dstSink.getWorldHeight());
            }
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
            final RecipeNodeWidget srcNodeW = nodeWidgets.get(edge.sourceId);
            final StepWidget srcSinkW = srcNodeW != null ? null : sinkWidgets.get(edge.sourceId);
            final RecipeNodeWidget dstNodeW = nodeWidgets.get(edge.targetId);
            final StepWidget dstSinkW = dstNodeW != null ? null : sinkWidgets.get(edge.targetId);
            if (srcNodeW == null && srcSinkW == null) continue;
            if (dstNodeW == null && dstSinkW == null) continue;

            final boolean isFluid;
            if (srcNodeW != null) {
                final Node srcNode = srcNodeW.getNode();
                isFluid = edge.sourceOutputIndex >= 0 && edge.sourceOutputIndex < srcNode.outputs.size()
                    && srcNode.outputs.get(edge.sourceOutputIndex)
                        .getType() == RecipePropertyAPI.FLUID;
            } else {
                final List<Port<?>> outs = srcSinkW.getData()
                    .getOutputs();
                isFluid = !outs.isEmpty() && outs.getFirst()
                    .getType() == RecipePropertyAPI.FLUID;
            }

            final List<int[]> route = edgeRoutes.get(edge.id);
            if (route != null && route.size() >= 2) {
                drawRoutedArrow(route, isFluid);
                continue;
            }

            final float z2 = graph.getZoom();
            final int srcX = srcNodeW != null ? widgetX(srcNodeW) + Math.round(worldWidth(srcNodeW) * z2)
                : sinkX(srcSinkW) + Math.round(srcSinkW.getWorldWidth() * z2);
            final int srcY = srcNodeW != null ? widgetY(srcNodeW) + portY(edge.sourceOutputIndex)
                : sinkY(srcSinkW) + Math.round((srcSinkW.getWorldHeight() / 2) * z2);

            final int dstX = dstNodeW != null ? widgetX(dstNodeW) : sinkX(dstSinkW);
            final int dstY = dstNodeW != null ? widgetY(dstNodeW) + portY(edge.targetInputIndex)
                : sinkY(dstSinkW) + Math.round((dstSinkW.getWorldHeight() / 2) * z2);

            drawArrow(srcX, srcY, dstX, dstY, isFluid);
        }
    }

    private int sinkX(final StepWidget w) {
        return Math.round(
            w.getData()
                .getX() * graph.getZoom() + graph.getPanX());
    }

    private int sinkY(final StepWidget w) {
        return Math.round(
            w.getData()
                .getY() * graph.getZoom() + graph.getPanY());
    }

    /**
     * Draws a multi-segment orthogonal arrow from cached world-space waypoints.
     */
    private void drawRoutedArrow(final List<int[]> route, final boolean fluid) {
        final int n = route.size();
        final int[] sx = new int[n];
        final int[] sy = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = Math.round(route.get(i)[0] * graph.getZoom() + graph.getPanX());
            sy[i] = Math.round(route.get(i)[1] * graph.getZoom() + graph.getPanY());
        }

        final float as = Math.max(ARROW_MIN_SIZE, ARROW_SIZE * graph.getZoom());
        final int color = fluid ? ARROW_COLOR_FLUID : ARROW_COLOR_ITEM;
        final int x2 = sx[n - 1];
        final int y2 = sy[n - 1];
        // Stop the line at the arrow base so it does not poke through the head (last segment is horizontal).
        sx[n - 1] = Math.round(x2 - as);

        drawLineStrip(sx, sy, color, Math.max(LINE_THICK_MIN, LINE_THICK_BASE * graph.getZoom()));
        drawArrowHead(x2, y2, sx[n - 1], as * ARROW_HB_RATIO, color);
    }

    private void drawArrow(final int x1, final int y1, final int x2, final int y2, final boolean fluid) {
        final float as = Math.max(ARROW_MIN_SIZE, ARROW_SIZE * graph.getZoom());
        final int ex = Math.round(x2 - as);
        final int color = fluid ? ARROW_COLOR_FLUID : ARROW_COLOR_ITEM;
        drawOrthogonalLine(x1, y1, x2, y2, ex, color, Math.max(LINE_THICK_MIN, LINE_THICK_BASE * graph.getZoom()));

        drawArrowHead(x2, y2, ex, as * ARROW_HB_RATIO, color);
    }

    private void drawPreviewLine() {
        final float z2 = graph.getZoom();
        final RecipeNodeWidget srcNodeW = nodeWidgets.get(edgeSourceNodeId);
        final StepWidget srcSinkW = srcNodeW != null ? null : sinkWidgets.get(edgeSourceNodeId);
        if (srcNodeW == null && srcSinkW == null) return;

        final int x1 = srcNodeW != null ? widgetX(srcNodeW) + Math.round(worldWidth(srcNodeW) * z2)
            : sinkX(srcSinkW) + Math.round(srcSinkW.getWorldWidth() * z2);
        final int y1 = srcNodeW != null ? widgetY(srcNodeW) + portY(edgeSourcePortIndex)
            : sinkY(srcSinkW) + Math.round((srcSinkW.getWorldHeight() / 2) * z2);

        int x2 = edgeEndX;
        int y2 = edgeEndY;

        if (edgeHoverNodeId != null) {
            final RecipeNodeWidget dstNodeW = nodeWidgets.get(edgeHoverNodeId);
            final StepWidget dstSinkW = dstNodeW != null ? null : sinkWidgets.get(edgeHoverNodeId);
            if (dstNodeW != null) {
                x2 = widgetX(dstNodeW);
                y2 = widgetY(dstNodeW) + portY(edgeHoverPortIndex);
            } else if (dstSinkW != null) {
                x2 = sinkX(dstSinkW);
                y2 = sinkY(dstSinkW) + Math.round((dstSinkW.getWorldHeight() / 2) * z2);
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
                final int localMx = worldMx - Math.round(widget.getNode().x);
                final int localMy = worldMy - Math.round(widget.getNode().y);
                final int port = widget.getOutputPortAt(localMx, localMy);
                if (port >= 0) {
                    creatingEdge = true;
                    edgeSourceNodeId = widget.getNode().id;
                    edgeSourcePortIndex = port;
                    edgeEndX = cmx;
                    edgeEndY = cmy;
                    edgeHoverNodeId = null;
                    edgeHoverPortIndex = -1;
                    return Result.SUCCESS;
                }
            }

            // Check sink output ports
            for (final StepWidget stepWidget : sinkWidgets.values()) {
                if (stepWidget.getData()
                    .getOutputs()
                    .isEmpty()) continue;
                final int localMx = worldMx - Math.round(
                    stepWidget.getData()
                        .getX());
                final int localMy = worldMy - Math.round(
                    stepWidget.getData()
                        .getY());
                if (stepWidget.getOutputPortAt(localMx, localMy) >= 0) {
                    creatingEdge = true;
                    edgeSourceNodeId = stepWidget.getData()
                        .getId();
                    edgeSourcePortIndex = 0;
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
                    graph.getEdges()
                        .remove(clicked.id);
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
        if (mouseButton == 2) {
            // allow middle mouse drag
            return Result.ACCEPT;
        }
        return Result.IGNORE;
    }

    private void openContextMenu() {
        contextMenu2.pos(getContext().getAbsMouseX(), getContext().getAbsMouseY());
        menuOpen = true;
    }

    @Override
    public boolean onMouseRelease(final int mouseButton) {
        if (creatingEdge) {
            if (edgeHoverNodeId != null) {
                final boolean validSrc = nodeWidgets.containsKey(edgeSourceNodeId)
                    || sinkWidgets.containsKey(edgeSourceNodeId);
                final boolean validDst = nodeWidgets.containsKey(edgeHoverNodeId)
                    || sinkWidgets.containsKey(edgeHoverNodeId);
                if (validSrc && validDst) {
                    final UUID id = UUID.randomUUID();
                    graph.getEdges()
                        .put(
                            id,
                            new Edge(id, edgeSourceNodeId, edgeHoverNodeId, edgeSourcePortIndex, edgeHoverPortIndex));
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

            final float z = graph.getZoom();
            final int worldDragMx = Math.round((cmx - graph.getPanX()) / z);
            final int worldDragMy = Math.round((cmy - graph.getPanY()) / z);

            // Determine source port
            final Port<?> srcPort;
            final boolean srcIsNode = nodeWidgets.containsKey(edgeSourceNodeId);
            if (srcIsNode) {
                final RecipeNodeWidget srcWidget = nodeWidgets.get(edgeSourceNodeId);
                final Node srcNode = srcWidget.getNode();
                if (edgeSourcePortIndex < 0 || edgeSourcePortIndex >= srcNode.outputs.size()) return;
                srcPort = srcNode.outputs.get(edgeSourcePortIndex);
            } else {
                final StepWidget srcSink = sinkWidgets.get(edgeSourceNodeId);
                if (srcSink == null) return;
                final List<Port<?>> outs = srcSink.getData()
                    .getOutputs();
                if (outs.isEmpty()) return;
                srcPort = (Port<?>) outs.getFirst();
            }

            // Check node input ports
            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                if (srcIsNode && widget.getNode().id.equals(edgeSourceNodeId)) continue;
                final int localMx = worldDragMx - Math.round(widget.getNode().x);
                final int localMy = worldDragMy - Math.round(widget.getNode().y);
                final int portIdx = widget.getInputPortAt(localMx, localMy);
                if (portIdx >= 0 && portIdx < widget.getNode().inputs.size()
                    && srcPort.canConnect(widget.getNode().inputs.get(portIdx))) {
                    edgeHoverNodeId = widget.getNode().id;
                    edgeHoverPortIndex = portIdx;
                    return;
                }
            }

            // Check sink input ports
            for (final StepWidget stepWidget : sinkWidgets.values()) {
                if (stepWidget.getData()
                    .getId()
                    .equals(edgeSourceNodeId)) continue;
                final List<Port<?>> ins = stepWidget.getData()
                    .getInputs();
                if (ins.isEmpty()) continue;
                final int localMx = worldDragMx - Math.round(
                    stepWidget.getData()
                        .getX());
                final int localMy = worldDragMy - Math.round(
                    stepWidget.getData()
                        .getY());
                if (stepWidget.getInputPortAt(localMx, localMy) >= 0 && srcPort.canConnect(ins.getFirst())) {
                    edgeHoverNodeId = stepWidget.getData()
                        .getId();
                    edgeHoverPortIndex = 0;
                    return;
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
        for (final FlowchartWidget<?, ?> gw : flowchartWidgets.values()) {
            if (!(gw instanceof GroupWidget)) continue;
            final Area a = gw.getArea();
            if (containsPointInclusive(a, mx, my)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOverAnyNode(final int mx, final int my) {
        final int cmx = mx - getArea().x;
        final int cmy = my - getArea().y;
        final float z = graph.getZoom();
        final int worldMx = Math.round((cmx - graph.getPanX()) / z);
        final int worldMy = Math.round((cmy - graph.getPanY()) / z);
        for (final RecipeNodeWidget widget : nodeWidgets.values()) {
            final Area a = widget.getArea();
            if (worldMx >= a.x && worldMx < a.x + a.width && worldMy >= a.y && worldMy < a.y + a.height) return true;
        }
        for (final StepWidget widget : sinkWidgets.values()) {
            final Area a = widget.getArea();
            if (worldMx >= a.x && worldMx < a.x + a.width && worldMy >= a.y && worldMy < a.y + a.height) return true;
        }
        return false;
    }

    public void addNote(int x, int y) {
        final Note note = new Note();
        note.setX(x);
        note.setY(y);

        graph.getNotes()
            .put(note.getId(), note);
        child(new NoteWidget(this, note));

        menuOpen = false;
    }

    public void addGroup(int x, int y) {
        final Group group = new Group();
        group.setX(x);
        group.setY(y);

        graph.getGroups()
            .put(group.getId(), group);
        child(new GroupWidget(this, group));

        menuOpen = false;
    }

    public void addSink(int x, int y) {
        final Step step = new Step();
        step.setX(x);
        step.setY(y);

        graph.getSteps()
            .put(step.getId(), step);
        addSinkWidget(step);

        menuOpen = false;
    }

    public void rebuildSinkWidgets() {
        for (final StepWidget w : sinkWidgets.values()) {
            remove(w);
        }
        sinkWidgets.clear();
        for (final Step step : graph.getSteps()
            .values()) {
            addSinkWidget(step);
        }
    }

    private void addSinkWidget(final Step step) {
        final StepWidget widget = new StepWidget(this, step);
        sinkWidgets.put(step.getId(), widget);
        child(widget);
    }

    @Nullable
    private ContextMenuWidget contextMenu = null;

    public void showContextMenu(final int x, final int y, final List<ContextMenuWidget.MenuItem> items) {
        closeContextMenu();
        contextMenu = new ContextMenuWidget(this, items, x, y);
        child(contextMenu);
    }

    public void closeContextMenu() {
        if (contextMenu != null) {
            remove(contextMenu);
            contextMenu = null;
        }
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
         * final Group currentGroup = getGroupForNode(node.id);
         * if (currentGroup != null) {
         * items.add(
         * new ContextMenuWidget.MenuItem(
         * "Remove from \"" + currentGroup.title + "\"",
         * () -> currentGroup.nodeIds.remove(node.id)));
         * }
         * final List<Group> otherGroups = new ArrayList<>();
         * for (final Group g : graph.getGroups()) {
         * if (g != currentGroup) otherGroups.add(g);
         * }
         * if (!otherGroups.isEmpty()) {
         * for (final Group g : otherGroups) {
         * items.add(new ContextMenuWidget.MenuItem("Add to \"" + g.title + "\"", () -> g.nodeIds.add(node.id)));
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
            final int worldW = w.getWorldWidth();

            // Output ports (right side)
            for (int i = 0; i < node.outputs.size(); i++) {
                final int sx = Math.round((node.x + worldW - PORT_S) * z + px);
                final int sy = Math.round((node.y + portWorldY(i) - PORT_HALF) * z + py);
                if (mouseX >= sx && mouseX < sx + ps && mouseY >= sy && mouseY < sy + ps) {
                    final String label = portLabel(node.outputs.get(i));
                    if (label != null) {
                        final int ax = Math.round((node.x + worldW) * z + px);
                        final int ay = Math.round((node.y + portWorldY(i)) * z + py);
                        drawPortLabel(label, ax, ay, true, z);
                    }
                    return;
                }
            }

            // Input ports (left side)
            for (int i = 0; i < node.inputs.size(); i++) {
                final int sx = Math.round(node.x * z + px);
                final int sy = Math.round((node.y + portWorldY(i) - PORT_HALF) * z + py);
                if (mouseX >= sx && mouseX < sx + ps && mouseY >= sy && mouseY < sy + ps) {
                    final String label = portLabel(node.inputs.get(i));
                    if (label != null) {
                        final int ax = Math.round(node.x * z + px);
                        final int ay = Math.round((node.y + portWorldY(i)) * z + py);
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
