package com.sbancuz.plannh.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a constant PlanNH holds is really a dependency's number, and should be read from that
 * dependency at startup instead of trusted.
 *
 * <p>
 * A mod's balance changes without asking PlanNH, and a copied number then reports a machine that no
 * longer exists. The copy stays in the source as the value to use when the dependency is absent or has
 * moved on, and {@link VersionedInjector} overwrites it during preInit with whatever the installed jar
 * actually says.
 *
 * <p>
 * Two things a mirror has to get right. A field must not be final: a {@code static final} with a
 * literal initializer is a JLS 4.12.4 constant variable, so javac folds its value into every use site
 * and overwriting the field reaches nobody. And {@link Class#value()} must name the dependency's class
 * in full, since nothing derives it from the mirror's own name.
 */
public @interface Versioned {

    /**
     * Marks a class that holds mirrors of one mod's constants.
     *
     * @param sinceVersion the earliest version of the mod expected to have these. A field that cannot
     *                     be read is reported only when the installed mod is this version or newer,
     *                     because on an older one its absence is the whole reason for the fallback.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Mod {

        String modId();

        String sinceVersion();
    }

    /**
     * Marks a nested class whose fields mirror the static fields of one class in the dependency.
     *
     * @param value the dependency class's fully qualified name. Preferred over {@link #clazz()} for an
     *              optional mod: naming it as a string costs nothing when the mod is absent, whereas a
     *              class literal is a real reference the annotation cannot always resolve.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @SuppressWarnings("FieldMayBeFinal")
    @interface Class {

        String value() default "";

        java.lang.Class<?> clazz() default void.class;

        String modId() default "";

        String sinceVersion() default "";
    }

    /**
     * Names the field in the dependency this mirror reads, when the two names differ.
     *
     * <p>
     * Repeatable, which is how one PlanNH constant follows a field the mod renamed: give one
     * {@code @Constant} per name the field has had and the first that resolves wins. Order them
     * newest-first, since that is the version most players are on and the one worth finding without
     * a failed lookup in front of it.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Repeatable(Versioned.Constants.class)
    @SuppressWarnings("FieldMayBeFinal")
    @interface Constant {

        String value() default "";

        String sinceVersion() default "";
    }

    /** Holder for repeated {@link Constant}s; never written by hand. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Constants {

        Constant[] value();
    }
}
