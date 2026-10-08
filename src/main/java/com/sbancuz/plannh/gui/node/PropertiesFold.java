package com.sbancuz.plannh.gui.node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.sbancuz.plannh.data.flowchart.Node;
import com.sbancuz.plannh.data.properties.RecipeProperty;
import com.sbancuz.plannh.data.properties.SummaryProperty;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartTextWidget;

class PropertiesFold extends NodeFold {

    private static final String LANG = "plannh.gui.node.properties.";

    public PropertiesFold(final NodeWidget node) {
        super(
            node,
            () -> node.getData()
                .isPropertiesOpen());
    }

    @Override
    protected void rebuild() {
        removeAll();

        final Node data = node.getData();
        final Map<RecipeProperty<?>, Object> properties = data.getProperties();

        for (final SummaryProperty<?> prop : properties(data)) {
            final String name = name(prop);
            final float amount = ((Number) properties.get(prop)).floatValue();

            child(
                FlowchartFlow.row(node)
                    .fullWidth()
                    .marginBottom(ROW_GAP)
                    .coverChildrenHeight()
                    .childPadding(2)
                    .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
                    .child(
                        FlowchartFlow.row(node)
                            .widthRel(LABEL_RATIO)
                            .crossAxisAlignment(Alignment.CrossAxis.START)
                            .coverChildrenHeight()
                            .childPadding(2)
                            .child(new FlowchartTextWidget(IKey.str(name), node)))
                    .child(
                        new FlowchartTextWidget(
                            IKey.str(prop.formatAmount(amount))
                                .color(
                                    // A property named per tick is energy, and the pack marks energy in its
                                    // own colour wherever it turns up - the tooltip's rule, kept.
                                    name.endsWith("/t") ? PlannhColors.ACCENT_YELLOW.getColor()
                                        : PlannhColors.ACCENT_BLUE.getColor()),
                            node).widthRel(1 - LABEL_RATIO)
                                .textAlign(Alignment.CenterRight)));
        }

        if (getChildren().isEmpty()) child(hint(LANG + "none_hint"));
        scheduleResize();
    }

    /**
     * Every property this recipe puts a number to, sorted by the name it is shown under so the rows
     * do not jump around between one opening of the node and the next.
     */
    private static List<SummaryProperty<?>> properties(final Node node) {
        final List<SummaryProperty<?>> found = new ArrayList<>();
        for (final Map.Entry<RecipeProperty<?>, Object> entry : node.getProperties()
            .entrySet()) {
            if (!(entry.getKey() instanceof final SummaryProperty<?> prop)) continue;
            if (!(entry.getValue() instanceof final Number number) || number.floatValue() == 0) continue;
            found.add(prop);
        }
        found.sort(Comparator.comparing(PropertiesFold::name));
        return found;
    }

    /** What a property calls itself. */
    private static <T> String name(final SummaryProperty<T> prop) {
        return prop.formatDisplayName(prop.getDefaultValue());
    }
}
