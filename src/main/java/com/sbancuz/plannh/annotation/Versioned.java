package com.sbancuz.plannh.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public @interface Versioned {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Mod {

        String modId();

        String sinceVersion();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @SuppressWarnings("FieldMayBeFinal")
    @interface Class {

        String value() default "";

        java.lang.Class<?> clazz() default void.class;

        String modId() default "";

        String sinceVersion() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @SuppressWarnings("FieldMayBeFinal")
    @interface Constant {

        String value() default "";

        String sinceVersion() default "";
    }
}
