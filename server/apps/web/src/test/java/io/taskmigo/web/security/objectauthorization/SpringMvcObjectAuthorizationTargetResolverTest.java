package io.taskmigo.web.security.objectauthorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.AuthorizationContract;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.condition.VersionRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@ExtendWith(MockitoExtension.class)
class SpringMvcObjectAuthorizationTargetResolverTest {

    @Mock
    private ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @Mock
    private RequestMappingInfo mapping;

    @Mock
    private VersionRequestCondition version;

    @Mock
    private TestAuthorizationContract schema;

    @Mock
    private OtherAuthorizationContract otherSchema;

    @Test
    @DisplayName("derives an Object Authorization route from the handler's typed Authorization Contract")
    void shouldResolveSchemaWhenContractHandlerMappingMatchesStatementTarget() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("list");
        HandlerMethod handler = new HandlerMethod(new TestController(), method);
        when(this.handlerMappings.getObject()).thenReturn(this.handlerMapping);
        when(this.handlerMapping.getHandlerMethods()).thenReturn(Map.of(this.mapping, handler));
        when(this.mapping.getVersionCondition()).thenReturn(this.version);
        when(this.version.getVersion()).thenReturn("0");
        when(this.mapping.getPatternValues()).thenReturn(Set.of("/api/v{version}/objects"));
        when(this.mapping.getMethodsCondition()).thenReturn(new RequestMethodsRequestCondition(RequestMethod.GET));
        SpringMvcObjectAuthorizationTargetResolver resolver = new SpringMvcObjectAuthorizationTargetResolver(
            this.handlerMappings,
            List.of(this.otherSchema, this.schema)
        );

        resolver.afterSingletonsInstantiated();
        List<ObjectAuthorizationSchema<?>> applicable = resolver.applicable(
            "GET",
            StatementTargetPathMatcher.compile("/api/v0/objects")
        );

        assertThat(applicable).containsExactly(this.schema);
    }

    private static final class TestController {

        @AuthorizationContract(TestAuthorizationContract.class)
        void list() {}
    }

    private interface TestAuthorizationContract extends ObjectAuthorizationSchema<TestObject> {}

    private interface OtherAuthorizationContract extends ObjectAuthorizationSchema<TestObject> {}

    private static final class TestObject {}
}
