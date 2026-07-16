package com.sbancuz.plannh.data.flowchart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.utils.Color;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Group extends GraphData {

    public static final int GROUP_MIN_W = 300;
    public static final int GROUP_MIN_H = 200;

    private static Random colorRandom = new Random(12345);

    private int width = GROUP_MIN_W;
    private int height = GROUP_MIN_H;
    private int color = getRandomColor();
    private boolean collapsed;
    private boolean clampNodes;
    private boolean coverChildren;
    @NotNull
    private final Map<UUID, GraphData> children = new HashMap<>();
    @NotNull
    private final Set<UUID> nodeIds = new HashSet<>();

    public Group() {
        super(UUID.randomUUID());
    }

    @Override
    public String getType() {
        return "group";
    }

    private int getRandomColor() {
        return Color.argb(colorRandom.nextFloat(), colorRandom.nextFloat(), colorRandom.nextFloat(), 0.5f);
    }
}
