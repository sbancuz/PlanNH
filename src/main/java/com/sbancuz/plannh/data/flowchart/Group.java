package com.sbancuz.plannh.data.flowchart;

import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

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
    private boolean coverChildren;
    /**
     * Sorted for the same reason the graph's own maps are, and for one more: Gson builds a
     * SortedMap field as a TreeMap but a Map keyed on anything but String as an insertion-ordered
     * LinkedTreeMap, so the declared type here is what makes a reloaded group iterate like a
     * built one.
     */
    private final SortedMap<UUID, GraphData> children = new TreeMap<>();

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

    @Override
    public boolean invalid() {
        return super.invalid() || children == null;
    }
}
