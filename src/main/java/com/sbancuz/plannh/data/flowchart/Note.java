package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Note extends GraphData {

    private List<String> text = new ArrayList<>();

    public Note() {
        super(UUID.randomUUID());
    }

    @Override
    public String getType() {
        return "note";
    }

    @Override
    public boolean invalid() {
        return super.invalid() || text == null;
    }
}
