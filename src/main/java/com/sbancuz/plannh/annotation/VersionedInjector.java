package com.sbancuz.plannh.annotation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.sbancuz.plannh.PlanNH;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;

/**
 * Copies the dependency's own numbers over PlanNH's fallbacks at preInit. See {@link Versioned} for
 * what the annotations mean and what a mirror field has to look like.
 *
 * <p>
 * Everything here fails soft and says so. A mod that is absent, a class that moved, a field that was
 * renamed - each leaves the fallback in place, because a planner quoting a slightly stale number is
 * worth more than one that will not load. The one thing it will not do is fail quietly: a lookup that
 * misses on a mod new enough to have had the field is logged, since that is drift rather than an old
 * pack.
 */
public final class VersionedInjector {

    private VersionedInjector() {}

    public static void injectAll(final ASMDataTable asmData) {
        injectAll(asmData, VersionedInjector::modPresent);
    }

    /** As above, against a stated set of installed mods rather than the one Forge reports. */
    public static void injectAll(final ASMDataTable asmData, final Predicate<String> installed) {
        for (final ASMDataTable.ASMData data : asmData.getAll(Versioned.Mod.class.getName())) {
            // The mod id is read out of the ASM table rather than off the loaded class, because
            // loading a provider is exactly what has to be avoided when its mod is missing: the class
            // names the mod's types throughout, and resolving them is what would fail.
            final Object declaredModId = data.getAnnotationInfo()
                .get("modId");
            if (declaredModId != null && !installed.test(declaredModId.toString())) continue;

            try {
                inject(Class.forName(data.getClassName()));
            } catch (final ReflectiveOperationException | RuntimeException | LinkageError e) {
                // RuntimeException covers a mirror declared with clazz() rather than value(): reading
                // a Class-valued annotation element whose class is absent throws TypeNotPresentException,
                // and one unusable mirror must not be the thing that stops the game loading.
                PlanNH.LOG.error("PlanNH: cannot read versioned constants from {}", data.getClassName(), e);
            }
        }
    }

    /**
     * Fills in every mirror on one holder. Separate from the ASM sweep so it can be driven directly,
     * which is the only way to check the mechanism without a mod installed to check it against.
     */
    public static void inject(final Class<?> holder) {
        final Versioned.Mod mod = holder.getAnnotation(Versioned.Mod.class);
        if (mod == null) return;

        for (final Class<?> mirror : holder.getDeclaredClasses()) {
            final Versioned.Class declared = mirror.getAnnotation(Versioned.Class.class);
            if (declared == null) continue;

            final String modId = declared.modId()
                .isEmpty() ? mod.modId() : declared.modId();
            final String since = declared.sinceVersion()
                .isEmpty() ? mod.sinceVersion() : declared.sinceVersion();

            final Class<?> source = resolveSource(declared);
            if (source == null) {
                // Not "the mod is old": the class is named in full, so failing to find it means the
                // dependency moved it, and every field under this mirror is now a guess.
                warnIfNewEnough(
                    modId,
                    since,
                    () -> "class " + (declared.clazz() != void.class ? declared.clazz()
                        .getName() : declared.value()) + " is gone");
                continue;
            }

            for (final Field target : mirror.getDeclaredFields()) {
                injectField(target, source, modId, since);
            }
        }
    }

    private static void injectField(final Field target, final Class<?> source, final String modId,
        final String mirrorSince) {
        if (target.isSynthetic()) return;
        if (!Modifier.isStatic(target.getModifiers())) return;

        // javac folds a constant variable into its use sites, so writing this field would land
        // somewhere nothing reads.
        if (Modifier.isFinal(target.getModifiers())) {
            PlanNH.LOG.error(
                "PlanNH: {}.{} is final, so its value is compiled into every use site and cannot be "
                    + "read from {}. Drop the final.",
                target.getDeclaringClass()
                    .getSimpleName(),
                target.getName(),
                modId);
            return;
        }

        final List<Candidate> candidates = candidatesFor(target, mirrorSince);
        for (final Candidate candidate : candidates) {
            final Object value = read(source, candidate.name());
            if (value == null) continue;

            try {
                target.setAccessible(true);
                target.set(null, value);
                return;
            } catch (final ReflectiveOperationException | IllegalArgumentException e) {
                PlanNH.LOG.error(
                    "PlanNH: {}.{} does not accept {}.{}",
                    target.getDeclaringClass()
                        .getSimpleName(),
                    target.getName(),
                    source.getSimpleName(),
                    candidate.name(),
                    e);
                return;
            }
        }

        // Against the earliest version any of the names was expected under: from that release onwards
        // one of them should have resolved, so nothing resolving is drift rather than an old pack.
        warnIfNewEnough(modId, earliest(candidates), () -> source.getName() + " has no " + describe(candidates));
    }

