from pathlib import Path
import re


def read(path: str) -> str:
    return Path(path).read_text()


def write(path: str, text: str) -> None:
    Path(path).write_text(text)


def replace(path: str, old: str, new: str, expected: int = 1) -> None:
    text = read(path)
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path}: expected {expected} occurrences, found {count}: {old!r}")
    write(path, text.replace(old, new))


replace(
    "server/modules/identity/src/main/java/io/taskmigo/identity/package-info.java",
    '        "authorization :: object-model",\n',
    '        "authorization :: object-model",\n        "authorization :: object-use-cases",\n        "authorization :: request",\n',
)
replace(
    "server/modules/ticket/src/main/java/io/taskmigo/ticket/package-info.java",
    '        "authorization :: object-model",\n',
    '        "authorization :: object-model",\n        "authorization :: object-use-cases",\n        "authorization :: request",\n',
)

user_support = Path(
    "server/modules/identity/src/test/java/io/taskmigo/identity/user/application/UserAuthorizationTestSupport.java"
)
user_support.write_text(
    '''package io.taskmigo.identity.user.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;

final class UserAuthorizationTestSupport {

    static final AuthorizationContext CONTEXT = new AuthorizationContext() {};

    private UserAuthorizationTestSupport() {}

    static List<QuerySchemaView> schemas() {
        return List.of(
            schema("identity.users.list"),
            schema("identity.users.delete"),
            schema("identity.users.update-statements")
        );
    }

    static ObjectAuthorization objectAuthorization(ObjectAuthorizationPredicate<UserInfo> predicate) {
        return new ObjectAuthorization() {
            @Override
            @SuppressWarnings("unchecked")
            public <Q> ObjectAuthorizationPredicate<Q> authorize(
                AuthorizationContext context,
                QuerySchemaView schema,
                QueryFieldContext queryContext
            ) {
                return (ObjectAuthorizationPredicate<Q>) predicate;
            }

            @Override
            public void validatePolicy(String policy, String method, String path) {}
        };
    }

    static ObjectAuthorizationPredicate<UserInfo> allowAll() {
        return new ObjectAuthorizationPredicate<>() {
            @Override
            public boolean isAlwaysTrue() {
                return true;
            }

            @Override
            public boolean isAlwaysFalse() {
                return false;
            }
        };
    }

    private static QuerySchemaView schema(String operation) {
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return operation;
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of();
            }
        };
    }
}
'''
)

user_service_test = (
    "server/modules/identity/src/test/java/io/taskmigo/identity/user/application/DefaultUserServiceTest.java"
)
replace(
    user_service_test,
    'import io.taskmigo.identity.user.domain.User;\n',
    'import io.taskmigo.identity.user.domain.User;\nimport io.taskmigo.query.ListQueryCompiler;\n',
)
replace(
    user_service_test,
    '            Mockito.mock(UserDeletionLifecycleService.class),\n            audits,\n            Clock.fixed(NOW, ZoneOffset.UTC)\n',
    '            Mockito.mock(UserDeletionLifecycleService.class),\n            audits,\n            UserAuthorizationTestSupport.schemas(),\n            UserAuthorizationTestSupport.objectAuthorization(UserAuthorizationTestSupport.allowAll()),\n            new ListQueryCompiler(),\n            Clock.fixed(NOW, ZoneOffset.UTC)\n',
)

user_auth_test = (
    "server/modules/identity/src/test/java/io/taskmigo/identity/user/application/DefaultUserAuthorizationMutationTest.java"
)
replace(
    user_auth_test,
    'import io.taskmigo.identity.user.domain.User;\n',
    'import io.taskmigo.identity.user.domain.User;\nimport io.taskmigo.query.ListQueryCompiler;\n',
)
text = read(user_auth_test)
setup_pattern = r'DefaultUserService service = service\((.*?)\n        \);'
if len(list(re.finditer(setup_pattern, text, re.S))) != 5:
    raise SystemExit(f"{user_auth_test}: expected 5 service setup calls")
text = re.sub(
    setup_pattern,
    lambda match: "DefaultUserService service = service("
    + match.group(1)
    + ",\n            authorization\n        );",
    text,
    count=5,
    flags=re.S,
)
text = text.replace(
    '                authorization,\n                new UserMutationActor',
    '                UserAuthorizationTestSupport.CONTEXT,\n                new UserMutationActor',
)
text = text.replace(
    'service.delete(userId, authorization, new UserMutationActor',
    'service.delete(userId, UserAuthorizationTestSupport.CONTEXT, new UserMutationActor',
)
old_signature = '''        SubjectGrantQueryService queries,
        SubjectGrantAssignmentService assignments,
        UserDeletionLifecycleService deletion
    ) {'''
