package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MachineProfileRegistry {

    private static final Map<String, MachineProfile> profiles = new HashMap<>();

    public static void register(final MachineProfile profile) {
        profiles.put(profile.id(), profile);
    }

    @Nullable
    public static MachineProfile get(final String id) {
        return profiles.get(id);
    }

    @Nonnull
    public static String defaultId() {
        return "minecraft";
    }

    public static void reset() {
        profiles.clear();
    }
}
