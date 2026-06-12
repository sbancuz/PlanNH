package com.sbancuz.plannh.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

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
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.ColorPickerDialog;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
import com.sbancuz.plannh.data.flowchart.Group;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Note;

import lombok.Getter;

public class CanvasWidget extends ParentWidget<CanvasWidget> implements Interactable {

    private static final int ARROW_COLOR_ITEM = PlannhColors.ARROW_ITEM.getColor();
    private static final int ARROW_COLOR_FLUID = PlannhColors.ARROW_FLUID.getColor();
    private static final int PREVIEW_COLOR = PlannhColors.PREVIEW_HIGHLIGHT.getColor();

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

    public CanvasWidget(Graph graph) {
        this.graph = graph;
        rebuildNodeWidgets();
    }

    public int getZoomPercent() {
        return Math.round(zoom * 100);
    }

    public void removeNode(UUID nodeId) {
        graph.removeNode(nodeId);
        for (Group group : graph.getGroups()) {
            group.nodeIds.remove(nodeId);
        }
        rebuildNodeWidgets();
    }

    public void setGraph(Graph newGraph) {
        this.graph = newGraph;
        rebuildNodeWidgets();
        rebuildNoteWidgets();
        rebuildGroupWidgets();
    }

    public void removeGroup(UUID groupId) {
        graph.removeGroup(groupId);
        rebuildGroupWidgets();
    }

    public void addGroup(int x, int y) {
        int n = 1;
        for (Group g : graph.getGroups()) {
            if (g.title.startsWith("Group")) {
                try {
                    int num = Integer.parseInt(
                        g.title.substring(5)
                            .trim());
                    if (num >= n) n = num + 1;
                } catch (NumberFormatException ignored) {}
            }
        }
        Group group = new Group(x, y, "Group " + n);
        graph.addGroup(group);
        GroupWidget gw = new GroupWidget(group, this);
        gw.syncTransform(zoom, panX, panY);
        groupWidgets.put(group.id, gw);
        child(gw);
    }

    public void moveGroupNodes(UUID groupId, int deltaX, int deltaY) {
        Group group = graph.groups.get(groupId);
        if (group == null) return;
        for (UUID nodeId : group.nodeIds) {
            Node node = graph.nodes.get(nodeId);
            if (node == null) continue;
            node.x += deltaX;
            node.y += deltaY;
            RecipeNodeWidget w = nodeWidgets.get(nodeId);
            if (w != null) {
                w.syncTransform(zoom, panX, panY);
            }
        }
    }

    public void hideGroupNodes(UUID groupId) {
        Group group = graph.groups.get(groupId);
        if (group == null) return;
        for (UUID nodeId : group.nodeIds) {
            RecipeNodeWidget w = nodeWidgets.remove(nodeId);
            if (w != null) {
                remove(w);
            }
        }
    }

    public void showGroupNodes(UUID groupId) {
        Group group = graph.groups.get(groupId);
        if (group == null) return;
        for (UUID nodeId : group.nodeIds) {
            if (nodeWidgets.containsKey(nodeId)) continue;
            Node node = graph.nodes.get(nodeId);
            if (node == null) continue;
            RecipeNodeWidget w = new RecipeNodeWidget(node, this);
            w.syncTransform(zoom, panX, panY);
            nodeWidgets.put(nodeId, w);
            child(w);
        }
    }

    public void onGroupDragFinished() {
        for (Node node : graph.getNodes()) {
            updateNodeGroupMembership(node);
        }
        autoFitGroups();
    }

    public void onGroupResizeFinished() {
        for (Node node : graph.getNodes()) {
            updateNodeGroupMembership(node);
        }
        autoFitGroups();
    }

    public void onNodeDragFinished() {
        for (Node node : graph.getNodes()) {
            updateNodeGroupMembership(node);
        }
        autoFitGroups();
    }

