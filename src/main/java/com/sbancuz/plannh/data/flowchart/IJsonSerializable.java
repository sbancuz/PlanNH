package com.sbancuz.plannh.data.flowchart;

import com.google.gson.JsonObject;

public interface IJsonSerializable {

    void loadFromJson(JsonObject json);

    void saveToJson(JsonObject json);
}
