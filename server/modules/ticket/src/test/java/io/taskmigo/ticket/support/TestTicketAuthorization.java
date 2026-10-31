package io.taskmigo.ticket.support;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.test.context.TestConfiguration;

@NullMarked
@TestConfiguration(proxyBeanMethods = false)
public class TestTicketAuthorization {

    public static <Q> ObjectAuthorizationPredicate<Q> context(ObjectAuthorizationPredicate<Q> predicate) {
        return predicate;
    }
}
