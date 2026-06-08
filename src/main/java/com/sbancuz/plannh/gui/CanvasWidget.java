package com.sbancuz.plannh.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Platform;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.data.flowchart.Edge;
import com.sbancuz.plannh.data.flowchart.Graph;
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
        rebuildNodeWidgets();
    }

    public void setGraph(Graph newGraph) {
        this.graph = newGraph;
        rebuildNodeWidgets();
        rebuildNoteWidgets();
    }

    public void rebuildNodeWidgets() {
        removeAll();
        editingNoteId = null;
        nodeWidgets.clear();
        for (Node node : graph.getNodes()) {
            RecipeNodeWidget widget = new RecipeNodeWidget(node, this);
            widget.syncTransform(zoom, panX, panY);
            nodeWidgets.put(node.id, widget);
            child(widget);
        }
        rebuildNoteWidgets();
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
    }

    private void updateNotePositions() {
        for (NoteWidget nw : noteWidgets.values()) {
            nw.syncTransform(zoom, panX, panY);
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int aw = getArea().width;
        int ah = getArea().height;
        if (aw <= 0 || ah <= 0) return;

        drawArrows();

        super.draw(context, widgetTheme);

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
        if (mouseButton == 0) {
            int absMx = getContext().getAbsMouseX();
            int absMy = getContext().getAbsMouseY();

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

            if (!isMouseOverAnyNode(absMx, absMy)) {
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

    public void startEditingNote(UUID noteId) {
        editingNoteId = noteId;
        for (NoteWidget nw : noteWidgets.values()) {
            nw.setEditing(nw.getNote().id.equals(noteId));
        }
    }

    public void onNoteEditingDone() {
        editingNoteId = null;
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
        return Result.IGNORE;
    }

    public Note getNoteForEdit() {
        if (editingNoteId == null) return null;
        return graph.notes.get(editingNoteId);
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