new_signature = '''        SubjectGrantQueryService queries,
        SubjectGrantAssignmentService assignments,
        UserDeletionLifecycleService deletion,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {'''
if old_signature not in text:
    raise SystemExit(f"{user_auth_test}: helper signature not found")
text = text.replace(old_signature, new_signature, 1)
old_constructor = '''            deletion,
            mock(UserAuditAppender.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
'''
new_constructor = '''            deletion,
            mock(UserAuditAppender.class),
            UserAuthorizationTestSupport.schemas(),
            UserAuthorizationTestSupport.objectAuthorization(authorization),
            new ListQueryCompiler(),
            Clock.fixed(NOW, ZoneOffset.UTC)
'''
if old_constructor not in text:
    raise SystemExit(f"{user_auth_test}: service constructor block not found")
write(user_auth_test, text.replace(old_constructor, new_constructor, 1))

ticket_support_dir = Path("server/modules/ticket/src/test/java/io/taskmigo/ticket/support")
ticket_support_dir.mkdir(parents=True, exist_ok=True)
(ticket_support_dir / "TestTicketAuthorization.java").write_text(
    '''package io.taskmigo.ticket.support;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QuerySchemaView;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
public class TestTicketAuthorization implements ObjectAuthorization {

    public static AuthorizationContext context(ObjectAuthorizationPredicate<?> predicate) {
        return new PredicateContext(predicate);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        QuerySchemaView schema,
        QueryFieldContext queryContext
    ) {
        if (!(context instanceof PredicateContext predicateContext)) {
            throw new IllegalArgumentException("Unsupported test authorization context");
        }
        return (ObjectAuthorizationPredicate<Q>) predicateContext.predicate();
    }

    @Override
    public void validatePolicy(String policy, String method, String path) {}

    private record PredicateContext(ObjectAuthorizationPredicate<?> predicate) implements AuthorizationContext {}
}
'''
)
(ticket_support_dir / "TicketQueryTestSupport.java").write_text(
    '''package io.taskmigo.ticket.support;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TicketQueryTestSupport {

    private TicketQueryTestSupport() {}

    public static TicketQueryFieldContexts emptyContexts() {
        return new TicketQueryFieldContexts() {
            @Override
            public QueryFieldContext global() {
                return QueryFieldContext.empty();
            }

            @Override
            public QueryFieldContext forType(UUID ticketTypeId) {
                return QueryFieldContext.empty();
            }

            @Override
            public QueryFieldContext forTicket(UUID ticketId) {
                return QueryFieldContext.empty();
            }
        };
    }

    public static List<QuerySchemaView> schemas(String... operations) {
        return Arrays.stream(operations).map(TicketQueryTestSupport::schema).toList();
    }

    private static QuerySchemaView schema(String operation) {
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return operation;
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of();
            }
        };
    }
}
'''
)

ticket_service_test = "server/modules/ticket/src/test/java/io/taskmigo/ticket/application/TicketServiceTest.java"
replace(
    ticket_service_test,
    'import io.taskmigo.query.ListQuerySpec;\n',
    'import io.taskmigo.query.ListQueryCompiler;\nimport io.taskmigo.query.ListQuerySpec;\n',
)
replace(
    ticket_service_test,
    'import io.taskmigo.ticket.domain.Ticket;\n',
    'import io.taskmigo.ticket.domain.Ticket;\nimport io.taskmigo.ticket.support.TestTicketAuthorization;\nimport io.taskmigo.ticket.support.TicketQueryTestSupport;\n',
)
replace(
    ticket_service_test,
    '        this.tickets,\n        this.tickets,\n        Clock.fixed(NOW, ZoneOffset.UTC)\n',
    '        this.tickets,\n        this.tickets,\n        TicketQueryTestSupport.emptyContexts(),\n        TicketQueryTestSupport.schemas("tickets.list", "tickets.read", "tickets.update"),\n        new TestTicketAuthorization(),\n        new ListQueryCompiler(),\n        Clock.fixed(NOW, ZoneOffset.UTC)\n',
)
text = read(ticket_service_test)
text, migrated_updates = re.subn(
    r',\n(?P<indent>\s*)ALLOW_ALL,\n(?P=indent)QueryFieldContext\.empty\(\)',
    lambda match: ",\n" + match.group("indent") + "TestTicketAuthorization.context(ALLOW_ALL)",
    text,
)
if migrated_updates != 2:
    raise SystemExit(f"{ticket_service_test}: expected 2 update authorization migrations, found {migrated_updates}")
write(ticket_service_test, text)

