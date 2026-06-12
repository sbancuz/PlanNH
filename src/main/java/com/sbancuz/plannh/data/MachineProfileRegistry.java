package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

public final class MachineProfileRegistry {

    private static final Map<String, MachineProfile> profiles = new HashMap<>();

    public static void register(final MachineProfile profile) {
        profiles.put(profile.id(), profile);
    }

    public static MachineProfile get(final String id) {
        return profiles.get(id);
    }

    public static String defaultId() {
        return "minecraft";
    }

}
