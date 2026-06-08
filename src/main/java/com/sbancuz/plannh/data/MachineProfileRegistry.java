package com.sbancuz.plannh.data;

import java.util.HashMap;
import java.util.List;
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
        return "vanilla";
    }

    public static MachineProfile vanillaProfile() {
        return new MachineProfile(
            "vanilla",
            "Vanilla",
            List.of(Settings.PARALLELS.def(), Settings.MACHINES.def()),
            MachineProfileRegistry::vanillaEffect);
    }

    private static MachineProfile.EffectResult vanillaEffect(Map<String, Object> s, MachineProfile.RecipeContext ctx) {
        int machines = MachineProfile.getInt(s, Settings.MACHINES.key(), 1);
        return new MachineProfile.EffectResult(ctx.recipeDuration(), ctx.recipeEUt(), machines);
    }
}