persistence_test = (
    "server/modules/ticket/src/test/java/io/taskmigo/ticket/persistence/TicketQueryPersistenceIntegrationTest.java"
)
replace(persistence_test, 'import io.taskmigo.query.ListQuerySpec;\n', 'import io.taskmigo.query.ListQueryRequest;\n')
for unused in (
    'import io.taskmigo.query.QueryPath;\n',
    'import io.taskmigo.query.QuerySort;\n',
    'import io.taskmigo.query.QueryPredicate;\n',
    'import org.jspecify.annotations.Nullable;\n',
):
    replace(persistence_test, unused, '')
replace(
    persistence_test,
    'import io.taskmigo.ticket.application.api.TicketService;\n',
    'import io.taskmigo.ticket.application.api.TicketService;\nimport io.taskmigo.ticket.support.TestTicketAuthorization;\n',
)
replace(
    persistence_test,
    'import org.springframework.context.annotation.FilterType;\n',
    'import org.springframework.context.annotation.FilterType;\nimport org.springframework.context.annotation.Import;\n',
)
replace(persistence_test, '@SpringBootTest(', '@Import(TestTicketAuthorization.class)\n@SpringBootTest(')
text = read(persistence_test)
text, first_query = re.subn(
    r'        ListQuerySpec<TicketInfo> query = new ListQuerySpec<>\(\n            compile\(context, "object\.score >= 2"\),\n            context,\n            List\.of\(new QuerySort\(QueryPath\.of\("score"\), QuerySort\.Direction\.ASC\)\)\n        \);',
    '        ListQueryRequest query = new ListQueryRequest("object.score >= 2", List.of("score"));',
    text,
)
text, second_query = re.subn(
    r'        ListQuerySpec<TicketInfo> query = new ListQuerySpec<>\(\n            compile\(context, null\),\n            context,\n            List\.of\(new QuerySort\(QueryPath\.of\("dueDate"\), QuerySort\.Direction\.ASC\)\)\n        \);',
    '        ListQueryRequest query = new ListQueryRequest(null, List.of("dueDate"));',
    text,
)
if first_query != 1 or second_query != 1:
    raise SystemExit(f"{persistence_test}: query migration mismatch {first_query=} {second_query=}")
text = text.replace(
    'this.tickets.list(1, 2, typeId, query, authorization)',
    'this.tickets.list(1, 2, typeId, query, TestTicketAuthorization.context(authorization))',
    1,
)
text = text.replace(
    'this.tickets.list(1, 10, typeId, query, allow)',
    'this.tickets.list(1, 10, typeId, query, TestTicketAuthorization.context(allow))',
    1,
)
text, helper_count = re.subn(
    r'\n    private QueryPredicate<TicketInfo> compile\(QueryFieldContext context, @Nullable String source\) \{\n        return \(QueryPredicate<TicketInfo>\) new FilterByCompiler\(\)\.compile\(this\.listSchema, context, source\);\n    \}\n',
    '\n',
    text,
)
if helper_count != 1:
    raise SystemExit(f"{persistence_test}: compile helper migration mismatch {helper_count}")
write(persistence_test, text)

for path in (
    "server/modules/ticket/src/test/java/io/taskmigo/ticket/persistence/TicketConcurrencyHistoryIntegrationTest.java",
    "server/modules/ticket/src/test/java/io/taskmigo/ticket/persistence/TicketHistoricalSchemaIntegrationTest.java",
):
    text = read(path)
    ticket_import = 'import io.taskmigo.ticket.application.api.TicketService;\n'
    if ticket_import not in text:
        raise SystemExit(f"{path}: TicketService import anchor missing")
    text = text.replace(
        ticket_import,
        ticket_import + 'import io.taskmigo.ticket.support.TestTicketAuthorization;\n',
        1,
    )
    spring_import = 'import org.springframework.context.annotation.FilterType;\n'
    if spring_import not in text:
        raise SystemExit(f"{path}: FilterType import anchor missing")
    text = text.replace(
        spring_import,
        spring_import + 'import org.springframework.context.annotation.Import;\n',
        1,
    )
    text = text.replace('@SpringBootTest(', '@Import(TestTicketAuthorization.class)\n@SpringBootTest(', 1)
    text, read_count = re.subn(
        r'this\.tickets\.read\(([^,]+), ALLOW_ALL, QueryFieldContext\.empty\(\)\)',
        r'this.tickets.read(\1, TestTicketAuthorization.context(ALLOW_ALL))',
        text,
    )
    if read_count == 0:
        raise SystemExit(f"{path}: no legacy read calls migrated")
    write(path, text)

web_context_test = Path(
    "server/apps/web/src/test/java/io/taskmigo/web/api/v0/ticket/TicketQueryFieldContextSourceTest.java"
)
if not web_context_test.exists():
    raise SystemExit(f"missing {web_context_test}")
web_context_test.unlink()
