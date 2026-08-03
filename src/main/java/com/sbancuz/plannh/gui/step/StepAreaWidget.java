package com.sbancuz.plannh.gui.step;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.sbancuz.plannh.data.flowchart.Port;
import com.sbancuz.plannh.data.flowchart.Step;
import com.sbancuz.plannh.gui.FlowchartWidget;
import com.sbancuz.plannh.gui.IFlowchartDraggable;

public class StepAreaWidget extends ParentWidget<StepAreaWidget>
    implements IFlowchartDraggable, RecipeViewerGhostIngredientSlot<ItemStack> {

    private static final int SLOT_SIZE = 18;

    private final StepWidget parent;
    private final Step data;

    public StepAreaWidget(StepWidget parent) {
        this.parent = parent;
        this.data = parent.getData();
        size(60, 60);
    }

    @Override
    public boolean handleDragAndDrop(final ItemStack draggedStack, final int button) {
        data.setFilter(draggedStack);
        return true;
    }

    @Override
    public void draw(final ModularGuiContext context, final WidgetThemeEntry<?> widgetTheme) {
        super.draw(context, widgetTheme);
        final var area = getArea();
        final var x = (area.w() - SLOT_SIZE) / 2;
        final var y = (area.h() - SLOT_SIZE) / 2;
        final var z = context.getCurrentDrawingZ();
        GuiTextures.SLOT_ITEM.draw(context, x, y, SLOT_SIZE, SLOT_SIZE, widgetTheme.getTheme());
        final Port<?> active = getActivePort();
        if (active != null && active.getValue() instanceof final ItemStack stack) {
            GuiDraw.drawItem(stack, x, y, SLOT_SIZE, SLOT_SIZE, z);
        }
    }

    @Nullable
    private Port<?> getActivePort() {
        final var outputs = data.getOutputs();
        if (!outputs.isEmpty()) return outputs.getFirst();
        final var inputs = data.getInputs();
        if (!inputs.isEmpty()) return inputs.getFirst();
        return null;
    }

    @Override
    public FlowchartWidget<?, ?> getFlowchartParent() {
        return parent;
    }
}
