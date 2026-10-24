package io.taskmigo.query;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares the typed Query contract exposed by one HTTP handler.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface QueryContract {
    Class<? extends QuerySchemaView> value();
}