    public Group getGroupForNode(UUID nodeId) {
        for (Group g : graph.getGroups()) {
            if (g.nodeIds.contains(nodeId)) return g;
        }
        return null;
    }

    private static final int CLAMP_MARGIN = 4;
    private static final int NODE_W_ESTIMATE = 120;
    private static final int NODE_H_ESTIMATE = 80;

    public void clampNodeToGroup(Node node) {
        Group group = getGroupForNode(node.id);
        if (group == null || !group.clampNodes) return;
        if (node.x < group.x + CLAMP_MARGIN) node.x = group.x + CLAMP_MARGIN;
        if (node.y < group.y + CLAMP_MARGIN + HEADER_OFFSET) node.y = group.y + CLAMP_MARGIN + HEADER_OFFSET;
        if (node.x + NODE_W_ESTIMATE > group.x + group.width - CLAMP_MARGIN)
            node.x = group.x + group.width - CLAMP_MARGIN - NODE_W_ESTIMATE;
        if (node.y + NODE_H_ESTIMATE > group.y + group.height - CLAMP_MARGIN)
            node.y = group.y + group.height - CLAMP_MARGIN - NODE_H_ESTIMATE;
    }

    private static final int HEADER_OFFSET = 24;

    private void autoFitGroups() {
        for (Group group : graph.getGroups()) {
            if (!group.autoResize || group.nodeIds.isEmpty()) continue;
            fitGroupToChildren(group);
        }
    }

