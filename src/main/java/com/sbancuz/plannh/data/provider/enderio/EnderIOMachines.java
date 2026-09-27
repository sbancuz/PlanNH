package com.sbancuz.plannh.data.provider.enderio;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.StatCollector;

import com.sbancuz.plannh.data.RecipeContext;
import com.sbancuz.plannh.data.Settings;
import com.sbancuz.plannh.data.machine.MachineVariant;
import com.sbancuz.plannh.data.machine.MachineVariants;
import com.sbancuz.plannh.data.properties.RecipeProperty;

/**
 * EnderIO's machines, as the shared picker sees them.
 *
 * <p>
 * Every EnderIO recipe belongs to exactly one machine - the recipe registry is keyed by machine name -
 * so no node ever has a choice to make here, and the picker offers one option. It is still worth
 * registering: the node then names the machine the recipe actually runs in rather than saying
 * "EnderIO", and the capacitor row appears only on the machines a capacitor drives.
 */
public enum EnderIOMachines implements MachineVariant {

    ALLOY_SMELTER("enderio:alloy_smelter", "tile.blockAlloySmelter.name", true),
    SAG_MILL("enderio:sag_mill", "tile.blockSagMill.name", true),
    VAT("enderio:vat", "tile.blockVat.name", true),
    SLICE_AND_SPLICE("enderio:slice_and_splice", "tile.blockSliceAndSplice.name", true),
    SOUL_BINDER("enderio:soul_binder", "tile.blockSoulBinder.name", true),
    /** Runs on experience levels rather than a capacitor, so it has no tier to choose. */
    ENCHANTER("enderio:enchanter", "tile.blockEnchanter.name", false);

    private final String id;
    private final String nameKey;
    private final Set<Settings> settings;

    /**
     * @param id      what a chart persists. PlanNH's own, because EnderIO keys its recipe registry by
     *                machine name rather than by block, and a chart has to survive a block being
     *                renamed
     * @param nameKey EnderIO's own unlocalized block name, so the picker reads the machine the way
     *                the player's game does in whichever of the sixteen languages EnderIO ships
     */
    EnderIOMachines(final String id, final String nameKey, final boolean capacitorDriven) {
        this.id = id;
        this.nameKey = nameKey;
        this.settings = capacitorDriven ? Set.of(Settings.EIO_CAPACITOR) : Set.of();
    }

    @Override
    @Nonnull
    public String id() {
        return id;
    }

    @Override
    @Nonnull
    public String displayName() {
        return StatCollector.translateToLocal(nameKey);
    }

    @Override
    @Nonnull
    public Set<Settings> settings() {
        return settings;
    }

    /**
     * Which EnderIO machine this recipe runs in. The recipe's own cached type is what says so, and
     * only the provider reading the recipe can see that, so it publishes the answer here.
     */
    public static final RecipeProperty<EnderIOMachines> MACHINE = RecipeProperty
        .<EnderIOMachines>builder("enderio.machine", null)
        .build();

    @Nullable
    public static EnderIOMachines byId(final String id) {
        for (final EnderIOMachines machine : values()) {
            if (machine.id.equals(id)) return machine;
        }
        return null;
    }

    /**
     * Which machine a node's recipe came from, published by the provider while it reads the recipe -
     * the recipe's own cached type is what says so, and only the provider can see that.
     */
    public static final MachineVariants.Source SOURCE = new MachineVariants.Source() {

        @Override
        @Nonnull
        public List<? extends MachineVariant> candidates(final RecipeContext ctx) {
            final EnderIOMachines machine = ctx.getOrDefault(MACHINE, null);
            return machine == null ? List.of() : List.of(machine);
        }

        @Override
        @Nullable
        public MachineVariant byId(final String id) {
            return EnderIOMachines.byId(id);
        }
    };
}
