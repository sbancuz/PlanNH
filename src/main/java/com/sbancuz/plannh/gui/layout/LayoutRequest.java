package com.sbancuz.plannh.gui.layout;

import java.util.List;

public record LayoutRequest(List<LayoutMachine> machines, List<LayoutRelation> relations, List<LayoutGroup> groups,
    List<LayoutNote> notes) {}
