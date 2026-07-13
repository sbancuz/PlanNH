package com.sbancuz.plannh.gui.node;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;

import java.awt.*;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.gtnewhorizons.modularui.api.math.Size;
import com.sbancuz.plannh.data.flowchart.Node2;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.IFlowchartDraggable;
import com.sbancuz.plannh.mixins.GTNEIDefaultHandlerAccessor;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;

public class RecipeAreaWidget extends ParentWidget<RecipeAreaWidget> implements IFlowchartDraggable {

    private final NodeWidget parent;
    private final Node2 data;
    private final NEIRecipeWidget neiWidget;
    private final RecipeHandlerRef handlerRef;

    private long lastHandlerUpdate = 0;

    public RecipeAreaWidget(NodeWidget parent) {
        this.parent = parent;
        this.data = parent.getData();

        handlerRef = RecipeHandlerRef.of(data.getRecipeId());
        neiWidget = new NEIRecipeWidget(handlerRef);
        neiWidget.showAsWidget(true);
        neiWidget.x = 2;

        // TODO make the offsets static and add docs
        if (handlerRef.handler instanceof GTNEIDefaultHandlerAccessor handler) {
            Size size = handler.getNeiProperties().recipeBackgroundSize;
            size(size.width, size.height);
        } else {
            size(neiWidget.w, neiWidget.h);
        }

        // input
        data.getInputs()
            .forEach(positionedStack -> child(new PortWidget(handlerRef, true, positionedStack)));

        // output
        data.getOutputs()
            .forEach(positionedStack -> child(new PortWidget(handlerRef, false, positionedStack)));
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
