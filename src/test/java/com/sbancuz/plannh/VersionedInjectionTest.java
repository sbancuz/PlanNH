package com.sbancuz.plannh;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sbancuz.plannh.annotation.Versioned;
import com.sbancuz.plannh.annotation.VersionedInjector;

/**
 * Drives the injector against a stand-in "dependency" declared right here, so the mechanism is
 * checked without needing a mod installed - and without the check quietly turning into an assertion
 * about that mod's balance.
 */
class VersionedInjectionTest {

    private static final String FAKE_MOD = "com.sbancuz.plannh.VersionedInjectionTest$Dependency";

    /** Stands in for the mod: static fields, one of them private, one renamed since an old release. */
    @SuppressWarnings("unused")
    static final class Dependency {

        static int SPEED = 42;
        private static final double[] TABLE = { 1.5, 2.5 };
        private static int HIDDEN = 77;
        static int RENAMED_NEW = 7;
        /** Only the old name exists here, which is the mod version a fallthrough has to cope with. */
        static int LEGACY_NAME = 5;
        /** A field whose type the mod changed out from under the mirror. */
        static String TYPE_CHANGED = "no longer a number";
        int notStatic = 3;
    }

    @Versioned.Mod(modId = "fakemod", sinceVersion = "1.0")
    static final class Holder {

        @Versioned.Class(FAKE_MOD)
        static class Mirror {

            static int SPEED = 1;
            static double[] TABLE = { 0.0 };
            static int notStatic = 111;
            static int ABSENT = 222;
            static int HIDDEN = 444;
            static int TYPE_CHANGED = 666;

            @Versioned.Constant("RENAMED_NEW")
            @Versioned.Constant("RENAMED_OLD")
            static int renamed = 333;

            /** The first name is the one this dependency does not have, so it must try the second. */
            @Versioned.Constant("MODERN_NAME")
            @Versioned.Constant("LEGACY_NAME")
            static int renamedSinceThisVersion = 555;
        }
    }

    /** A mirror that kept its final, which is the shape that made the whole thing a no-op. */
    @Versioned.Mod(modId = "fakemod", sinceVersion = "1.0")
    static final class FinalHolder {

        @Versioned.Class(FAKE_MOD)
        static class Mirror {

            static final int SPEED = 1;
        }
    }

    /** A mirror pointed at a class the dependency does not have. */
    @Versioned.Mod(modId = "fakemod", sinceVersion = "1.0")
    static final class MovedHolder {

        @Versioned.Class("com.sbancuz.plannh.NoSuchClassAnywhere")
        static class Mirror {

            static int SPEED = 1;
        }
    }

    @BeforeEach
    void resetMirrors() {
        Holder.Mirror.SPEED = 1;
        Holder.Mirror.TABLE = new double[] { 0.0 };
        Holder.Mirror.notStatic = 111;
        Holder.Mirror.ABSENT = 222;
        Holder.Mirror.HIDDEN = 444;
        Holder.Mirror.TYPE_CHANGED = 666;
        Holder.Mirror.renamed = 333;
        Holder.Mirror.renamedSinceThisVersion = 555;
    }

    /** The headline: after injection the mirror holds the dependency's number, not PlanNH's. */
    @Test
    void aMirrorTakesTheDependencysValue() {
        VersionedInjector.inject(Holder.class);

        assertEquals(Dependency.SPEED, Holder.Mirror.SPEED);
    }

    /** Arrays were the only thing that ever worked, so they have to keep working. */
    @Test
    void aTableIsCopiedWhole() {
        VersionedInjector.inject(Holder.class);

        assertArrayEquals(new double[] { 1.5, 2.5 }, Holder.Mirror.TABLE);
    }

    /** Mods keep their constants private more often than not, so private is not a reason to give up. */
    @Test
    void aPrivateFieldIsStillRead() {
        VersionedInjector.inject(Holder.class);

        assertEquals(77, Holder.Mirror.HIDDEN, "HIDDEN is private on the dependency");
    }

    /**
     * The version-dependent lookup: one PlanNH constant, several names the mod has called it, first
     * that resolves wins. This is what lets a mirror survive a rename without a second mirror.
     */
    @Test
    void aRenamedFieldIsFoundUnderItsCurrentName() {
        VersionedInjector.inject(Holder.class);

        assertEquals(Dependency.RENAMED_NEW, Holder.Mirror.renamed);
    }

    /**
     * The half that actually needs the loop: the name listed first is the one a newer mod uses, this
     * dependency only has the older one, so resolving it means trying past a miss rather than giving
     * up at the first name.
     */
    @Test
    void anOlderDependencyIsFoundUnderThePreviousName() {
        VersionedInjector.inject(Holder.class);

        assertEquals(Dependency.LEGACY_NAME, Holder.Mirror.renamedSinceThisVersion);
    }

    /** A field the dependency does not have leaves the fallback alone rather than zeroing it. */
    @Test
    void aMissingFieldLeavesTheFallback() {
        VersionedInjector.inject(Holder.class);

        assertEquals(222, Holder.Mirror.ABSENT);
    }

    /**
     * A mod that changed a field's type leaves the fallback alone. The value is found, so the miss is
     * not a missing field - it is a value the mirror cannot hold, and writing it is what would throw.
     */
    @Test
    void aFieldWhoseTypeChangedLeavesTheFallback() {
        VersionedInjector.inject(Holder.class);

        assertEquals(666, Holder.Mirror.TYPE_CHANGED);
    }

    /** An instance field is not a constant to mirror, and reading it would need an instance. */
    @Test
    void anInstanceFieldIsNotTreatedAsAConstant() {
        VersionedInjector.inject(Holder.class);

        assertEquals(111, Holder.Mirror.notStatic);
    }

    /**
     * A final mirror is refused outright. Writing it would appear to work while every use site kept
     * the folded literal.
     *
     * <p>
     * Read reflectively on purpose. {@code FinalHolder.Mirror.SPEED} written plainly is folded into
     * this test too, so the assertion would read the literal 1 whatever the field held and would pass
     * even if the injector had written it.
     *
     * <p>
     * This pins the outcome, not which layer produced it: without the injector's own check the JVM
     * refuses the write anyway and the field is equally untouched. The check earns its place by saying
     * "drop the final" instead of reporting a type error, and {@code VersionedMirrorShapeTest} is what
     * actually stops a final mirror being written in the first place.
     */
    @Test
    void aFinalMirrorIsRefused() throws ReflectiveOperationException {
        VersionedInjector.inject(FinalHolder.class);

        final Field untouched = FinalHolder.Mirror.class.getDeclaredField("SPEED");
        untouched.setAccessible(true);
        assertEquals(1, untouched.getInt(null), "a final mirror must be left alone, not written behind its use sites");
    }

    /** A class the dependency moved costs its own mirrors, not the launch. */
    @Test
    void aMissingSourceClassIsSurvivable() {
        VersionedInjector.inject(MovedHolder.class);

        assertEquals(1, MovedHolder.Mirror.SPEED);
    }

    /** A class with no {@code @Versioned.Mod} is simply not ours to fill in. */
    @Test
    void anUnannotatedHolderIsIgnored() {
        VersionedInjector.inject(String.class);
    }
}
