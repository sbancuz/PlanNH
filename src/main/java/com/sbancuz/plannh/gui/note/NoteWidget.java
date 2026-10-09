package com.sbancuz.plannh.gui.note;

import java.util.SortedMap;
import java.util.UUID;

import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.sbancuz.plannh.data.flowchart.Note;
import com.sbancuz.plannh.gui.CanvasWidget;
import com.sbancuz.plannh.gui.PlannhColors;
import com.sbancuz.plannh.gui.common.CloseButtonWidget;
import com.sbancuz.plannh.gui.common.FlowchartFlow;
import com.sbancuz.plannh.gui.common.FlowchartWidget;
import com.sbancuz.plannh.gui.common.HeaderTextWidget;

public class NoteWidget extends FlowchartWidget<NoteWidget, Note> {

    public NoteWidget(CanvasWidget canvas, Note note) {
        super(canvas, note);

        name("note");
        coverChildren();

        Flow mainColumn = Flow.column()
            .name("note.column")
            .coverChildren();
        Flow topRow = FlowchartFlow.row(this)
            .name("note.header")
            .coverChildrenHeight()
            .fullWidth()
            .childPadding(4)
            .background(new Rectangle().color(PlannhColors.NOTE_BORDER.getColor()));

        topRow.child(new HeaderTextWidget(this, PlannhColors.NOTE_BORDER_EDIT.getColor()));
        topRow.child(new CloseButtonWidget(this));

        mainColumn.child(topRow);
        mainColumn.child(new NoteTextWidget(this));

        child(mainColumn);
    }

    @Override
    protected SortedMap<UUID, Note> getDefaultContainer() {
        return canvas.getGraph()
            .getNotes();
    }

    @Override
    public boolean isObstacle() {
        return true;
    }
}
