package io.taskmigo.authorization.object;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Declares the typed Object Authorization contract protecting one HTTP handler.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuthorizationContract {
    Class<? extends ObjectAuthorizationSchema<?>> value();
}
