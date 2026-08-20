package com.sbancuz.plannh.gui.node;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.modularui.api.math.Size;
import com.sbancuz.plannh.Compat;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.IFlowchartDraggable;
import com.sbancuz.plannh.mixins.GTNEIDefaultHandlerAccessor;

import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;

public class RecipeAreaWidget extends ParentWidget<RecipeAreaWidget> implements IFlowchartDraggable {

    // needed for proper positioning of the recipe
    private static final int WIDGET_OFFSET_X = 2;
    private static final Map<RecipeHandlerRef, Integer> handlers = new HashMap<>();
    private static final Set<NEIRecipeWidget> widgets = new HashSet<>();

    private final NodeWidget parent;
    private final Node data;
    private final NEIRecipeWidget neiWidget;
    private final RecipeHandlerRef handlerRef;

    private static long lastHandlerUpdate = 0;
    private boolean success = true;

    public RecipeAreaWidget(NodeWidget parent) {
        this.parent = parent;
        this.data = parent.getData();

        handlerRef = RecipeHandlerRef.of(data.getRecipeId());
        if (handlerRef == null) {
            neiWidget = null;
            success = false;
            child(
                IKey.str("AN ERROR OCCURED DURING LOADING")
                    .asWidget());
            return;
        }
        neiWidget = new NEIRecipeWidget(handlerRef);
        neiWidget.showAsWidget(true);
        neiWidget.x = WIDGET_OFFSET_X;

        if (Compat.GREGTECH.isLoaded && handlerRef.handler instanceof GTNEIDefaultHandlerAccessor handler) {
            Size size = handler.getNeiProperties().recipeBackgroundSize;
            size(size.width, size.height);
        } else {
            size(neiWidget.w, neiWidget.h);
        }

        NEIRecipeWidgetAccessor accessor = (NEIRecipeWidgetAccessor) neiWidget;
        int yShift = accessor.plannh$getHandlerInfo()
            .getYShift();

        // inputs
        if (data.getInputs() != null) {
            accessor.plannh$setInputs(
                data.getInputs()
                    .stream()
                    .map(Port::getStack)
                    .toList());

            data.getInputs()
                .forEach(
                    port -> port.getPositions()
                        .forEach(
                            pos -> child(
                                new PortWidget(
                                    parent.getCanvas(),
                                    port,
                                    port.getAmount() > 0 ? PortWidget.PortType.INPUT : PortWidget.PortType.CATALYST,
                                    yShift,
                                    pos))));
        }

        // outputs
        if (data.getOutputs() != null) data.getOutputs()
            .forEach(
                port -> port.getPositions()
                    .forEach(
                        pos -> child(
                            new PortWidget(
                                parent.getCanvas(),
                                port,
                                port.getAmount() > 0 ? PortWidget.PortType.OUTPUT : PortWidget.PortType.CATALYST,
                                yShift,
                                pos))));
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!success) return;

        final long now = Minecraft.getSystemTime();
        if (now - lastHandlerUpdate > 50) {
            lastHandlerUpdate = now;

            handlers.keySet()
                .forEach(handlerRef -> handlerRef.handler.onUpdate());
            widgets.forEach(neiWidget -> ((NEIRecipeWidgetAccessor) neiWidget).plannh$setUpdate(true));
        }

        glEnable(GL_TEXTURE_2D);
        neiWidget.draw(0, 0);

        glDisable(GL_TEXTURE_2D);
    }

    @Override
    public void onInit() {
        if (success) {
            handlers.computeIfPresent(handlerRef, (_, i) -> i + 1);
            handlers.putIfAbsent(handlerRef, 1);
            widgets.add(neiWidget);
        }
    }

    @Override
    public void dispose() {
        if (success) {
            handlers.computeIfPresent(handlerRef, (_, i) -> i - 1);
            if (handlers.get(handlerRef) == 0) handlers.remove(handlerRef);
            widgets.remove(neiWidget);
        }
        super.dispose();
    }
}
