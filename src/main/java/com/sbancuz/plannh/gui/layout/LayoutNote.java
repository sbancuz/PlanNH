package com.sbancuz.plannh.gui.layout;

import java.util.UUID;

public record LayoutNote(UUID id, int width, int height, Side side, UUID anchorId) {

    /**
     * Which face of a machine a note sits on.
     */
    public enum Side {

        ABOVE,
        BELOW,
        LEFT,
        RIGHT;

        /** Whether reserving a note on this side grows the anchor's height rather than its width. */
        public boolean vertical() {
            return this == ABOVE || this == BELOW;
        }
    }
}
