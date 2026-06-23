package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Note extends GraphData {

    @NotNull
    private List<String> text = new ArrayList<>();

    public Note() {
        super(UUID.randomUUID());
    }

    @Override
    public String getType() {
        return "note";
    }
}
