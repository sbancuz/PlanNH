package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Note;

import java.util.Map;
import java.util.UUID;

public class NoteWidget extends FlowchartWidget<NoteWidget, Note> {

    protected NoteWidget(CanvasWidget canvas, Note note) {
        super(canvas, note);

        coverChildren();

        Flow mainColumn = Flow.column()
            .coverChildren();
        Flow topRow = FlowchartFlow.row(this)
            .coverChildrenHeight()
            .fullWidth()
            .childPadding(4)
            .mainAxisAlignment(Alignment.MainAxis.SPACE_BETWEEN)
            .background(new Rectangle().color(PlannhColors.NOTE_BORDER.getColor()));

        HeaderTextWidget header = new HeaderTextWidget(this);
        topRow.child(header.background(
            new DynamicDrawable(
                () -> new Rectangle()
                    .color(header.isEditing() ? PlannhColors.NOTE_BORDER_EDIT.getColor() : Color.argb(1f, 1f, 1f, 0)))));
        topRow.child(new CloseButtonWidget(this));

        mainColumn.child(topRow);
        mainColumn.child(new NoteTextWidget(this));

        child(mainColumn);
    }

    @Override
    protected Map<UUID, Note> getDefaultContainer() {
        return canvas.getGraph().notes;
    }
}
