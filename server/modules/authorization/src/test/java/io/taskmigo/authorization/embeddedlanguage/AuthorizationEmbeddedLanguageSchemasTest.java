package io.taskmigo.authorization.embeddedlanguage;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationEmbeddedLanguageSchemasTest {

    @Test
    @DisplayName("maps enum query fields to the Language string type")
    void shouldCompileStringComparisonWhenObjectFieldIsEnumBacked() {
        QuerySchemaView schema = new QuerySchemaView() {
            @Override
            public String operation() {
                return "test.users.update";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of(
                    new QueryFieldDescriptor(
                        QueryPath.of("status"),
                        TypeDescriptor.of(Status.class),
                        false,
                        Set.of(QueryOperator.EQ, QueryOperator.NE)
                    )
                );
            }
        };

        assertThatCode(() ->
            new LanguageCompiler().compile(
                "object.status == \"RETAINED\"",
                AuthorizationEmbeddedLanguageSchemas.object(schema),
                AuthorizationCompilationProfile.objectPolicy()
            )
        ).doesNotThrowAnyException();
    }

    private enum Status {
        ACTIVE,
        RETAINED,
    }
}
