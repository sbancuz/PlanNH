package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Note extends GraphData {

    public static final String TYPE = "note";

    private List<String> text = new ArrayList<>();

    /**
     * The machine whose box reserves this note's space, or null if it has never been laid out.
     *
     * <p>
     * <b>Persisted on purpose, because recomputing it is not idempotent.</b> A note is placed rigidly
     * relative to its anchor, so after one layout the machine nearest the note's <em>new</em> position is
     * usually the same one — but in a dense column a short machine above can be nearer than a tall
     * anchor below. Recomputing from a position the previous run just produced makes the chart drift a
     * little further every time the button is pressed, which is the exact failure a deterministic layout
     * exists to avoid. One nullable id makes it structural instead of statistical.
     *
     * <p>
     * Additive in the save format: Gson ignores it when reading an old chart, and re-saved charts emit it.
     */
    private UUID anchorId;

    public Note() {
        super(UUID.randomUUID());
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean invalid() {
        return super.invalid() || text == null;
    }
}
