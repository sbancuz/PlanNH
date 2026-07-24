package com.sbancuz.plannh.gui.node;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import com.sbancuz.plannh.mixins.NEIRecipeWidgetAccessor;

import codechicken.nei.PositionedStack;
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
        int yShift = accessor.getHandlerInfo()
            .getYShift();

        // inputs
        List<PositionedStack> inputs = new ArrayList<>(accessor.callGetInputs());
        inputs.addAll(accessor.callGetCatalysts());
        inputs.removeIf(ps -> ps.item.stackSize <= 0);
        if (data.getInputs() != null) for (Port<?> port : data.getInputs()) port.getIndices()
            .forEach(i -> child(new PortWidget(inputs.get(i), true, port, yShift)));

        // outputs
        List<PositionedStack> outputs = new ArrayList<>(accessor.callGetOutputs());
        outputs.removeIf(ps -> ps.item.stackSize <= 0);
        if (data.getOutputs() != null) for (Port<?> port : data.getOutputs()) port.getIndices()
            .forEach(i -> child(new PortWidget(outputs.get(i), false, port, yShift)));
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
            widgets.forEach(neiWidget -> ((NEIRecipeWidgetAccessor) neiWidget).setUpdate(true));
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
