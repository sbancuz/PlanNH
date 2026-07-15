package com.sbancuz.plannh.gui.node;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

import net.minecraft.client.Minecraft;

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

    private final NodeWidget parent;
    private final Node data;
    private final NEIRecipeWidget neiWidget;
    private final RecipeHandlerRef handlerRef;

    private long lastHandlerUpdate = 0;

    public RecipeAreaWidget(NodeWidget parent) {
        this.parent = parent;
        this.data = parent.getData();

        handlerRef = RecipeHandlerRef.of(data.getRecipeId());
        neiWidget = new NEIRecipeWidget(handlerRef);
        neiWidget.showAsWidget(true);
        neiWidget.x = WIDGET_OFFSET_X;

        if (Compat.GREGTECH.isLoaded && handlerRef.handler instanceof GTNEIDefaultHandlerAccessor handler) {
            Size size = handler.getNeiProperties().recipeBackgroundSize;
            size(size.width, size.height);
        } else {
            size(neiWidget.w, neiWidget.h);
        }

        // inputs
        for (Port<?> port : data.getInputs()) port.getPositions()
            .forEach(pos -> child(new PortWidget(handlerRef, true, pos, port)));

        // outputs
        for (Port<?> port : data.getOutputs()) port.getPositions()
            .forEach(pos -> child(new PortWidget(handlerRef, false, pos, port)));
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        final long now = Minecraft.getSystemTime();
        if (now - lastHandlerUpdate > 50) {
            lastHandlerUpdate = now;
            handlerRef.handler.onUpdate();
        }

        glEnable(GL_TEXTURE_2D);
        neiWidget.draw(0, 0);

        glDisable(GL_TEXTURE_2D);
    }
}
