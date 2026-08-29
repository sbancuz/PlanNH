package com.sbancuz.plannh.gui.node;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

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

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import lombok.Getter;

public class RecipeAreaWidget extends ParentWidget<RecipeAreaWidget> implements IFlowchartDraggable {

    // needed for proper positioning of the recipe
    private static final int WIDGET_OFFSET_X = 2;
    private static final Map<RecipeHandlerRef, Integer> handlers = new HashMap<>();
    private static final Set<NEIRecipeWidget> widgets = new HashSet<>();

    private final NodeWidget parent;
    private final NEIRecipeWidget neiWidget;
    private final RecipeHandlerRef handlerRef;
    // second index is used to differentiate duplicate item ports
    @Getter
    private final Map<IntIntPair, PortWidget> inputPorts = new HashMap<>();
    @Getter
    private final Map<IntIntPair, PortWidget> outputPorts = new HashMap<>();

    private static long lastHandlerUpdate = 0;
    private boolean success = true;

    public RecipeAreaWidget(NodeWidget parent) {
        this.parent = parent;
        Node data = parent.getData();

        handlerRef = RecipeHandlerRef.of(data.getRecipeId());
        if (handlerRef == null) {
            neiWidget = null;
            success = false;
            child(
                IKey.str("AN ERROR OCCURRED DURING LOADING")
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

        List<Port<?>> inputs = data.getInputs();
        List<Port<?>> outputs = data.getOutputs();
        // todo needed?
        if (inputs == null || outputs == null) throw new RuntimeException("inputs / outputs were incorrectly loaded");

        accessor.plannh$setInputs(
            inputs.stream()
                .map(Port::getAllStacks)
                .flatMap(List::stream)
                .toList());

        // inputs
        for (int i = 0; i < inputs.size(); i++) {
            Port<?> port = inputs.get(i);
            List<PositionedStack> stacks = port.getAllStacks();
            for (int j = 0; j < stacks.size(); j++) {
                IntIntPair index = IntIntPair.of(i, j);

                PortWidget portWidget = new PortWidget(
                    parent.getCanvas(),
                    port,
                    port.getAmount() > 0 ? PortWidget.PortType.INPUT : PortWidget.PortType.CATALYST,
                    yShift,
                    index,
                    data,
                    this);
                inputPorts.put(index, portWidget);
                child(portWidget);
            }
        }

        // outputs
        for (int i = 0; i < outputs.size(); i++) {
            Port<?> port = outputs.get(i);
            List<PositionedStack> stacks = port.getAllStacks();
            for (int j = 0; j < stacks.size(); j++) {
                IntIntPair index = IntIntPair.of(i, j);

                PortWidget portWidget = new PortWidget(
                    parent.getCanvas(),
                    port,
                    port.getAmount() > 0 ? PortWidget.PortType.OUTPUT : PortWidget.PortType.CATALYST,
                    yShift,
                    index,
                    data,
                    this);
                outputPorts.put(index, portWidget);
                child(portWidget);
            }
        }
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
