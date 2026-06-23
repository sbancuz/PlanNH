package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class GraphData {

    protected final UUID id;
    protected int x;
    protected int y;
    protected String header;
    protected final String type = getType();

    protected GraphData(UUID id) {
        this.id = id;
        header = StringUtils.capitalize(getType());
    }

    public abstract String getType();
}
