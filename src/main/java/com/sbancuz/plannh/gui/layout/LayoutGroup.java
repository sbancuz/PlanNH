package com.sbancuz.plannh.gui.layout;

import java.util.List;
import java.util.UUID;

public record LayoutGroup(UUID id, List<UUID> memberIds, int pad, int header) {

    public LayoutGroup {
        memberIds = List.copyOf(memberIds);
    }
}
