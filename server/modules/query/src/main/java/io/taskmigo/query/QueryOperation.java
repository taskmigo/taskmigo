package io.taskmigo.query;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Selects the persistence-neutral Query Schema operation used by one inbound handler.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface QueryOperation {
    /// Returns the stable operation identifier published by the handler's Query Schema.
    String value();
}
