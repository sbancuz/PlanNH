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
 * EnderIO's machines, registered with the shared picker.
 *
 * <p>
 * EnderIO's recipe registry is keyed by machine name, so every EnderIO recipe belongs to one machine and
 * the picker has one option. Registering still matters: the node prints the machine the recipe runs in,
 * not "EnderIO", and the capacitor row appears only on capacitor-driven machines.
 */
public enum EnderIOMachines implements MachineVariant {

    ALLOY_SMELTER("enderio:alloy_smelter", "tile.blockAlloySmelter.name", true),
    SAG_MILL("enderio:sag_mill", "tile.blockSagMill.name", true),
    VAT("enderio:vat", "tile.blockVat.name", true),
    SLICE_AND_SPLICE("enderio:slice_and_splice", "tile.blockSliceAndSplice.name", true),
    SOUL_BINDER("enderio:soul_binder", "tile.blockSoulBinder.name", true),
    /** Runs on experience levels, not a capacitor, so it has no tier row. */
    ENCHANTER("enderio:enchanter", "tile.blockEnchanter.name", false);

    private final String id;
    private final String nameKey;
    private final Set<Settings> settings;

    /**
     * @param id      the id stored in the chart. A PlanNH id, because EnderIO's recipe registry is keyed
     *                by machine name, not by block, and a chart has to survive a block rename
     * @param nameKey EnderIO's unlocalized block name, so the picker prints the machine name in the
     *                player's language
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
     * The EnderIO machine this recipe runs in. The provider sets it from the recipe's cached type,
     * which only the provider has access to.
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
     * Returns the machine the provider stored in {@link #MACHINE} while reading the node's recipe.
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
