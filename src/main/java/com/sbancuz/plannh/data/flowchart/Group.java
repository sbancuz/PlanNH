package com.sbancuz.plannh.data.flowchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Group {

    public final UUID id;
    public String title;
    public int x, y;
    public int width, height;
    public boolean collapsed;
    public int colorOverride;
    public boolean clampNodes;
    public boolean autoResize;
    public final List<UUID> nodeIds = new ArrayList<>();

    public Group(UUID id, int x, int y, int width, int height, String title) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.title = title;
    }

    public Group(int x, int y, int width, int height, String title) {
        this(UUID.randomUUID(), x, y, width, height, title);
    }

    public Group(int x, int y, String title) {
        this(x, y, 300, 200, title);
    }
}
