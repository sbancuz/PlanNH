package com.sbancuz.plannh.gui.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The order machines, groups and relations are handed to the engine in.
 *
 * <p>
 * A layered engine consumes nodes in model order and breaks ties on it, so some total order is
 * mandatory — shuffling insertion order moves machines that identical input run twice leaves alone.
 * This is the whole of it, because the chart's collections are already {@code TreeMap<UUID, _>} and
 * iterating them <em>is</em> a total order. Machines key on their own id, a group on the earliest id
 * among its members so a container is seeded where its contents would have been.
 *
 * <p>
 * The order is a <em>tree</em>: depth-first pre-order, so a group's members come out as one
 * consecutive run. They are laid out inside a single compound, and a flat order interleaving them
 * with loose machines would be wrong rather than merely untidy.
 */
final class CanonicalOrder {

    private final LayoutRequest request;
    private final Map<UUID, LayoutGroup> groups = new HashMap<>();
    private final Map<UUID, UUID> parentOf = new HashMap<>();
    private final Map<UUID, UUID> keyOf = new HashMap<>();
    private final Set<UUID> machineIds = new HashSet<>();

    private List<UUID> machineOrder;
    private List<UUID> groupOrder;
    private List<LayoutRelation> relationOrder;

    public CanonicalOrder(final LayoutRequest request) {
        this.request = request;
        for (final LayoutGroup group : request.groups()) groups.put(group.id(), group);

        // Membership is the parent edge: a machine in a group and a group in a group are both members
        // of something, which is what makes "is this a container" and "where does this live" separate
        // questions rather than one.
        for (final LayoutGroup group : request.groups()) {
            for (final UUID member : group.memberIds()) {
                // A group listed as its own member would otherwise become its own parent and drop out
                // of the top level entirely, so it would never be emitted at all. Only reachable from
                // corrupt data; guarded because the alternative is an empty layout with no complaint.
                if (member.equals(group.id())) continue;
                parentOf.putIfAbsent(member, group.id());
            }
        }

        for (final LayoutMachine machine : request.machines()) {
            keyOf.put(machine.id(), machine.id());
            machineIds.add(machine.id());
        }
        for (final LayoutGroup group : request.groups()) keyOf.put(group.id(), keyOfGroup(group, new HashSet<>()));
    }

    /** Every machine, depth-first pre-order. Groups' members appear as consecutive runs. */
    public List<UUID> machines() {
        if (machineOrder == null) buildOrder();
        return machineOrder;
    }

    /** Every group, depth-first pre-order, so a parent always precedes the groups nested inside it. */
    public List<UUID> groupIds() {
        if (groupOrder == null) buildOrder();
        return groupOrder;
    }

    /** The relations, in request order, with any whose endpoints are not both present dropped. */
    public List<LayoutRelation> relations() {
        if (relationOrder == null) {
            final Set<UUID> present = new HashSet<>();
            for (final LayoutMachine machine : request.machines()) present.add(machine.id());
            relationOrder = request.relations()
                .stream()
                .filter(relation -> present.contains(relation.sourceId()) && present.contains(relation.targetId()))
                .toList();
        }
        return relationOrder;
    }

    /**
     * The immediate group containing this machine or group, or null at the top level.
     *
     * <p>
     * The engine needs this to decide which compound a thing belongs to, and the applier needs it to
     * convert a world position into the group-relative coordinate the model stores.
     */
    public UUID parentOf(final UUID id) {
        return parentOf.get(id);
    }

    private void buildOrder() {
        final Set<UUID> topLevel = new LinkedHashSet<>();
        for (final LayoutMachine machine : request.machines()) {
            if (!parentOf.containsKey(machine.id())) topLevel.add(machine.id());
        }
        for (final UUID groupId : groups.keySet()) {
            if (!parentOf.containsKey(groupId)) topLevel.add(groupId);
        }

        final List<UUID> machines = new ArrayList<>(
            request.machines()
                .size());
        final List<UUID> orderedGroups = new ArrayList<>(groups.size());
        emit(topLevel, machines, orderedGroups, new HashSet<>());

        machineOrder = List.copyOf(machines);
        groupOrder = List.copyOf(orderedGroups);
    }

    /**
     * Sorts one container's members and emits them depth-first.
     *
     * @param onPath groups already being emitted higher up this branch, so corrupt data where a group
     *               contains itself terminates instead of recursing forever. Unreachable from a chart
     *               built by dragging, which is exactly why it needs a guard rather than a check
     */
    private void emit(final Iterable<UUID> members, final List<UUID> machines, final List<UUID> orderedGroups,
        final Set<UUID> onPath) {

        // Sorted by key, not by id: a group is keyed on its contents, so the container is seeded where
        // its members would have been.
        final List<UUID> sorted = new ArrayList<>();
        members.forEach(sorted::add);
        sorted.sort(
            Comparator.comparing((UUID id) -> keyOf.get(id))
                .thenComparing(Comparator.naturalOrder()));

        for (final UUID id : sorted) {
            final LayoutGroup group = groups.get(id);
            if (group != null) {
                if (!onPath.add(id)) continue;
                orderedGroups.add(id);
                emit(group.memberIds(), machines, orderedGroups, onPath);
                onPath.remove(id);
                continue;
            }
            // A member that is neither a machine nor a group is stale membership - a machine deleted
            // while its group still named it. Emitting it would put an id in the machine order that no
            // spec exists for, and the engine would then look up null.
            if (!machineIds.contains(id)) continue;
            machines.add(id);
        }
    }

    /** A group keys on the earliest id it contains, falling back to its own id when empty. */
    private UUID keyOfGroup(final LayoutGroup group, final Set<UUID> seen) {
        UUID earliest = null;
        for (final UUID member : group.memberIds()) {
            final UUID key = groups.containsKey(member) && seen.add(member) ? keyOfGroup(groups.get(member), seen)
                : keyOf.get(member);
            if (key == null) continue;
            if (earliest == null || key.compareTo(earliest) < 0) earliest = key;
        }
        group.memberIds()
            .forEach(seen::remove);
        return earliest == null ? group.id() : earliest;
    }
}
