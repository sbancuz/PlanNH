package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;

import lombok.Getter;

public class CanvasWidget extends ParentWidget<CanvasWidget> implements Interactable {

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

    @Nonnull
    @Getter
    private Graph graph;
    private final Map<UUID, RecipeNodeWidget> nodeWidgets = new HashMap<>();
    private final Map<UUID, NoteWidget> noteWidgets = new HashMap<>();
    private final Map<UUID, GroupWidget> groupWidgets = new HashMap<>();

    @Getter
    private float zoom = 1.0f;
    @Getter
    private float panX = 0;
    @Getter
    private float panY = 0;

    private boolean panning = false;
    private int panStartMouseX, panStartMouseY;
    private float panStartX, panStartY;

    private boolean creatingEdge = false;
    private UUID edgeSourceNodeId;
    private int edgeSourcePortIndex;
    private int edgeEndX, edgeEndY;
    private UUID edgeHoverNodeId;
    private int edgeHoverPortIndex;

    public CanvasWidget(final Graph graph) {
        this.graph = graph;
        rebuildNodeWidgets();
    }

    public int getZoomPercent() {
        return Math.round(zoom * 100);
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
        final GroupWidget gw = new GroupWidget(group, this);
        gw.syncTransform(zoom, panX, panY);
        groupWidgets.put(group.id, gw);
        child(gw);
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
                w.syncTransform(zoom, panX, panY);
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
                final RecipeNodeWidget w = new RecipeNodeWidget(node, this);
                w.syncTransform(zoom, panX, panY);
                nodeWidgets.put(nodeId, w);
                child(w);
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
        final int pad = 12;
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
        group.x = minX - pad;
        group.y = minY - pad;
        group.width = maxX - minX + pad * 2;
        group.height = maxY - minY + pad * 2;
        final GroupWidget gw = groupWidgets.get(group.id);
        if (gw != null) {
            gw.syncTransform(zoom, panX, panY);
        }
        // Move all nodes into the group if auto-sizing
        for (final UUID nid : group.nodeIds) {
            final Node n = graph.nodes.get(nid);
            if (n == null) continue;
            clampNodeToGroup(n);
            final RecipeNodeWidget w = nodeWidgets.get(nid);
            if (w != null) w.syncTransform(zoom, panX, panY);
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
        editingNoteId = null;
        editingGroupId = null;
        nodeWidgets.clear();
        groupWidgets.clear();
        noteWidgets.clear();
        for (final Group group : graph.getGroups()) {
            final GroupWidget gw = new GroupWidget(group, this);
            gw.syncTransform(zoom, panX, panY);
            groupWidgets.put(group.id, gw);
            child(gw);
        }
        for (final Node node : graph.getNodes()) {
            final RecipeNodeWidget widget = new RecipeNodeWidget(node, this);
            widget.syncTransform(zoom, panX, panY);
            nodeWidgets.put(node.id, widget);
            child(widget);
            updateNodeGroupMembership(node);
        }
        rebuildNoteWidgets();
    }

    public void rebuildGroupWidgets() {
        for (final GroupWidget gw : groupWidgets.values()) {
            remove(gw);
        }
        groupWidgets.clear();
        for (final Group group : graph.getGroups()) {
            final GroupWidget gw = new GroupWidget(group, this);
            gw.syncTransform(zoom, panX, panY);
            groupWidgets.put(group.id, gw);
            child(gw);
        }
    }

    public void rebuildNoteWidgets() {
        for (final NoteWidget nw : noteWidgets.values()) {
            remove(nw);
        }
        noteWidgets.clear();
        for (final Note note : graph.notes.values()) {
            final NoteWidget nw = new NoteWidget(note, this);
            nw.syncTransform(zoom, panX, panY);
            noteWidgets.put(note.id, nw);
            child(nw);
        }
    }

    private void updatePositions() {
        for (final RecipeNodeWidget w : nodeWidgets.values()) w.syncTransform(zoom, panX, panY);
        for (final NoteWidget w : noteWidgets.values()) w.syncTransform(zoom, panX, panY);
        for (final GroupWidget w : groupWidgets.values()) w.syncTransform(zoom, panX, panY);
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
        final float spacing = GRID_SIZE * zoom;
        if (spacing < MIN_GRID_SPACING) return;

        final int gridColor = PlannhColors.GRID_LINE.getColor();
        final int majorColor = PlannhColors.GRID_MAJOR.getColor();

        final int firstKX = (int) Math.ceil(-panX / spacing);
        final float startX = panX + firstKX * spacing;
        for (float x = startX; x < w; x += spacing) {
            final int k = firstKX + Math.round((x - startX) / spacing);
            GuiDraw.drawRect(Math.round(x), 0, 1, h, k % GRID_MAJOR == 0 ? majorColor : gridColor);
        }

        final int firstKY = (int) Math.ceil(-panY / spacing);
        final float startY = panY + firstKY * spacing;
        for (float y = startY; y < h; y += spacing) {
            final int k = firstKY + Math.round((y - startY) / spacing);
            GuiDraw.drawRect(0, Math.round(y), w, 1, k % GRID_MAJOR == 0 ? majorColor : gridColor);
        }
    }

    private int widgetX(final RecipeNodeWidget w) {
        return Math.round(w.getNode().x * zoom + panX);
    }

    private int widgetY(final RecipeNodeWidget w) {
        return Math.round(w.getNode().y * zoom + panY);
    }

    private int portY(final int index) {
        return Math.round(((index + 1) * PORT_SPACING + PORT_ORIGIN) * zoom);
    }

    private void drawArrows() {
        for (final Edge edge : graph.getEdges()) {
            final RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            final Node srcNode = graph.nodes.get(edge.sourceNodeId);
            final boolean isFluid = srcNode != null && edge.sourceOutputIndex >= srcNode.outputs.size();

            final int srcX = widgetX(srcWidget) + srcWidget.getArea().width;
            final int srcY = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            final int dstX = widgetX(dstWidget);
            final int dstY = widgetY(dstWidget) + portY(edge.targetInputIndex);

            drawArrow(srcX, srcY, dstX, dstY, isFluid);
        }
    }

    private void drawArrow(final int x1, final int y1, final int x2, final int y2, final boolean fluid) {
        final float as = Math.max(ARROW_MIN_SIZE, ARROW_SIZE * zoom);
        final int ex = Math.round(x2 - as);
        final int color = fluid ? ARROW_COLOR_FLUID : ARROW_COLOR_ITEM;
        drawOrthogonalLine(x1, y1, x2, y2, ex, color, Math.max(LINE_THICK_MIN, LINE_THICK_BASE * zoom));

        final int r = Color.getRed(color);
        final int g = Color.getGreen(color);
        final int b = Color.getBlue(color);
        final int a = Color.getAlpha(color);
        final float hb = as * ARROW_HB_RATIO;
        Platform.startDrawing(Platform.DrawMode.TRIANGLES, Platform.VertexFormat.POS_COLOR, buf -> {
            buf.pos(x2, y2, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(ex, Math.round(y2 - hb), 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(ex, Math.round(y2 + hb), 0)
                .color(r, g, b, a)
                .endVertex();
        });
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

        drawOrthogonalLine(x1, y1, x2, y2, x2, PREVIEW_COLOR, Math.max(LINE_THICK_MIN, LINE_THICK_BASE * zoom));
    }

    private void drawOrthogonalLine(final int x1, final int y1, final int x2, final int y2, final int xEnd,
        final int color, final float thickness) {
        final int midX = (x1 + x2) / 2;
        final int r = Color.getRed(color);
        final int g = Color.getGreen(color);
        final int b = Color.getBlue(color);
        final int a = Color.getAlpha(color);

        Platform.setupDrawColor();
        GL11.glLineWidth(thickness);
        Platform.startDrawing(Platform.DrawMode.LINE_STRIP, Platform.VertexFormat.POS_COLOR, buf -> {
            buf.pos(x1, y1, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(midX, y1, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(midX, y2, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(xEnd, y2, 0)
                .color(r, g, b, a)
                .endVertex();
        });
        GL11.glLineWidth(1);
    }

    @Nullable
    private Edge getEdgeAt(final int absMx, final int absMy) {
        final int margin = Math.max(EDGE_MARGIN_BASE, Math.round(EDGE_MARGIN_BASE * zoom));
        final int cmx = absMx - getArea().x;
        final int cmy = absMy - getArea().y;
        for (final Edge edge : graph.getEdges()) {
            final RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            final RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            final int x1 = widgetX(srcWidget) + srcWidget.getArea().width;
            final int y1 = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            final int x2 = widgetX(dstWidget);
            final int y2 = widgetY(dstWidget) + portY(edge.targetInputIndex);
            final int midX = (x1 + x2) / 2;

            if (cmx >= Math.min(x1, midX) - margin && cmx <= Math.max(x1, midX) + margin
                && cmy >= y1 - margin
                && cmy <= y1 + margin) return edge;
            if (cmx >= midX - margin && cmx <= midX + margin
                && cmy >= Math.min(y1, y2) - margin
                && cmy <= Math.max(y1, y2) + margin) return edge;
            if (cmx >= Math.min(midX, x2) - margin && cmx <= Math.max(midX, x2) + margin
                && cmy >= y2 - margin
                && cmy <= y2 + margin) return edge;
        }
        return null;
    }

    private boolean canConnect(final Node srcNode, final int srcOutIdx, final Node dstNode, final int dstInIdx) {
        if (srcNode == dstNode) return false;
        if (srcOutIdx < 0 || dstInIdx < 0) return false;

        final int itemOutCount = srcNode.outputs.size();
        final int itemInCount = dstNode.inputs.size();
        final int fluidOutCount = srcNode.fluidOutputs.size();
        final int fluidInCount = dstNode.fluidInputs.size();

        final boolean srcIsFluid = srcOutIdx >= itemOutCount;
        final boolean dstIsFluid = dstInIdx >= itemInCount;

        if (srcIsFluid != dstIsFluid) return false;

        if (!srcIsFluid) {
            final ItemStack out = srcNode.outputs.get(srcOutIdx)
                .left();
            final ItemStack in = dstNode.inputs.get(dstInIdx)
                .left();
            return out != null && in != null && out.isItemEqual(in);
        }

        final int srcFluidIdx = srcOutIdx - itemOutCount;
        final int dstFluidIdx = dstInIdx - itemInCount;
        if (srcFluidIdx >= fluidOutCount || dstFluidIdx >= fluidInCount) return false;
        final FluidStack out = srcNode.fluidOutputs.get(srcFluidIdx)
            .left();
        final FluidStack in = dstNode.fluidInputs.get(dstFluidIdx)
            .left();
        return out != null && in != null && out.isFluidEqual(in);
    }

    @Override
    public @Nonnull Result onMousePressed(final int mouseButton) {
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
                panning = true;
                panStartMouseX = absMx;
                panStartMouseY = absMy;
                panStartX = panX;
                panStartY = panY;
                return Interactable.Result.SUCCESS;
            }
            return Interactable.Result.IGNORE;
        }
        if (mouseButton == 1) {
            final int cmx = absMx - getArea().x;
            final int cmy = absMy - getArea().y;

            // Check if over a group header (pass click through for its own right-click menu)
            for (final GroupWidget gw : groupWidgets.values()) {
                final Area a = gw.getArea();
                if (absMx >= a.x && absMx < a.x + a.width && absMy >= a.y && absMy < a.y + a.height) {
                    showGroupContextMenu(gw, cmx, cmy);
                    return Result.SUCCESS;
                }
            }

            // Check if over a node
            for (final RecipeNodeWidget widget : nodeWidgets.values()) {
                final Area a = widget.getArea();
                if (absMx >= a.x && absMx < a.x + a.width && absMy >= a.y && absMy < a.y + a.height) {
                    showNodeContextMenu(widget, cmx, cmy);
                    return Result.SUCCESS;
                }
            }

            // Empty canvas
            showCanvasContextMenu(cmx, cmy);
            return Result.SUCCESS;
        }
        if (mouseButton == 2) {
            panning = true;
            panStartMouseX = getContext().getAbsMouseX();
            panStartMouseY = getContext().getAbsMouseY();
            panStartX = panX;
            panStartY = panY;
            return Interactable.Result.SUCCESS;
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
        panning = false;
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
        } else if (panning) {
            final int dx = getContext().getAbsMouseX() - panStartMouseX;
            final int dy = getContext().getAbsMouseY() - panStartMouseY;
            panX = panStartX + dx;
            panY = panStartY + dy;
            updatePositions();
        }
    }

    @Override
    public boolean onMouseScroll(final UpOrDown direction, final int amount) {
        float delta = direction == UpOrDown.UP ? 0.15f : -0.15f;
        delta *= Math.max(1, Math.abs(amount));
        final float oldZoom = zoom;
        zoom = Math.clamp(zoom + delta, 0.1f, 5.0f);
        final float ratio = zoom / oldZoom;

        final int mx = getContext().getAbsMouseX();
        final int my = getContext().getAbsMouseY();

        final float mxRel = mx - getArea().x;
        final float myRel = my - getArea().y;

        panX = mxRel - (mxRel - panX) * ratio;
        panY = myRel - (myRel - panY) * ratio;

        updatePositions();
        return true;
    }

    private boolean isMouseOverAnyGroup(final int mx, final int my) {
        for (final GroupWidget gw : groupWidgets.values()) {
            final Area a = gw.getArea();
            if (mx >= a.x && mx <= a.x + a.width && my >= a.y && my <= a.y + a.height) {
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOverAnyNode(final int mx, final int my) {
        for (final RecipeNodeWidget widget : nodeWidgets.values()) {
            final Area a = widget.getArea();
            if (mx >= a.x && mx <= a.x + a.width && my >= a.y && my <= a.y + a.height) {
                return true;
            }
        }
        return false;
    }

    // ── Notes ──

    public void addNote(final int x, final int y) {
        final Note note = new Note(UUID.randomUUID(), x, y);
        graph.notes.put(note.id, note);
        final NoteWidget nw = new NoteWidget(note, this);
        nw.syncTransform(zoom, panX, panY);
        noteWidgets.put(note.id, nw);
        child(nw);
    }

    public void removeNote(final UUID noteId) {
        final NoteWidget nw = noteWidgets.remove(noteId);
        if (nw != null) remove(nw);
        graph.notes.remove(noteId);
    }

    @Nullable
    private UUID editingNoteId = null;
    @Nullable
    private UUID editingGroupId = null;

    public void startEditingNote(final UUID noteId) {
        editingNoteId = noteId;
        for (final NoteWidget nw : noteWidgets.values()) {
            nw.setEditing(nw.getNote().id.equals(noteId));
        }
    }

    public void onNoteEditingDone() {
        editingNoteId = null;
    }

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
    public @Nonnull Result onKeyPressed(final char typedChar, final int keyCode) {
        if (editingNoteId != null) {
            final NoteWidget nw = noteWidgets.get(editingNoteId);
            if (nw == null) {
                editingNoteId = null;
                return Result.IGNORE;
            }
            final Note note = nw.getNote();
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                nw.setEditing(false);
                return Result.SUCCESS;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_BACK) {
                if (!note.text.isEmpty()) {
                    note.text = note.text.substring(0, note.text.length() - 1);
                }
                return Result.SUCCESS;
            }
            if (typedChar >= 32 && typedChar < 127) {
                note.text += typedChar;
                return Result.SUCCESS;
            }
            return Result.SUCCESS;
        }
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
        return contextMenu != null && mx >= contextMenu.getArea().x
            && mx < contextMenu.getArea().x + contextMenu.getArea().width
            && my >= contextMenu.getArea().y
            && my < contextMenu.getArea().y + contextMenu.getArea().height;
    }

    private void openColorPickerFor(final Group group) {
        final int currentColor = group.colorOverride != 0 ? group.colorOverride : PlannhColors.titleColor(group.title);
        final ColorPickerDialog picker = new ColorPickerDialog(chosenColor -> {
            group.colorOverride = chosenColor;
            rebuildGroupWidgets();
        }, currentColor, false);
        IPanelHandler.simple(getPanel(), (parent, player) -> picker, true)
            .openPanel();
    }

    private void contextMenuAt(final int cmx, final int cmy, final List<ContextMenuWidget.MenuItem> items) {
        final int ax = getArea().x + cmx;
        final int ay = getArea().y + cmy;
        showContextMenu(ax, ay, items);
    }

    private void showCanvasContextMenu(final int cmx, final int cmy) {
        final List<ContextMenuWidget.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenuWidget.MenuItem("Add Group", () -> {
            int gx = Math.round((cmx - panX) / zoom);
            int gy = Math.round((cmy - panY) / zoom);
            if (gx < 0) gx = 0;
            if (gy < 0) gy = 0;
            addGroup(gx, gy);
        }));
        items.add(new ContextMenuWidget.MenuItem("Add Note", () -> {
            int nx = Math.round((cmx - panX) / zoom);
            int ny = Math.round((cmy - panY) / zoom);
            if (nx < 0) nx = 0;
            if (ny < 0) ny = 0;
            addNote(nx, ny);
        }));
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
                () -> { group.clampNodes = !group.clampNodes; }));
        items.add(
            new ContextMenuWidget.MenuItem(
                group.autoResize ? "Disable Auto-Resize" : "Enable Auto-Resize",
                () -> { group.autoResize = !group.autoResize; }));
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
                    () -> { currentGroup.nodeIds.remove(node.id); }));
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
        final int ps = Math.round(PORT_S * zoom);
        final int half = Math.round(PORT_HALF * zoom);

        for (final RecipeNodeWidget w : nodeWidgets.values()) {
            final Node node = w.getNode();
            final int widgetX = widgetX(w);
            final int widgetY = widgetY(w);
            final int widgetWidth = w.getArea().width;

            if (tryPortLabel(
                mouseX,
                mouseY,
                zoom,
                ps,
                half,
                widgetX,
                widgetY,
                widgetWidth,
                node.outputs.size(),
                0,
                true,
                i -> node.outputs.get(i)
                    .left()
                    .getDisplayName()))
                return;
            if (tryPortLabel(
                mouseX,
                mouseY,
                zoom,
                ps,
                half,
                widgetX,
                widgetY,
                widgetWidth,
                node.fluidOutputs.size(),
                node.outputs.size(),
                true,
                i -> node.fluidOutputs.get(i)
                    .left()
                    .getLocalizedName()))
                return;
            if (tryPortLabel(
                mouseX,
                mouseY,
                zoom,
                ps,
                half,
                widgetX,
                widgetY,
                widgetWidth,
                node.inputs.size(),
                0,
                false,
                i -> node.inputs.get(i)
                    .left()
                    .getDisplayName()))
                return;
            if (tryPortLabel(
                mouseX,
                mouseY,
                zoom,
                ps,
                half,
                widgetX,
                widgetY,
                widgetWidth,
                node.fluidInputs.size(),
                node.inputs.size(),
                false,
                i -> node.fluidInputs.get(i)
                    .left()
                    .getLocalizedName()))
                return;
        }
    }

    private boolean tryPortLabel(final int mouseX, final int mouseY, final float zoom, final int ps, final int half,
        final int widgetX, final int widgetY, final int widgetWidth, final int portCount, final int portOffset,
        final boolean rightSide, final IntFunction<String> labelFn) {
        for (int i = 0; i < portCount; i++) {
            final int fi = portOffset + i;
            final int px = rightSide ? widgetX + widgetWidth - ps : widgetX;
            final int pcY = widgetY + portY(fi);
            if (mouseX >= px && mouseX < px + ps && mouseY >= pcY - half && mouseY < pcY + half) {
                drawPortLabel(labelFn.apply(i), rightSide ? widgetX + widgetWidth : widgetX, pcY, rightSide, zoom);
                return true;
            }
        }
        return false;
    }

    private static void drawPortLabel(String name, final int anchorX, final int centerY, final boolean rightSide,
        final float z) {
        if (name.length() > PORT_LABEL_MAX) name = name.substring(0, PORT_LABEL_TRUNC) + "\u2026";
        final int tw = Minecraft.getMinecraft().fontRenderer.getStringWidth(name);
        final int fh = Math.round(PORT_FONT_SIZE * z * PORT_FONT_SCALE);
        final int gap = Math.round(PORT_GAP * z);
        final int labelX = rightSide ? anchorX + gap : anchorX - tw - gap;
        final int labelY = centerY - fh / 2;
        final int pad = Math.round(PORT_LABEL_PAD * z);
        GuiDraw.drawRect(labelX - pad, labelY - pad, tw + pad * 2, fh + pad * 2, PlannhColors.PORT_LABEL_BG.getColor());
        GuiDraw.drawText(name, labelX, labelY, z * 0.9f, PlannhColors.TEXT_WHITE.getColor(), false);
    }
}
