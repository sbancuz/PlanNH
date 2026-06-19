package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.layout.IViewport;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.api.widget.IDraggable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.BufferBuilder;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.Stencil;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.sbancuz.plannh.api.RecipePropertyAPI;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.data.flowchart.Port;

import lombok.Getter;

public class CanvasWidget extends ParentWidget<CanvasWidget> implements Interactable, IViewport, IDraggable {

    private static final int GRID_SIZE = 20;
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
    // private final Map<UUID, NoteWidget> noteWidgets = new HashMap<>();
    private final Map<UUID, GroupWidget> groupWidgets = new HashMap<>();

    private boolean panning = false;
    private int panStartMouseX, panStartMouseY;
    private float panStartX, panStartY;

    private boolean creatingEdge = false;
    private UUID edgeSourceNodeId;
    private int edgeSourcePortIndex;
    private int edgeEndX, edgeEndY;
    private UUID edgeHoverNodeId;
    private int edgeHoverPortIndex;

    /**
     * Cached arrow routes (world-space waypoints) keyed by edge id, plus the layout they were built for.
     */
    private final Map<UUID, List<int[]>> edgeRoutes = new HashMap<>();
    private long routeSignature = Long.MIN_VALUE;

    public CanvasWidget(final Graph graph) {
        this.graph = graph;
        rebuildNodeWidgets();
        full();
        marginBottom(18);
    }

    public int getZoomPercent() {
        return Math.round(graph.getZoom() * 100);
    }

    public void removeNode(final UUID nodeId) {
        graph.removeNode(nodeId);
        for (final Group group : graph.getGroups()) {
            group.nodeIds.remove(nodeId);
        }
        rebuildNodeWidgets();
    }

    public void setGraph(final Graph newGraph) {
        this.graph = newGraph;
        rebuildNodeWidgets();
        rebuildNoteWidgets();
        rebuildGroupWidgets();
    }

    public void removeGroup(final UUID groupId) {
        graph.removeGroup(groupId);
        rebuildGroupWidgets();
    }

    public void addGroup(final int x, final int y) {
        int n = 1;
        for (final Group g : graph.getGroups()) {
            if (g.title.startsWith("Group")) {
                try {
                    final int num = Integer.parseInt(
                        g.title.substring(5)
                            .trim());
                    if (num >= n) n = num + 1;
                } catch (final NumberFormatException ignored) {}
            }
        }
        final Group group = new Group(x, y, "Group " + n);
        graph.addGroup(group);
        addGroupWidget(group);
    }

    public void moveGroupNodes(final UUID groupId, final int deltaX, final int deltaY) {
        final Group group = graph.groups.get(groupId);
        if (group == null) return;
        for (final UUID nodeId : group.nodeIds) {
            final Node node = graph.nodes.get(nodeId);
            if (node == null) continue;
            node.x += deltaX;
            node.y += deltaY;
            final RecipeNodeWidget w = nodeWidgets.get(nodeId);
            if (w != null) {
                // w.syncTransform(zoom, panX, graph.getPanY());
            }
        }
    }

    public void setGroupNodesVisible(final UUID groupId, final boolean visible) {
        final Group group = graph.groups.get(groupId);
        if (group == null) return;
        for (final UUID nodeId : group.nodeIds) {
            if (visible) {
                if (nodeWidgets.containsKey(nodeId)) continue;
                final Node node = graph.nodes.get(nodeId);
                if (node == null) continue;
                addNodeWidget(node);
            } else {
                final RecipeNodeWidget w = nodeWidgets.remove(nodeId);
                if (w != null) remove(w);
            }
        }
    }

    public void recheckMembershipAndFit() {
        for (final Node node : graph.getNodes()) {
            updateNodeGroupMembership(node);
        }
        autoFitGroups();
    }

    @Nullable
    public Group getGroupForNode(final UUID nodeId) {
        for (final Group g : graph.getGroups()) {
            if (g.nodeIds.contains(nodeId)) return g;
        }
        return null;
    }

