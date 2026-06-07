package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.sbancuz.plannh.PlanNH;
import com.sbancuz.plannh.api.PlanAPI;
import com.sbancuz.plannh.data.FlowchartGraph;
import com.sbancuz.plannh.data.FlowchartSummary;

public class FlowchartScreen extends ModularScreen {

    public final FlowchartGraph graph;
    public final CanvasWidget canvas;

    private FlowchartScreen(ModularPanel panel, FlowchartGraph graph, CanvasWidget canvas) {
        super(PlanNH.MODID, panel);
        getContext().setSettings(new UISettings());
        getContext().getUISettings()
            .getRecipeViewerSettings()
            .enable();
        this.graph = graph;
        this.canvas = canvas;
    }

    public static FlowchartScreen create() {
        FlowchartGraph graph = PlanAPI.reloadFromFile();
        CanvasWidget canvas = new CanvasWidget(graph);

        ModularPanel panel = ModularPanel.defaultPanel("flowchart_main");
        panel.fullScreenInvisible();

        panel.child(
            canvas.left(0)
                .top(20)
                .right(176)
                .bottom(20));

        panel.addChild(new SummaryWidget(graph, canvas), -1);

        return new FlowchartScreen(panel, graph, canvas);
    }

    @Override
    public void onResize(int width, int height) {
        super.onResize(width, height);
        if (getScreenWrapper() != null
            && getScreenWrapper().getGuiScreen() instanceof FlowchartGuiContainer container) {
            container.applyNeiSizing(width);
        }
    }

    @Override
    public void onClose() {
        PlanAPI.saveGraph(graph);
        super.onClose();
    }

    private static class SummaryWidget extends Widget<SummaryWidget> implements Interactable {

        private static final int WIDTH = 200;
        private static final int TITLE_H = 16;
        private static final int COLLAPSE_W = 20;

        private final FlowchartGraph graph;
        private final CanvasWidget canvas;

        private int floatX = 210;
        private int floatY = 24;
        private boolean collapsed = false;

        private boolean dragging = false;
        private int dragAbsMX, dragAbsMY;
        private int dragStartX, dragStartY;

        SummaryWidget(FlowchartGraph graph, CanvasWidget canvas) {
            this.graph = graph;
            this.canvas = canvas;
            pos(floatX, floatY);
            size(WIDTH, computeHeight());
        }

        private int computeHeight() {
            if (collapsed) return TITLE_H;
            FlowchartSummary s = graph.calculateSummary();
            int h = TITLE_H + 4;
            if (!s.netInputs().isEmpty()) {
                h += 14 + s.netInputs().size() * 11 + 4;
            }
            if (!s.netOutputs().isEmpty()) {
                h += 14 + s.netOutputs().size() * 11 + 4;
            }
            if (s.totalEu() > 0) h += 14;
            h += 4 + 1 + 10;
            h += 14 + 10 + 10 + 10 + 10 + 10;
            return h;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            Area a = getArea();
            int w = a.width;
            int h = a.height;

            GuiDraw.drawRect(0, 0, w, h, Color.argb(40, 40, 40, 220));
            GuiDraw.drawRect(0, 0, w, TITLE_H, Color.argb(60, 60, 60, 240));
            GuiDraw.drawText("Summary", 4, 3, 1.0f, 0xFFFFFF, false);
            GuiDraw.drawText(collapsed ? "[+]" : "\u2212", w - COLLAPSE_W, 3, 1.0f, 0xAAAAAA, false);

            if (collapsed) return;

            FlowchartSummary summary = graph.calculateSummary();
            int ly = TITLE_H + 4;

            if (!summary.netInputs().isEmpty()) {
                GuiDraw.drawText("Inputs (" + summary.netInputs().size() + ")", 6, ly, 1.0f, 0x77AA77, false);
                ly += 14;
                for (FlowchartSummary.SummaryLine line : summary.netInputs()) {
                    GuiDraw.drawText(line.totalCount + "x " + line.stack.getDisplayName(), 10, ly, 0.8f, 0xAAAAAA, false);
                    ly += 11;
                }
                ly += 4;
            }

            if (!summary.netOutputs().isEmpty()) {
                GuiDraw.drawText("Outputs (" + summary.netOutputs().size() + ")", 6, ly, 1.0f, 0xAAAA77, false);
                ly += 14;
                for (FlowchartSummary.SummaryLine line : summary.netOutputs()) {
                    GuiDraw.drawText(line.totalCount + "x " + line.stack.getDisplayName(), 10, ly, 0.8f, 0xFFCC66, false);
                    ly += 11;
                }
                ly += 4;
            }

            if (summary.totalEu() > 0) {
                GuiDraw.drawText("EU: " + summary.totalEu(), 6, ly, 1.0f, 0x88AAFF, false);
                ly += 14;
            }

            GuiDraw.drawRect(0, ly + 4, w, 1, Color.argb(80, 200, 200, 200));
            ly += 10;
            GuiDraw.drawText("Zoom: " + canvas.getZoomPercent() + "%", 6, ly, 0.9f, 0xAAAAAA, false);
            ly += 14;
            GuiDraw.drawText("[Scroll] zoom", 6, ly, 0.8f, 0x666666, false);
            ly += 10;
            GuiDraw.drawText("[MMB] pan", 6, ly, 0.8f, 0x666666, false);
            ly += 10;
            GuiDraw.drawText("[LMB drag] move node", 6, ly, 0.8f, 0x666666, false);
            ly += 10;
            GuiDraw.drawText("[Double-click] open NEI", 6, ly, 0.8f, 0x666666, false);
            ly += 10;
            GuiDraw.drawText("[+ in NEI GUI] add recipe", 6, ly, 0.8f, 0x666666, false);
        }

        @Override
        public Result onMousePressed(int mouseButton) {
            if (mouseButton != 0) return Result.IGNORE;
            int mx = getContext().getMouseX();
            int my = getContext().getMouseY();

            if (my < TITLE_H && mx >= WIDTH - COLLAPSE_W) {
                collapsed = !collapsed;
                size(WIDTH, computeHeight());
                return Result.SUCCESS;
            }

            if (my < TITLE_H) {
                dragging = true;
                dragAbsMX = getContext().getAbsMouseX();
                dragAbsMY = getContext().getAbsMouseY();
                dragStartX = floatX;
                dragStartY = floatY;
                return Result.SUCCESS;
            }

            return Result.IGNORE;
        }

        @Override
        public boolean onMouseRelease(int mouseButton) {
            dragging = false;
            return true;
        }

        @Override
        public void onMouseDrag(int mouseButton, long timeSinceClick) {
            if (!dragging) return;
            floatX = dragStartX + (getContext().getAbsMouseX() - dragAbsMX);
            floatY = dragStartY + (getContext().getAbsMouseY() - dragAbsMY);
            pos(floatX, floatY);
        }

        @Override
        public boolean onMouseScroll(UpOrDown direction, int amount) {
            return false;
        }
    }
}