    /** One name the dependency might know a constant by, and the release it was expected from. */
    private record Candidate(String name, String since) {}

    /**
     * The names to try, in declaration order, which the annotation asks to be newest-first. A field
     * with no {@code @Constant} mirrors the name it already has - the common case, and why the
     * annotation is optional.
     */
    private static List<Candidate> candidatesFor(final Field target, final String mirrorSince) {
        final Versioned.Constant[] declared = target.getAnnotationsByType(Versioned.Constant.class);
        if (declared.length == 0) return List.of(new Candidate(target.getName(), mirrorSince));

        final List<Candidate> candidates = new ArrayList<>();
        for (final Versioned.Constant constant : declared) {
            candidates.add(
                new Candidate(
                    constant.value()
                        .isEmpty() ? target.getName() : constant.value(),
                    constant.sinceVersion()
                        .isEmpty() ? mirrorSince : constant.sinceVersion()));
        }
        return candidates;
    }

    private static String earliest(final List<Candidate> candidates) {
        return candidates.stream()
            .map(Candidate::since)
            .min(Comparator.comparing(DefaultArtifactVersion::new))
            .orElseThrow();
    }

    private static String describe(final List<Candidate> candidates) {
        if (candidates.size() == 1) return "field " + candidates.getFirst()
            .name();
        return "field named any of " + candidates.stream()
            .map(Candidate::name)
            .toList();
    }

    /** The dependency's value, or null when it has no such static field to read. */
    @Nullable
    private static Object read(final Class<?> source, final String name) {
        try {
            final Field field = source.getDeclaredField(name);
            if (!Modifier.isStatic(field.getModifiers())) return null;
            field.setAccessible(true);
            return field.get(null);
        } catch (final ReflectiveOperationException | RuntimeException | LinkageError absent) {
            return null;
        }
    }

    @Nullable
    private static Class<?> resolveSource(final Versioned.Class declared) {
        if (declared.clazz() != void.class) return declared.clazz();
        if (declared.value()
            .isEmpty()) {
            PlanNH.LOG.error("PlanNH: @Versioned.Class needs the dependency class named in full");
            return null;
        }
        try {
            return Class.forName(declared.value());
        } catch (final ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /**
     * Reports a miss only against a mod new enough to have had what was looked for. On an older one
     * the fallback is the right answer and saying so every launch would train people to ignore it.
     */
    private static void warnIfNewEnough(final String modId, final String sinceVersion, final Supplier<String> what) {
        final ModContainer container = installed(modId);
        if (container == null) return;

        if (new DefaultArtifactVersion(container.getVersion()).compareTo(new DefaultArtifactVersion(sinceVersion)) < 0)
            return;

        PlanNH.LOG.warn(
            "PlanNH: {} {} - {}. Falling back to the value PlanNH was written against, which may be stale.",
            modId,
            container.getVersion(),
            what.get());
    }

    /** Null when the mod is absent, or when there is no Forge to ask - a test driving this directly. */
    @Nullable
    private static ModContainer installed(final String modId) {
        try {
            return Loader.instance()
                .getIndexedModList()
                .get(modId);
        } catch (final RuntimeException | LinkageError outsideForge) {
            return null;
        }
    }

    private static boolean modPresent(final String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (final RuntimeException | LinkageError outsideForge) {
            return true;
        }
    }
}
