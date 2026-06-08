package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

public class Note {

    public final UUID id;
    public int x;
    public int y;
    public String text = "";

    public Note(UUID id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }
}
