package com.sbancuz.plannh.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;

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
import com.sbancuz.plannh.data.FlowchartEdge;
import com.sbancuz.plannh.data.FlowchartGraph;
import com.sbancuz.plannh.data.FlowchartNode;

import lombok.Getter;

public class CanvasWidget extends ParentWidget<CanvasWidget> implements Interactable {

    private static final int ARROW_COLOR = Color.argb(255, 200, 100, 50);
    private static final int PREVIEW_COLOR = Color.argb(180, 255, 200, 80);

    private final FlowchartGraph graph;
    private final Map<UUID, RecipeNodeWidget> nodeWidgets = new HashMap<>();

    public FlowchartGraph getGraph() {
        return graph;
    }

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

    public CanvasWidget(FlowchartGraph graph) {
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

    public void rebuildNodeWidgets() {
        removeAll();
        nodeWidgets.clear();
        for (FlowchartNode node : graph.getNodes()) {
            RecipeNodeWidget widget = new RecipeNodeWidget(node, this);
            widget.syncTransform(zoom, panX, panY);
            nodeWidgets.put(node.id, widget);
            child(widget);
        }
    }

    private void updateNodePositions() {
        for (Map.Entry<UUID, RecipeNodeWidget> entry : nodeWidgets.entrySet()) {
            FlowchartNode node = graph.nodes.get(entry.getKey());
            if (node == null) continue;
            RecipeNodeWidget widget = entry.getValue();
            widget.syncTransform(zoom, panX, panY);
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
        for (FlowchartEdge edge : graph.getEdges()) {
            RecipeNodeWidget srcWidget = nodeWidgets.get(edge.sourceNodeId);
            RecipeNodeWidget dstWidget = nodeWidgets.get(edge.targetNodeId);
            if (srcWidget == null || dstWidget == null) continue;

            int srcX = widgetX(srcWidget) + srcWidget.getArea().width;
            int srcY = widgetY(srcWidget) + portY(edge.sourceOutputIndex);
            int dstX = widgetX(dstWidget);
            int dstY = widgetY(dstWidget) + portY(edge.targetInputIndex);

            drawArrow(srcX, srcY, dstX, dstY);
        }
    }

    private void drawArrow(int x1, int y1, int x2, int y2) {
        int midX = (x1 + x2) / 2;
        int arrowSize = Math.round(6 * zoom);
        int thickness = Math.max(1, Math.round(2 * zoom));

        int r = Color.getRed(ARROW_COLOR);
        int g = Color.getGreen(ARROW_COLOR);
        int b = Color.getBlue(ARROW_COLOR);
        int a = Color.getAlpha(ARROW_COLOR);

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
            buf.pos(x2, y2, 0)
                .color(r, g, b, a)
                .endVertex();
        });
        GL11.glLineWidth(1);

        GuiDraw.drawRect(x2 - arrowSize, y2 - thickness - 1, arrowSize, thickness, ARROW_COLOR);
        GuiDraw.drawRect(x2 - arrowSize, y2 + 2, arrowSize, thickness, ARROW_COLOR);
        GuiDraw.drawRect(x2 - arrowSize, y2 - 1, arrowSize + 1, thickness + 2, ARROW_COLOR);
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

    private FlowchartEdge getEdgeAt(int absMx, int absMy) {
        int margin = Math.max(4, Math.round(4 * zoom));
        int cmx = absMx - getArea().x;
        int cmy = absMy - getArea().y;
        for (FlowchartEdge edge : graph.getEdges()) {
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

    private boolean canConnect(FlowchartNode srcNode, int srcOutIdx, FlowchartNode dstNode, int dstInIdx) {
        if (srcNode == dstNode) return false;
        if (srcOutIdx < 0 || srcOutIdx >= srcNode.outputs.size()) return false;
        if (dstInIdx < 0 || dstInIdx >= dstNode.inputs.size()) return false;
        ItemStack out = srcNode.outputs.get(srcOutIdx);
        ItemStack in = dstNode.inputs.get(dstInIdx);
        if (out == null || in == null) return false;
        return out.isItemEqual(in);
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
                FlowchartEdge clicked = getEdgeAt(absMx, absMy);
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
                FlowchartNode srcNode = graph.nodes.get(edgeSourceNodeId);
                FlowchartNode dstNode = graph.nodes.get(edgeHoverNodeId);
                if (srcNode != null && dstNode != null) {
                    graph.addEdge(
                        new FlowchartEdge(
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
}
