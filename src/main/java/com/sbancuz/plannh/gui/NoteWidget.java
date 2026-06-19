package com.sbancuz.plannh.gui;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Note;

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

        topRow.child(new HeaderTextWidget(this));
        topRow.child(new CloseButtonWidget(this, canvas));

        mainColumn.child(topRow);
        mainColumn.child(new NoteTextWidget(this));

        child(mainColumn);
    }

    @Override
    public void removeFromGraph() {
        canvas.getGraph().notes.remove(data.getId());
    }
}
