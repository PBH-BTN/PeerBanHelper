package com.ghostchu.peerbanhelper.config;

import org.jetbrains.annotations.Nullable;

import java.lang.annotation.*;

/**
 * Mark a method as update script and will be executed by ConfigurationUpdater
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface UpdateScript {
    @Nullable String description() default "";

    int version();
}