    public void fitGroupToChildren(Group group) {
        if (group.nodeIds.isEmpty()) return;
        int pad = 12;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (UUID nid : group.nodeIds) {
            Node n = graph.nodes.get(nid);
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
        GroupWidget gw = groupWidgets.get(group.id);
        if (gw != null) {
            gw.syncTransform(zoom, panX, panY);
        }
        // Move all nodes into the group if auto-sizing
        for (UUID nid : group.nodeIds) {
            Node n = graph.nodes.get(nid);
            if (n == null) continue;
            clampNodeToGroup(n);
            RecipeNodeWidget w = nodeWidgets.get(nid);
            if (w != null) w.syncTransform(zoom, panX, panY);
        }
    }

    private void updateNodeGroupMembership(Node node) {
        for (Group group : graph.getGroups()) {
            if (group.collapsed) continue;
            boolean inside = node.x >= group.x && node.x < group.x + group.width
                && node.y >= group.y
                && node.y < group.y + group.height;
            boolean contained = group.nodeIds.contains(node.id);
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
        for (Group group : graph.getGroups()) {
            GroupWidget gw = new GroupWidget(group, this);
            gw.syncTransform(zoom, panX, panY);
            groupWidgets.put(group.id, gw);
            child(gw);
        }
        for (Node node : graph.getNodes()) {
            RecipeNodeWidget widget = new RecipeNodeWidget(node, this);
            widget.syncTransform(zoom, panX, panY);
            nodeWidgets.put(node.id, widget);
            child(widget);
            updateNodeGroupMembership(node);
        }
        rebuildNoteWidgets();
    }

    public void rebuildGroupWidgets() {
        for (GroupWidget gw : groupWidgets.values()) {
            remove(gw);
        }
        groupWidgets.clear();
        for (Group group : graph.getGroups()) {
            GroupWidget gw = new GroupWidget(group, this);
            gw.syncTransform(zoom, panX, panY);
            groupWidgets.put(group.id, gw);
            child(gw);
        }
    }

    public void rebuildNoteWidgets() {
        for (NoteWidget nw : noteWidgets.values()) {
            remove(nw);
        }
        noteWidgets.clear();
        for (Note note : graph.notes.values()) {
            NoteWidget nw = new NoteWidget(note, this);
            nw.syncTransform(zoom, panX, panY);
            noteWidgets.put(note.id, nw);
            child(nw);
        }
    }

    private void updateNodePositions() {
        for (Map.Entry<UUID, RecipeNodeWidget> entry : nodeWidgets.entrySet()) {
            Node node = graph.nodes.get(entry.getKey());
            if (node == null) continue;
            RecipeNodeWidget widget = entry.getValue();
            widget.syncTransform(zoom, panX, panY);
        }
        updateNotePositions();
        updateGroupPositions();
    }

    private void updateNotePositions() {
        for (NoteWidget nw : noteWidgets.values()) {
            nw.syncTransform(zoom, panX, panY);
        }
    }

    private void updateGroupPositions() {
        for (GroupWidget gw : groupWidgets.values()) {
            gw.syncTransform(zoom, panX, panY);
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int aw = getArea().width;
        int ah = getArea().height;
        if (aw <= 0 || ah <= 0) return;

        super.draw(context, widgetTheme);

        drawArrows();

        if (creatingEdge) {
            drawPreviewLine();
        }

        drawHoveredPortLabels();
    }

    private int widgetX(RecipeNodeWidget w) {
        return Math.round(w.getNode().x * zoom + panX);
    }

    private int widgetY(RecipeNodeWidget w) {
        return Math.round(w.getNode().y * zoom + panY);
    }

    private int portY(int index) {
        return Math.round(((index + 1) * 18 + 10) * zoom);
    }

    private void drawArrows() {
        for (Edge edge : graph.getEdges()) {
            RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            Node srcNode = graph.nodes.get(edge.sourceNodeId);
            boolean isFluid = srcNode != null && edge.sourceOutputIndex >= srcNode.outputs.size();

            int srcX = widgetX(srcWidget) + srcWidget.getArea().width;
            int srcY = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            int dstX = widgetX(dstWidget);
            int dstY = widgetY(dstWidget) + portY(edge.targetInputIndex);

            drawArrow(srcX, srcY, dstX, dstY, isFluid);
        }
    }

    private void drawArrow(int x1, int y1, int x2, int y2, boolean fluid) {
        int midX = (x1 + x2) / 2;
        float as = Math.max(4, 6 * zoom);
        float thick = Math.max(1, 2 * zoom);

        int color = fluid ? ARROW_COLOR_FLUID : ARROW_COLOR_ITEM;
        int r = Color.getRed(color);
        int g = Color.getGreen(color);
        int b = Color.getBlue(color);
        int a = Color.getAlpha(color);

        Platform.setupDrawColor();
        GL11.glLineWidth(thick);
        int ex = Math.round(x2 - as);
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
            buf.pos(ex, y2, 0)
                .color(r, g, b, a)
                .endVertex();
        });
        GL11.glLineWidth(1);

        float hb = as * 0.35f;
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
        RecipeNodeWidget srcWidget = nodeWidgets.get(edgeSourceNodeId);
        if (srcWidget == null) return;

        int x1 = widgetX(srcWidget) + srcWidget.getArea().width;
        int y1 = widgetY(srcWidget) + portY(edgeSourcePortIndex);

        int x2 = edgeEndX;
        int y2 = edgeEndY;

        if (edgeHoverNodeId != null) {
            RecipeNodeWidget dstWidget = nodeWidgets.get(edgeHoverNodeId);
            if (dstWidget != null) {
                x2 = widgetX(dstWidget);
                y2 = widgetY(dstWidget) + portY(edgeHoverPortIndex);
            }
        }

        int midX = (x1 + x2) / 2;
        int thick = Math.max(1, Math.round(2 * zoom));

        int r = Color.getRed(PREVIEW_COLOR);
        int g = Color.getGreen(PREVIEW_COLOR);
        int b = Color.getBlue(PREVIEW_COLOR);
        int a = Color.getAlpha(PREVIEW_COLOR);

        int fx1 = x1, fy1 = y1, fmidX = midX, fx2 = x2, fy2 = y2;

        Platform.setupDrawColor();
        GL11.glLineWidth(thick);
        Platform.startDrawing(Platform.DrawMode.LINE_STRIP, Platform.VertexFormat.POS_COLOR, buf -> {
            buf.pos(fx1, fy1, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(fmidX, fy1, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(fmidX, fy2, 0)
                .color(r, g, b, a)
                .endVertex();
            buf.pos(fx2, fy2, 0)
                .color(r, g, b, a)
                .endVertex();
        });
        GL11.glLineWidth(1);
    }

    private Edge getEdgeAt(int absMx, int absMy) {
        int margin = Math.max(4, Math.round(4 * zoom));
        int cmx = absMx - getArea().x;
        int cmy = absMy - getArea().y;
        for (Edge edge : graph.getEdges()) {
            RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            int x1 = widgetX(srcWidget) + srcWidget.getArea().width;
            int y1 = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            int x2 = widgetX(dstWidget);
            int y2 = widgetY(dstWidget) + portY(edge.targetInputIndex);
            int midX = (x1 + x2) / 2;

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

    private boolean canConnect(Node srcNode, int srcOutIdx, Node dstNode, int dstInIdx) {
        if (srcNode == dstNode) return false;
        if (srcOutIdx < 0 || dstInIdx < 0) return false;

        int itemOutCount = srcNode.outputs.size();
        int itemInCount = dstNode.inputs.size();
        int fluidOutCount = srcNode.fluidOutputs.size();
        int fluidInCount = dstNode.fluidInputs.size();

        boolean srcIsFluid = srcOutIdx >= itemOutCount;
        boolean dstIsFluid = dstInIdx >= itemInCount;

        if (srcIsFluid != dstIsFluid) return false;

        if (!srcIsFluid) {
            if (srcOutIdx >= itemOutCount || dstInIdx >= itemInCount) return false;
            ItemStack out = srcNode.outputs.get(srcOutIdx)
                .left();
            ItemStack in = dstNode.inputs.get(dstInIdx)
                .left();
            return out != null && in != null && out.isItemEqual(in);
        }

        int srcFluidIdx = srcOutIdx - itemOutCount;
        int dstFluidIdx = dstInIdx - itemInCount;
        if (srcFluidIdx >= fluidOutCount || dstFluidIdx >= fluidInCount) return false;
        FluidStack out = srcNode.fluidOutputs.get(srcFluidIdx)
            .left();
        FluidStack in = dstNode.fluidInputs.get(dstFluidIdx)
            .left();
        return out != null && in != null && out.isFluidEqual(in);
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        int absMx = getContext().getAbsMouseX();
        int absMy = getContext().getAbsMouseY();

        // Close context menu on any click, unless it's on the menu itself
        if (contextMenu != null) {
            if (isMouseOverContextMenu(absMx, absMy)) {
                return Result.IGNORE;
            }
            closeContextMenu();
        }

        if (mouseButton == 0) {
            int cmx = absMx - getArea().x;
            int cmy = absMy - getArea().y;
            for (RecipeNodeWidget widget : nodeWidgets.values()) {
                int localMx = cmx - widgetX(widget);
                int localMy = cmy - widgetY(widget);
                int port = widget.getOutputPortAt(localMx, localMy);
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
                Edge clicked = getEdgeAt(absMx, absMy);
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
            int cmx = absMx - getArea().x;
            int cmy = absMy - getArea().y;

            // Check if over a group header (pass click through for its own right-click menu)
            for (GroupWidget gw : groupWidgets.values()) {
                Area a = gw.getArea();
                if (absMx >= a.x && absMx < a.x + a.width && absMy >= a.y && absMy < a.y + a.height) {
                    showGroupContextMenu(gw, cmx, cmy);
                    return Result.SUCCESS;
                }
            }

            // Check if over a node
            for (RecipeNodeWidget widget : nodeWidgets.values()) {
                Area a = widget.getArea();
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
    public boolean onMouseRelease(int mouseButton) {
        if (creatingEdge) {
            if (edgeHoverNodeId != null) {
                Node srcNode = graph.nodes.get(edgeSourceNodeId);
                Node dstNode = graph.nodes.get(edgeHoverNodeId);
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
    public void onMouseDrag(int mouseButton, long timeSinceClick) {
        if (creatingEdge) {
            int cmx = getContext().getAbsMouseX() - getArea().x;
            int cmy = getContext().getAbsMouseY() - getArea().y;
            edgeEndX = cmx;
            edgeEndY = cmy;

            edgeHoverNodeId = null;
            edgeHoverPortIndex = -1;

            RecipeNodeWidget srcWidget = nodeWidgets.get(edgeSourceNodeId);
            if (srcWidget == null) return;

            for (RecipeNodeWidget widget : nodeWidgets.values()) {
                if (widget == srcWidget) continue;
                int localMx = cmx - widgetX(widget);
                int localMy = cmy - widgetY(widget);
                int port = widget.getInputPortAt(localMx, localMy);
                if (port >= 0 && canConnect(srcWidget.getNode(), edgeSourcePortIndex, widget.getNode(), port)) {
                    edgeHoverNodeId = widget.getNode().id;
                    edgeHoverPortIndex = port;
                    break;
                }
            }
        } else if (panning) {
            int dx = getContext().getAbsMouseX() - panStartMouseX;
            int dy = getContext().getAbsMouseY() - panStartMouseY;
            panX = panStartX + dx;
            panY = panStartY + dy;
            updateNodePositions();
        }
    }

    @Override
    public boolean onMouseScroll(UpOrDown direction, int amount) {
        float delta = direction == UpOrDown.UP ? 0.15f : -0.15f;
        delta *= Math.max(1, Math.abs(amount));
        float oldZoom = zoom;
        zoom = Math.clamp(zoom + delta, 0.1f, 5.0f);
        float ratio = zoom / oldZoom;

        int mx = getContext().getAbsMouseX();
        int my = getContext().getAbsMouseY();

        float mxRel = mx - getArea().x;
        float myRel = my - getArea().y;

        panX = mxRel - (mxRel - panX) * ratio;
        panY = myRel - (myRel - panY) * ratio;

        updateNodePositions();
        return true;
    }

    private boolean isMouseOverAnyGroup(int mx, int my) {
        for (GroupWidget gw : groupWidgets.values()) {
            Area a = gw.getArea();
            if (mx >= a.x && mx <= a.x + a.width && my >= a.y && my <= a.y + a.height) {
                return true;
            }
        }
        return false;
    }

    private boolean isMouseOverAnyNode(int mx, int my) {
        for (RecipeNodeWidget widget : nodeWidgets.values()) {
            Area a = widget.getArea();
            if (mx >= a.x && mx <= a.x + a.width && my >= a.y && my <= a.y + a.height) {
                return true;
            }
        }
        return false;
    }

    // ── Notes ──

    public void addNote(int x, int y) {
        Note note = new Note(UUID.randomUUID(), x, y);
        graph.notes.put(note.id, note);
        NoteWidget nw = new NoteWidget(note, this);
        nw.syncTransform(zoom, panX, panY);
        noteWidgets.put(note.id, nw);
        child(nw);
    }

    public void removeNote(UUID noteId) {
        NoteWidget nw = noteWidgets.remove(noteId);
        if (nw != null) remove(nw);
        graph.notes.remove(noteId);
    }

    private UUID editingNoteId = null;
    private UUID editingGroupId = null;

    public void startEditingNote(UUID noteId) {
        editingNoteId = noteId;
        for (NoteWidget nw : noteWidgets.values()) {
            nw.setEditing(nw.getNote().id.equals(noteId));
        }
    }

    public void onNoteEditingDone() {
        editingNoteId = null;
    }

    public void startEditingGroup(UUID groupId) {
        editingGroupId = groupId;
        for (GroupWidget gw : groupWidgets.values()) {
            gw.setEditing(gw.getGroup().id.equals(groupId));
        }
    }

    public void onGroupEditingDone() {
        editingGroupId = null;
    }

    @Override
    public Result onKeyPressed(char typedChar, int keyCode) {
        if (editingNoteId != null) {
            NoteWidget nw = noteWidgets.get(editingNoteId);
            if (nw == null) {
                editingNoteId = null;
                return Result.IGNORE;
            }
            Note note = nw.getNote();
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE || keyCode == org.lwjgl.input.Keyboard.KEY_RETURN) {
                nw.setEditing(false);
                return Result.SUCCESS;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_BACK) {
                if (note.text.length() > 0) {
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
            GroupWidget gw = groupWidgets.get(editingGroupId);
            if (gw == null) {
                editingGroupId = null;
                return Result.IGNORE;
            }
            Group group = gw.getGroup();
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE || keyCode == org.lwjgl.input.Keyboard.KEY_RETURN) {
                gw.setEditing(false);
                return Result.SUCCESS;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_BACK) {
                if (group.title.length() > 0) {
                    if (gw.getCursorPos() > 0) {
                        int cp = gw.getCursorPos();
                        group.title = group.title.substring(0, cp - 1) + group.title.substring(cp);
                        gw.decrementCursor();
                    }
                }
                return Result.SUCCESS;
            }
            if (typedChar >= 32 && typedChar < 127) {
                int cp = gw.getCursorPos();
                group.title = group.title.substring(0, cp) + typedChar + group.title.substring(cp);
                gw.incrementCursor();
                return Result.SUCCESS;
            }
            return Result.SUCCESS;
        }
        return Result.IGNORE;
    }

    public Note getNoteForEdit() {
        if (editingNoteId == null) return null;
        return graph.notes.get(editingNoteId);
    }

    // ── Context Menu ──

    public static class ContextMenu extends Widget<ContextMenu> implements Interactable {

        private static final int ITEM_H = 14;
        private static final int PAD = 3;

        public record MenuItem(String label, Runnable action) {}

        private final List<MenuItem> items;
        private final CanvasWidget canvas;

        public ContextMenu(CanvasWidget canvas, List<MenuItem> items, int x, int y) {
            this.canvas = canvas;
            this.items = items;
            Minecraft mc = Minecraft.getMinecraft();
            int maxW = 80;
            for (MenuItem item : items) {
                int w = mc.fontRenderer.getStringWidth(item.label);
                if (w > maxW) maxW = w;
            }
            pos(x, y);
            size(maxW + PAD * 2 + 4, items.size() * ITEM_H + PAD * 2);
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            int w = getArea().width;
            int h = getArea().height;
            int mx = getContext().getMouseX();
            int my = getContext().getMouseY();

            GuiDraw.drawRect(0, 0, w, h, 0xE6323237);
            GuiDraw.drawRect(0, 0, w, 1, 0x9064A0DC);
            GuiDraw.drawRect(0, h - 1, w, 1, 0x9064A0DC);
            GuiDraw.drawRect(0, 0, 1, h, 0x9064A0DC);
            GuiDraw.drawRect(w - 1, 0, 1, h, 0x9064A0DC);

            for (int i = 0; i < items.size(); i++) {
                int iy = PAD + i * ITEM_H;
                if (mx >= PAD && mx < w - PAD && my >= iy && my < iy + ITEM_H) {
                    GuiDraw.drawRect(PAD, iy, w - PAD * 2, ITEM_H, 0x3C64C864);
                }
                GuiDraw.drawText(items.get(i).label, PAD + 2, iy + 2, 1.0f, PlannhColors.TEXT_WHITE.getColor(), false);
            }
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            if (mouseButton == 0) {
                int mx = getContext().getMouseX();
                int my = getContext().getMouseY();
                for (int i = 0; i < items.size(); i++) {
                    int iy = PAD + i * ITEM_H;
                    if (mx >= PAD && mx < getArea().width - PAD && my >= iy && my < iy + ITEM_H) {
                        items.get(i).action.run();
                        break;
                    }
                }
                canvas.closeContextMenu();
                return Result.SUCCESS;
            }
            return Result.SUCCESS;
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            return true;
        }
    }

    private ContextMenu contextMenu = null;

    public void showContextMenu(int x, int y, List<ContextMenu.MenuItem> items) {
        closeContextMenu();
        contextMenu = new ContextMenu(this, items, x, y);
        child(contextMenu);
    }

    public void closeContextMenu() {
        if (contextMenu != null) {
            remove(contextMenu);
            contextMenu = null;
        }
    }

    public boolean isMouseOverContextMenu(int mx, int my) {
        return contextMenu != null && mx >= contextMenu.getArea().x
            && mx < contextMenu.getArea().x + contextMenu.getArea().width
            && my >= contextMenu.getArea().y
            && my < contextMenu.getArea().y + contextMenu.getArea().height;
    }

    private void showCanvasContextMenu(int cmx, int cmy) {
        List<ContextMenu.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenu.MenuItem("Add Group", () -> {
            int gx = Math.round((cmx - canvasPanX()) / zoom);
            int gy = Math.round((cmy - canvasPanY()) / zoom);
            if (gx < 0) gx = 0;
            if (gy < 0) gy = 0;
            addGroup(gx, gy);
        }));
        items.add(new ContextMenu.MenuItem("Add Note", () -> {
            int nx = Math.round((cmx - canvasPanX()) / zoom);
            int ny = Math.round((cmy - canvasPanY()) / zoom);
            if (nx < 0) nx = 0;
            if (ny < 0) ny = 0;
            addNote(nx, ny);
        }));
        int ax = getArea().x + cmx;
        int ay = getArea().y + cmy;
        showContextMenu(ax, ay, items);
    }

    private float canvasPanX() {
        return panX;
    }

    private float canvasPanY() {
        return panY;
    }

    private void showGroupContextMenu(GroupWidget gw, int cmx, int cmy) {
        Group group = gw.getGroup();
        List<ContextMenu.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenu.MenuItem("Rename Group", () -> startEditingGroup(group.id)));
        items.add(new ContextMenu.MenuItem("Customize Color", () -> {
            int currentColor = group.colorOverride != 0 ? group.colorOverride : PlannhColors.titleColor(group.title);
            ColorPickerDialog picker = new ColorPickerDialog(chosenColor -> {
                group.colorOverride = chosenColor;
                rebuildGroupWidgets();
            }, currentColor, false);
            IPanelHandler.simple(getPanel(), (parent, player) -> picker, true)
                .openPanel();
        }));
        items.add(
            new ContextMenu.MenuItem(
                group.clampNodes ? "Disable Clamp" : "Enable Clamp",
                () -> { group.clampNodes = !group.clampNodes; }));
        items.add(
            new ContextMenu.MenuItem(
                group.autoResize ? "Disable Auto-Resize" : "Enable Auto-Resize",
                () -> { group.autoResize = !group.autoResize; }));
        items.add(new ContextMenu.MenuItem("Delete Group", () -> removeGroup(group.id)));
        int ax = getArea().x + cmx;
        int ay = getArea().y + cmy;
        showContextMenu(ax, ay, items);
    }

    private void showNodeContextMenu(RecipeNodeWidget nw, int cmx, int cmy) {
        Node node = nw.getNode();
        List<ContextMenu.MenuItem> items = new ArrayList<>();
        Group currentGroup = getGroupForNode(node.id);
        if (currentGroup != null) {
            items.add(
                new ContextMenu.MenuItem(
                    "Remove from \"" + currentGroup.title + "\"",
                    () -> { currentGroup.nodeIds.remove(node.id); }));
        }
        List<Group> otherGroups = new ArrayList<>();
        for (Group g : graph.getGroups()) {
            if (g != currentGroup) otherGroups.add(g);
        }
        if (!otherGroups.isEmpty()) {
            for (Group g : otherGroups) {
                items.add(new ContextMenu.MenuItem("Add to \"" + g.title + "\"", () -> g.nodeIds.add(node.id)));
            }
        }
        if (items.isEmpty()) return;
        int ax = getArea().x + cmx;
        int ay = getArea().y + cmy;
        showContextMenu(ax, ay, items);
    }

    // ── Hovered port labels ──

    private static final int PORT_S = 8;
    private static final int PORT_HALF = 4;
    private static final int PORT_GAP = 6;

    private void drawHoveredPortLabels() {
        int mx = getContext().getMouseX();
        int my = getContext().getMouseY();
        float z = zoom;
        int ps = Math.round(PORT_S * z);
        int half = Math.round(PORT_HALF * z);

        for (RecipeNodeWidget w : nodeWidgets.values()) {
            Node node = w.getNode();
            int wx = widgetX(w);
            int wy = widgetY(w);
            int ww = w.getArea().width;

            for (int i = 0; i < node.outputs.size(); i++) {
                int px = wx + ww - ps;
                int pcY = wy + portY(i);
                if (mx >= px && mx < px + ps && my >= pcY - half && my < pcY + half) {
                    drawPortLabel(
                        node.outputs.get(i)
                            .left()
                            .getDisplayName(),
                        wx + ww,
                        pcY,
                        true,
                        z);
                    return;
                }
            }

            for (int i = 0; i < node.fluidOutputs.size(); i++) {
                int fi = node.outputs.size() + i;
                int px = wx + ww - ps;
                int pcY = wy + portY(fi);
                if (mx >= px && mx < px + ps && my >= pcY - half && my < pcY + half) {
                    drawPortLabel(
                        node.fluidOutputs.get(i)
                            .left()
                            .getLocalizedName(),
                        wx + ww,
                        pcY,
                        true,
                        z);
                    return;
                }
            }

            for (int i = 0; i < node.inputs.size(); i++) {
                int pcY = wy + portY(i);
                if (mx >= wx && mx < wx + ps && my >= pcY - half && my < pcY + half) {
                    drawPortLabel(
                        node.inputs.get(i)
                            .left()
                            .getDisplayName(),
                        wx,
                        pcY,
                        false,
                        z);
                    return;
                }
            }

            for (int i = 0; i < node.fluidInputs.size(); i++) {
                int fi = node.inputs.size() + i;
                int pcY = wy + portY(fi);
                if (mx >= wx && mx < wx + ps && my >= pcY - half && my < pcY + half) {
                    drawPortLabel(
                        node.fluidInputs.get(i)
                            .left()
                            .getLocalizedName(),
                        wx,
                        pcY,
                        false,
                        z);
                    return;
                }
            }
        }
    }

    private static void drawPortLabel(String name, int anchorX, int centerY, boolean rightSide, float z) {
        if (name.length() > 20) name = name.substring(0, 19) + "\u2026";
        int tw = Minecraft.getMinecraft().fontRenderer.getStringWidth(name);
        int fh = Math.round(9 * z * 0.9f);
        int gap = Math.round(PORT_GAP * z);
        int labelX = rightSide ? anchorX + gap : anchorX - tw - gap;
        int labelY = centerY - fh / 2;
        int pad = Math.round(2 * z);
        GuiDraw.drawRect(labelX - pad, labelY - pad, tw + pad * 2, fh + pad * 2, PlannhColors.PORT_LABEL_BG.getColor());
        GuiDraw.drawText(name, labelX, labelY, z * 0.9f, PlannhColors.TEXT_WHITE.getColor(), false);
    }
}
