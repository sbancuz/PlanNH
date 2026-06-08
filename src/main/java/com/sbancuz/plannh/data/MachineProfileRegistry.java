package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

public class MachineProfileRegistry {

    private static final Map<String, MachineProfile> profiles = new HashMap<>();

    public static void register(MachineProfile profile) {
        profiles.put(profile.id(), profile);
    }

    public static MachineProfile get(String id) {
        return profiles.get(id);
    }

    public static String defaultId() {
        return "minecraft";
    }

}