    public void clampNodeToGroup(final Node node) {
        final Group group = getGroupForNode(node.id);
        if (group == null || !group.clampNodes) return;
        if (node.x < group.x + CLAMP_MARGIN) node.x = group.x + CLAMP_MARGIN;
        if (node.y < group.y + CLAMP_MARGIN + HEADER_OFFSET) node.y = group.y + CLAMP_MARGIN + HEADER_OFFSET;
        if (node.x + NODE_W_ESTIMATE > group.x + group.width - CLAMP_MARGIN)
            node.x = group.x + group.width - CLAMP_MARGIN - NODE_W_ESTIMATE;
        if (node.y + NODE_H_ESTIMATE > group.y + group.height - CLAMP_MARGIN)
            node.y = group.y + group.height - CLAMP_MARGIN - NODE_H_ESTIMATE;
    }

    private void autoFitGroups() {
        for (final Group group : graph.getGroups()) {
            if (!group.autoResize || group.nodeIds.isEmpty()) continue;
            fitGroupToChildren(group);
        }
    }

    public void fitGroupToChildren(final Group group) {
        if (group.nodeIds.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (final UUID nid : group.nodeIds) {
            final Node n = graph.nodes.get(nid);
            if (n == null) continue;
            if (n.x < minX) minX = n.x;
            if (n.y < minY) minY = n.y;
            if (n.x + NODE_W_ESTIMATE > maxX) maxX = n.x + NODE_W_ESTIMATE;
            if (n.y + NODE_H_ESTIMATE > maxY) maxY = n.y + NODE_H_ESTIMATE;
        }
        if (minX == Integer.MAX_VALUE) return;
        group.x = minX - GROUP_FIT_PAD;
        group.y = minY - GROUP_FIT_PAD;
        group.width = maxX - minX + GROUP_FIT_PAD * 2;
        group.height = maxY - minY + GROUP_FIT_PAD * 2;
        final GroupWidget gw = groupWidgets.get(group.id);
        if (gw != null) {
            // gw.syncTransform(zoom, panX, graph.getPanY());
        }
        // Move all nodes into the group if auto-sizing
        for (final UUID nid : group.nodeIds) {
            final Node n = graph.nodes.get(nid);
            if (n == null) continue;
            clampNodeToGroup(n);
            final RecipeNodeWidget w = nodeWidgets.get(nid);
            // if (w != null) w.syncTransform(zoom, panX, graph.getPanY());
        }
    }

    private void updateNodeGroupMembership(final Node node) {
        for (final Group group : graph.getGroups()) {
            if (group.collapsed) continue;
            final boolean inside = node.x >= group.x && node.x < group.x + group.width
                && node.y >= group.y
                && node.y < group.y + group.height;
            final boolean contained = group.nodeIds.contains(node.id);
            if (inside && !contained) {
                group.nodeIds.add(node.id);
            } else if (!inside && contained) {
                group.nodeIds.remove(node.id);
            }
        }
    }

    public void rebuildNodeWidgets() {
        removeAll();
        // editingNoteId = null;
        editingGroupId = null;
        nodeWidgets.clear();
        groupWidgets.clear();
        // noteWidgets.clear();
        for (final Group group : graph.getGroups()) {
            addGroupWidget(group);
        }
        for (final Node node : graph.getNodes()) {
            addNodeWidget(node);
            updateNodeGroupMembership(node);
        }
        rebuildNoteWidgets();
    }

    public void rebuildGroupWidgets() {
        clearWidgetMap(groupWidgets);
        for (final Group group : graph.getGroups()) {
            addGroupWidget(group);
        }
    }

    public void rebuildNoteWidgets() {
        for (final Note note : graph.notes.values()) addNoteWidget(note);
    }

    private void clearWidgetMap(final Map<UUID, ? extends IWidget> widgets) {
        for (final IWidget widget : widgets.values()) {
            remove(widget);
        }
        widgets.clear();
    }

    private void addGroupWidget(final Group group) {
        final GroupWidget gw = new GroupWidget(group, this);
        // gw.syncTransform(zoom, panX, graph.getPanY());
        groupWidgets.put(group.id, gw);
        child(gw);
    }

    private void addNodeWidget(final Node node) {
        final RecipeNodeWidget widget = new RecipeNodeWidget(node, this);
        // widget.syncTransform(zoom, panX, graph.getPanY());
        nodeWidgets.put(node.id, widget);
        child(widget);
    }

    private void addNoteWidget(final Note note) {
        final NoteWidget nw = new NoteWidget(this, note);
        // nw.syncTransform(zoom, panX, graph.getPanY());
        // noteWidgets.put(note.id, nw);
        child(nw);
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        final int aw = getArea().width;
        final int ah = getArea().height;
        if (aw <= 0 || ah <= 0) return;

        drawGrid(aw, ah);

        super.draw(context, widgetTheme);

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
     * Converts a canvas-space X coordinate to world space, clamped to >= 0.
     */
    private int toWorldX(final int cx) {
        return Math.max(0, Math.round((cx - graph.getPanX()) / graph.getZoom()));
    }

    /**
     * Converts a canvas-space Y coordinate to world space, clamped to >= 0.
     */
    private int toWorldY(final int cy) {
        return Math.max(0, Math.round((cy - graph.getPanY()) / graph.getZoom()));
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
        final List<ArrowRouter.Request> requests = new ArrayList<>();
        for (final Edge edge : graph.getEdges()) {
            final RecipeNodeWidget src = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dst = nodeWidgets.get(edge.targetNodeId);
            if (src == null || dst == null) continue;
            final int sx = src.getNode().x + worldWidth(src);
            final int sy = src.getNode().y + portWorldY(edge.sourceOutputIndex);
            final int dx = dst.getNode().x;
            final int dy = dst.getNode().y + portWorldY(edge.targetInputIndex);
            requests.add(new ArrowRouter.Request(edge.id, sx, sy, dx, dy));
        }

        edgeRoutes.putAll(ARROW_ROUTER.route(obstacles, requests));
    }

    private long computeRouteSignature() {
        long sig = ROUTE_HASH_SEED;
        for (final Edge edge : graph.getEdges()) {
            final RecipeNodeWidget src = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dst = nodeWidgets.get(edge.targetNodeId);
            if (src == null || dst == null) continue;
            sig = mixRouteHash(sig, edge.id.getMostSignificantBits());
            sig = mixRouteHash(sig, edge.id.getLeastSignificantBits());
            sig = mixRouteHash(sig, edge.sourceOutputIndex);
            sig = mixRouteHash(sig, edge.targetInputIndex);
            sig = mixRouteHash(sig, src.getNode().x);
            sig = mixRouteHash(sig, src.getNode().y);
            sig = mixRouteHash(sig, worldWidth(src));
            sig = mixRouteHash(sig, worldHeight(src));
            sig = mixRouteHash(sig, dst.getNode().x);
            sig = mixRouteHash(sig, dst.getNode().y);
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
        for (final Edge edge : graph.getEdges()) {
            final RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            final Node srcNode = graph.nodes.get(edge.sourceNodeId);
            final boolean isFluid = srcNode != null && edge.sourceOutputIndex >= 0
                && edge.sourceOutputIndex < srcNode.outputs.size()
                && srcNode.outputs.get(edge.sourceOutputIndex)
                    .getType() == RecipePropertyAPI.FLUID;

            final List<int[]> route = edgeRoutes.get(edge.id);
            if (route != null && route.size() >= 2) {
                drawRoutedArrow(route, isFluid);
                continue;
            }

            final int srcX = widgetX(srcWidget) + srcWidget.getArea().width;
            final int srcY = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            final int dstX = widgetX(dstWidget);
            final int dstY = widgetY(dstWidget) + portY(edge.targetInputIndex);

            drawArrow(srcX, srcY, dstX, dstY, isFluid);
        }
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
        final RecipeNodeWidget srcWidget = nodeWidgets.get(edgeSourceNodeId);
        if (srcWidget == null) return;

        final int x1 = widgetX(srcWidget) + srcWidget.getArea().width;
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
        for (final Edge edge : graph.getEdges()) {
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
        if (srcOutIdx >= srcNode.outputs.size() || dstInIdx >= dstNode.inputs.size()) return false;
        return srcNode.outputs.get(srcOutIdx)
            .canConnect(dstNode.inputs.get(dstInIdx));
    }

    @Override
    public @NotNull Result onMousePressed(final int mouseButton) {
        final int absMx = getContext().getAbsMouseX();
        final int absMy = getContext().getAbsMouseY();

        // Close context menu on any click, unless it's on the menu itself
        if (isMouseOverContextMenu(absMx, absMy)) {
            return Result.IGNORE;
        }
        closeContextMenu();

        if (mouseButton == 0) {
            final int cmx = absMx - getArea().x;
            final int cmy = absMy - getArea().y;
            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                final int localMx = cmx - widgetX(widget);
                final int localMy = cmy - widgetY(widget);
                final int port = widget.getOutputPortAt(localMx, localMy);
                if (port >= 0) {
                    creatingEdge = true;
                    edgeSourceNodeId = widget.getNode().id;
                    edgeSourcePortIndex = port;
                    edgeEndX = cmx;
                    edgeEndY = cmy;
                    edgeHoverNodeId = null;
                    edgeHoverPortIndex = -1;
                    return Interactable.Result.SUCCESS;
                }
            }

            if (!isMouseOverAnyNode(absMx, absMy) && !isMouseOverAnyGroup(absMx, absMy)) {
                final Edge clicked = getEdgeAt(absMx, absMy);
                if (clicked != null) {
                    graph.removeEdge(clicked.id);
                    return Interactable.Result.SUCCESS;
                }
                return Result.ACCEPT;
            }
            return Interactable.Result.IGNORE;
        }
        if (mouseButton == 1) {
            final int cmx = absMx - getArea().x;
            final int cmy = absMy - getArea().y;

            // Check if over a group header (pass click through for its own right-click menu)
            for (final GroupWidget gw : groupWidgets.values()) {
                final Area a = gw.getArea();
                if (containsPoint(a, absMx, absMy)) {
                    showGroupContextMenu(gw, absMx, absMy);
                    return Result.SUCCESS;
                }
            }

            // Check if over a node
            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                final Area a = widget.getArea();
                if (containsPoint(a, absMx, absMy)) {
                    showNodeContextMenu(widget, absMx, absMy);
                    return Result.SUCCESS;
                }
            }

            // Empty canvas
            showCanvasContextMenu(cmx, cmy);
            return Result.SUCCESS;
        }
        if (mouseButton == 2) {
            return Result.ACCEPT;
        }
        return Interactable.Result.IGNORE;
    }

    @Override
    public boolean onMouseRelease(final int mouseButton) {
        if (creatingEdge) {
            if (edgeHoverNodeId != null) {
                final Node srcNode = graph.nodes.get(edgeSourceNodeId);
                final Node dstNode = graph.nodes.get(edgeHoverNodeId);
                if (srcNode != null && dstNode != null) {
                    graph.addEdge(
                        new Edge(
                            UUID.randomUUID(),
                            edgeSourceNodeId,
                            edgeHoverNodeId,
                            edgeSourcePortIndex,
                            edgeHoverPortIndex));
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

            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                if (widget == srcWidget) continue;
                final int localMx = cmx - widgetX(widget);
                final int localMy = cmy - widgetY(widget);
                final int port = widget.getInputPortAt(localMx, localMy);
                if (port >= 0 && canConnect(srcWidget.getNode(), edgeSourcePortIndex, widget.getNode(), port)) {
                    edgeHoverNodeId = widget.getNode().id;
                    edgeHoverPortIndex = port;
                    break;
                }
            }
        }
    }

    @Override
    public boolean onMouseScroll(final UpOrDown direction, final int amount) {
        float delta = direction == UpOrDown.UP ? ZOOM_STEP : -ZOOM_STEP;
        delta *= Math.max(1, Math.abs(amount));
        final float oldZoom = graph.getZoom();
        graph.setZoom(Math.clamp(graph.getZoom() + delta, ZOOM_MIN, ZOOM_MAX));
        final float ratio = graph.getZoom() / oldZoom;

        final int mx = getContext().getAbsMouseX();
        final int my = getContext().getAbsMouseY();

        final float mxRel = mx - getArea().x;
        final float myRel = my - getArea().y;

        graph.setPanX(mxRel - (mxRel - graph.getPanX()) * ratio);
        graph.setPanY(myRel - (myRel - graph.getPanY()) * ratio);

        // updatePositions();
        return true;
    }

    private boolean isMouseOverAnyGroup(final int mx, final int my) {
        for (final GroupWidget gw : groupWidgets.values()) {
            final Area a = gw.getArea();
            if (containsPointInclusive(a, mx, my)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOverAnyNode(final int mx, final int my) {
        for (final RecipeNodeWidget widget : nodeWidgets.values()) {
            final Area a = widget.getArea();
            if (containsPointInclusive(a, mx, my)) {
                return true;
            }
        }
        return false;
    }

    // ── Notes ──

    public void addNote(final int x, final int y) {
        final Note note = new Note(UUID.randomUUID());
        note.setX(x);
        note.setY(y);
        graph.notes.put(note.getId(), note);
        addNoteWidget(note);
    }

    public void removeNote(final UUID noteId) {
        graph.notes.remove(noteId);
    }

    // @Nullable
    // private UUID editingNoteId = null;
    @Nullable
    private UUID editingGroupId = null;

    /*
     * public void startEditingNote(final UUID noteId) {
     * editingNoteId = noteId;
     * for (final NoteWidget nw : noteWidgets.values()) {
     * nw.setEditing(nw.getNote().id.equals(noteId));
     * }
     * }
     */

    /*
     * public void onNoteEditingDone() {
     * editingNoteId = null;
     * }
     */

    public void startEditingGroup(final UUID groupId) {
        editingGroupId = groupId;
        for (final GroupWidget gw : groupWidgets.values()) {
            gw.setEditing(gw.getGroup().id.equals(groupId));
        }
    }

    public void onGroupEditingDone() {
        editingGroupId = null;
    }

    @Override
    public @NotNull Result onKeyPressed(final char typedChar, final int keyCode) {
        /*
         * if (editingNoteId != null) {
         * final NoteWidget nw = noteWidgets.get(editingNoteId);
         * if (nw == null) {
         * editingNoteId = null;
         * return Result.IGNORE;
         * }
         * final Note note = nw.getNote();
         * if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
         * nw.setEditing(false);
         * return Result.SUCCESS;
         * }
         * if (keyCode == org.lwjgl.input.Keyboard.KEY_BACK) {
         * if (!note.text.isEmpty()) {
         * note.text = note.text.substring(0, note.text.length() - 1);
         * }
         * return Result.SUCCESS;
         * }
         * if (typedChar >= 32 && typedChar < 127) {
         * note.text += typedChar;
         * return Result.SUCCESS;
         * }
         * return Result.SUCCESS;
         * }
         */
        if (editingGroupId != null) {
            final GroupWidget gw = groupWidgets.get(editingGroupId);
            if (gw == null) {
                editingGroupId = null;
                return Result.IGNORE;
            }
            final Group group = gw.getGroup();
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                gw.setEditing(false);
                return Result.SUCCESS;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (!group.title.isEmpty()) {
                    if (gw.getCursorPos() > 0) {
                        final int cp = gw.getCursorPos();
                        group.title = group.title.substring(0, cp - 1) + group.title.substring(cp);
                        gw.decrementCursor();
                    }
                }
                return Result.SUCCESS;
            }
            if (typedChar >= 32 && typedChar < 127) {
                final int cp = gw.getCursorPos();
                group.title = group.title.substring(0, cp) + typedChar + group.title.substring(cp);
                gw.incrementCursor();
                return Result.SUCCESS;
            }
            return Result.SUCCESS;
        }
        return Result.IGNORE;
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

    public boolean isMouseOverContextMenu(final int mx, final int my) {
        return contextMenu != null && containsPoint(contextMenu.getArea(), mx, my);
    }

    private void openColorPickerFor(final Group group) {
        final int currentColor = group.colorOverride != 0 ? group.colorOverride : PlannhColors.titleColor(group.title);
        final ColorPickerDialog picker = new ColorPickerDialog(chosenColor -> {
            group.colorOverride = chosenColor;
            rebuildGroupWidgets();
        }, currentColor, false);
        IPanelHandler
            .simple(
                getPanel(),
                (@SuppressWarnings("unused") final ModularPanel parentPanel,
                    @SuppressWarnings("unused") final EntityPlayer player) -> picker,
                true)
            .openPanel();
    }

    private void contextMenuAt(final int cmx, final int cmy, final List<ContextMenuWidget.MenuItem> items) {
        final int ax = getArea().x + cmx;
        final int ay = getArea().y + cmy;
        showContextMenu(ax, ay, items);
    }

    private void showCanvasContextMenu(final int cmx, final int cmy) {
        final List<ContextMenuWidget.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenuWidget.MenuItem("Add Group", () -> addGroup(toWorldX(cmx), toWorldY(cmy))));
        items.add(new ContextMenuWidget.MenuItem("Add Note", () -> addNote(toWorldX(cmx), toWorldY(cmy))));
        contextMenuAt(cmx, cmy, items);
    }

    private void showGroupContextMenu(final GroupWidget gw, final int cmx, final int cmy) {
        final Group group = gw.getGroup();
        final List<ContextMenuWidget.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenuWidget.MenuItem("Rename Group", () -> startEditingGroup(group.id)));
        items.add(new ContextMenuWidget.MenuItem("Customize Color", () -> openColorPickerFor(group)));
        items.add(
            new ContextMenuWidget.MenuItem(
                group.clampNodes ? "Disable Clamp" : "Enable Clamp",
                () -> group.clampNodes = !group.clampNodes));
        items.add(
            new ContextMenuWidget.MenuItem(
                group.autoResize ? "Disable Auto-Resize" : "Enable Auto-Resize",
                () -> group.autoResize = !group.autoResize));
        items.add(new ContextMenuWidget.MenuItem("Delete Group", () -> removeGroup(group.id)));
        contextMenuAt(cmx, cmy, items);
    }

    private void showNodeContextMenu(final RecipeNodeWidget nw, final int cmx, final int cmy) {
        final Node node = nw.getNode();
        final List<ContextMenuWidget.MenuItem> items = new ArrayList<>();
        final Group currentGroup = getGroupForNode(node.id);
        if (currentGroup != null) {
            items.add(
                new ContextMenuWidget.MenuItem(
                    "Remove from \"" + currentGroup.title + "\"",
                    () -> currentGroup.nodeIds.remove(node.id)));
        }
        final List<Group> otherGroups = new ArrayList<>();
        for (final Group g : graph.getGroups()) {
            if (g != currentGroup) otherGroups.add(g);
        }
        if (!otherGroups.isEmpty()) {
            for (final Group g : otherGroups) {
                items.add(new ContextMenuWidget.MenuItem("Add to \"" + g.title + "\"", () -> g.nodeIds.add(node.id)));
            }
        }
        if (items.isEmpty()) return;
        contextMenuAt(cmx, cmy, items);
    }

    // ── Hovered port labels ──

    private void drawHoveredPortLabels() {
        final int mouseX = getContext().getMouseX();
        final int mouseY = getContext().getMouseY();
        final int ps = Math.max(1, Math.round(PORT_S * graph.getZoom()));
        final int half = Math.round(PORT_HALF * graph.getZoom());

        for (final RecipeNodeWidget w : nodeWidgets.values()) {
            final Node node = w.getNode();
            final int widgetX = widgetX(w);
            final int widgetY = widgetY(w);
            final int widgetWidth = w.getArea().width;

            if (tryPortLabel(
                mouseX,
                mouseY,
                graph.getZoom(),
                ps,
                half,
                widgetX,
                widgetY,
                widgetWidth,
                node.outputs,
                true)) return;
            if (tryPortLabel(
                mouseX,
                mouseY,
                graph.getZoom(),
                ps,
                half,
                widgetX,
                widgetY,
                widgetWidth,
                node.inputs,
                false)) return;
        }
    }

    private boolean tryPortLabel(final int mouseX, final int mouseY, final float zoom, final int ps, final int half,
        final int widgetX, final int widgetY, final int widgetWidth, final List<Port<?>> ports,
        final boolean rightSide) {
        for (int i = 0; i < ports.size(); i++) {
            final int px = rightSide ? widgetX + widgetWidth - ps : widgetX;
            final int pcY = widgetY + portY(i);
            final int py = pcY - half;
            if (mouseX >= px && mouseX < px + ps && mouseY >= py && mouseY < py + ps) {
                final String label = portLabel(ports.get(i));
                if (label != null) {
                    drawPortLabel(label, rightSide ? widgetX + widgetWidth : widgetX, pcY, rightSide, zoom);
                }
                return true;
            }
        }
        return false;
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
}
