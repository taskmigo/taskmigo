package io.taskmigo.web.composition.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.TaskmigoApplication;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

class HttpSessionRevocationIntegrationTest extends ApiIntegrationTestSupport {

    private static final String PASSWORD = "session-test-password";
    private static final String PKCE =
        "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256";
    private static final String AUTHORIZE =
        "/oauth2/authorize?response_type=code&client_id=session-test&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&scope=openid" +
        PKCE;
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final JdbcTemplate jdbc;
    private final IdentityProvisioningService provisioning;
    private final UserRetentionService retention;
    private final JdbcRegisteredClientRepository clients;
    private final PostgreSQLContainer postgres;
    private final SessionRepository<?> sessions;
    private final PlatformTransactionManager transactions;

    HttpSessionRevocationIntegrationTest(
        JdbcTemplate jdbc,
        IdentityProvisioningService provisioning,
        UserRetentionService retention,
        JdbcRegisteredClientRepository clients,
        PostgreSQLContainer postgres,
        SessionRepository<?> sessions,
        PlatformTransactionManager transactions
    ) {
        this.jdbc = jdbc;
        this.provisioning = provisioning;
        this.retention = retention;
        this.clients = clients;
        this.postgres = postgres;
        this.sessions = sessions;
        this.transactions = transactions;
    }

    @BeforeEach
    void configureSessionClient() {
        this.setRetention("P30D");
        if (this.clients.findByClientId("session-test") == null) {
            this.clients.save(
                RegisteredClient.withId("session-test")
                    .clientId("session-test")
                    .clientSecret("{noop}secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("http://localhost/callback")
                    .scope("openid")
                    .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                    .build()
            );
        }
    }

    @AfterEach
    void restoreRetention() {
        this.setRetention("P30D");
    }

    /// Verifies DELETE removes real servlet sessions and OAuth rows immediately for both retention branches.
    /// Given: two successful browser logins for one User and one unrelated authenticated User.
    /// Expect: target sessions and attributes disappear, old cookies redirect to login, and unrelated access survives.
    @ParameterizedTest
    @ValueSource(strings = { "P30D", "P0D" })
    @DisplayName("revokes every browser session on retained and immediate tombstone deletion")
    void shouldRevokeAllBrowserSessionsWhenUserIsDeleted(String duration) throws Exception {
        // Arrange
        this.setRetention(duration);
        ManagedUser target = this.createUser();
        ManagedUser other = this.createUser();
        Browser first = this.login(target.username());
        Browser second = this.login(target.username());
        Browser unrelated = this.login(other.username());
        this.assertAuthorized(first, this.port());
        this.assertAuthorized(second, this.port());
        this.insertConsent(target.username());
        assertThat(this.sessionCount(target.id())).isEqualTo(2);
        assertThat(this.oauthCount(target.username())).isPositive();
        // Act
        this.api().users().delete(target.id());
        // Assert
        assertThat(this.sessionCount(target.id())).isZero();
        assertThat(this.oauthCount(target.username())).isZero();
        assertThat(this.consentCount(target.username())).isZero();
        assertThat(this.orphanAttributeCount()).isZero();
        assertThat(this.status(target.id())).isEqualTo(duration.equals("P0D") ? "TOMBSTONE" : "RETAINED");
        this.assertLoginRequired(first, this.port());
        this.assertLoginRequired(second, this.port());
        this.assertAuthorized(unrelated, this.port());
        assertThat(this.sessionCount(other.id())).isOne();
    }

    /// Verifies a session created by one Web instance is available and revocable on another instance.
    /// Given: two real Web application contexts sharing PostgreSQL and one authenticated browser.
    /// Expect: Web B accepts Web A's cookie, deletes the User, and both instances reject the old session.
    @Test
    @DisplayName("shares and revokes browser sessions across two Web instances")
    void shouldRevokeSessionAcrossInstancesWhenOtherInstanceDeletesUser() throws Exception {
        // Arrange
        ManagedUser user = this.createUser();
        Browser browser = this.login(user.username());
        try (
            var context = new SpringApplicationBuilder(TaskmigoApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + this.postgres.getJdbcUrl(),
                "--spring.datasource.username=" + this.postgres.getUsername(),
                "--spring.datasource.password=" + this.postgres.getPassword(),
                "--spring.flyway.enabled=false",
                "--taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
                "--taskmigo.oauth.signing-key-auto-create=true"
            )
        ) {
            int otherPort = Objects.requireNonNull(
                ((ServletWebServerApplicationContext) context).getWebServer()
            ).getPort();
            this.assertAuthorized(browser, otherPort);
            TaskmigoApiClient otherApi = new TaskmigoApiClient(
                URI.create("http://localhost:" + otherPort),
                new TaskmigoApiClient.ClientCredentials("browser", "integration-secret")
            );
            // Act
            otherApi.users().delete(user.id());
            // Assert
            assertThat(this.sessionCount(user.id())).isZero();
            this.assertLoginRequired(browser, this.port());
            this.assertLoginRequired(browser, otherPort);
        }
    }

    /// Verifies failed deletion does not partially revoke browser authentication.
    /// Given: active sessions/OAuth data and an audit trigger rejecting this User's deletion event.
    /// Expect: lifecycle and every authentication row roll back; retry later revokes them successfully.
    @Test
    @DisplayName("rolls back session and OAuth revocation with a failed deletion audit")
    void shouldRestoreSessionStateWhenDeletionAuditFails() throws Exception {
        // Arrange
        ManagedUser user = this.createUser();
        Browser browser = this.login(user.username());
        this.assertAuthorized(browser, this.port());
        this.insertConsent(user.username());
        int authorizations = this.oauthCount(user.username());
        this.installAuditFailure(user.id());
        try {
            // Act + Assert
            assertThatThrownBy(() -> this.api().users().delete(user.id())).isInstanceOf(RuntimeException.class);
            assertThat(this.status(user.id())).isEqualTo("ACTIVE");
            assertThat(this.sessionCount(user.id())).isOne();
            assertThat(this.oauthCount(user.username())).isEqualTo(authorizations);
            assertThat(this.consentCount(user.username())).isOne();
            this.assertAuthorized(browser, this.port());
        } finally {
            this.removeAuditFailure();
        }
        this.api().users().delete(user.id());
        assertThat(this.sessionCount(user.id())).isZero();
        assertThat(this.oauthCount(user.username())).isZero();
        assertThat(this.consentCount(user.username())).isZero();
    }

    /// Verifies Worker cleanup can remove persisted servlet data without Web-owned deserialization.
    /// Given: an expired retained User with leftover sessions/OAuth state from before retention.
    /// Expect: the shared tombstone operation deletes all authentication rows and old cookies become anonymous.
    @Test
    @DisplayName("removes leftover browser sessions during Worker tombstoning")
    void shouldRevokeSessionWhenWorkerTombstonesExpiredUser() throws Exception {
        // Arrange
        this.setRetention("P1D");
        ManagedUser user = this.createUser();
        Browser browser = this.login(user.username());
        this.assertAuthorized(browser, this.port());
        this.insertConsent(user.username());
        Instant now = Instant.now();
        this.jdbc.update(
            "update users set status = 'RETAINED', retained_at = ? where id = ?",
            Timestamp.from(now.minus(Duration.ofDays(2))),
            user.id()
        );
        // Act
        this.retention.purgeExpiredUsers(now);
        // Assert
        assertThat(this.status(user.id())).isEqualTo("TOMBSTONE");
        assertThat(this.sessionCount(user.id())).isZero();
        assertThat(this.oauthCount(user.username())).isZero();
        assertThat(this.consentCount(user.username())).isZero();
        this.assertLoginRequired(browser, this.port());
    }

    /// Verifies old cookies cannot authenticate a new identity reusing a tombstoned username.
    /// Given: a logged-in User deleted with P0D, followed by a new User with the same username.
    /// Expect: the old browser still requires login while a fresh login belongs to the replacement UUID.
    @Test
    @DisplayName("does not attach old browser cookies to a replacement User")
    void shouldRejectOldCookieWhenUsernameIsReused() throws Exception {
        // Arrange
        this.setRetention("P0D");
        ManagedUser old = this.createUser();
        Browser browser = this.login(old.username());
        this.api().users().delete(old.id());
        UUID replacement = this.provisioning
            .reconcileUser(old.username(), "{noop}" + PASSWORD, Set.of(), "Replacement", "User", Set.of(), Set.of())
            .id();
        // Act + Assert
        this.assertLoginRequired(browser, this.port());
        Browser replacementBrowser = this.login(old.username());
        this.assertAuthorized(replacementBrowser, this.port());
        assertThat(this.sessionCount(old.id())).isZero();
        assertThat(this.sessionCount(replacement)).isOne();
    }

    /// Verifies persisted sessions preserve CSRF, login redirect, fixation protection and logout.
    /// Given: a saved authorization request followed by interactive login.
    /// Expect: login changes the session id and restores the request; logout requires CSRF and removes the session.
    @Test
    @DisplayName("preserves browser login and logout security behavior")
    void shouldPreserveSecurityBehaviorWhenUsingJdbcSessions() throws Exception {
        // Arrange
        ManagedUser user = this.createUser();
        Browser browser = this.login(user.username());
        String csrf = csrf(browser.get(this.port(), "/login").body());
        // Act + Assert
        assertThat(browser.post(this.port(), "/logout", "").statusCode()).isEqualTo(403);
        assertThat(browser.post(this.port(), "/logout", "_csrf=" + encode(csrf)).statusCode()).isEqualTo(302);
        assertThat(this.sessionCount(user.id())).isZero();
        this.assertLoginRequired(browser, this.port());
    }

    /// Verifies browser-approved consent reaches guarded persistence and is subsequently revoked.
    /// Given: a real authenticated browser and an OAuth client requiring consent to profile scope.
    /// Expect: the authorization code and consent are persisted, then DELETE removes both.
    @Test
    @DisplayName("persists and revokes consent approved by a real interactive User")
    void shouldRevokeConsentWhenBrowserApprovedUserIsDeleted() throws Exception {
        // Arrange
        ManagedUser user = this.createUser();
        String clientId = "consent-" + UUID.randomUUID();
        this.clients.save(
            RegisteredClient.withId(clientId)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret("{noop}" + clientId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .scope("profile")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .build()
        );
        Browser browser = this.login(user.username());
        HttpResponse<String> page = browser.get(
            this.port(),
            "/oauth2/authorize?response_type=code&client_id=" +
                clientId +
                "&redirect_uri=http%3A%2F%2Flocalhost%2Fcallback&scope=profile&state=consent-test" +
                PKCE
        );
        assertThat(page.statusCode()).isEqualTo(200);
        var state = Pattern.compile("name=\"state\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(state.find()).isTrue();
        // Act
        HttpResponse<String> approval = browser.post(
            this.port(),
            "/oauth2/authorize",
            "client_id=" + clientId + "&state=" + encode(Objects.requireNonNull(state.group(1))) + "&scope=profile"
        );
        // Assert
        assertThat(approval.statusCode()).isEqualTo(302);
        assertThat(approval.headers().firstValue("location").orElseThrow()).contains("code=");
        assertThat(this.consentCount(user.username())).isEqualTo(1);
        this.api().users().delete(user.id());
        assertThat(this.consentCount(user.username())).isZero();
        assertThat(this.oauthCount(user.username())).isZero();
        this.assertLoginRequired(browser, this.port());
    }

    private ManagedUser createUser() {
        String username = "session-" + UUID.randomUUID();
        UUID id = this.provisioning
            .reconcileUser(username, "{noop}" + PASSWORD, Set.of(), "Session", "User", Set.of(), Set.of())
            .id();
        return new ManagedUser(id, username);
    }

    /// Given: session persistence owns the User lock before DELETE starts.
    /// Expect: DELETE waits for the save transaction and removes its committed session.
    @Test
    @DisplayName("deletion removes a session whose save acquired the User lock first")
    void shouldRemoveSavedSessionWhenSaveWinsUserLock() throws Exception {
        // Arrange
        ManagedUser user = this.createUser();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PendingSession pending = pendingSession(this.sessions, user, new BlockingAttribute(locked, release));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Act
            var save = executor.submit(pending.save());
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> this.provisioning.deleteUser(user.username()));
            try {
                this.awaitBlockedUserTransaction();
            } finally {
                release.countDown();
            }
            save.get(20, TimeUnit.SECONDS);
            deletion.get(20, TimeUnit.SECONDS);
        }
        // Assert
        assertThat(this.sessions.findById(pending.id())).isNull();
        assertThat(this.sessionCount(user.id())).isZero();
    }

    /// Given: DELETE holds the User lock while a previously prepared authenticated session attempts a save.
    /// Expect: the save resumes after deletion commits and cannot recreate the session or its attributes.
    @Test
    @DisplayName("a stale authenticated save cannot recreate sessions after deletion wins the lock")
    void shouldRejectStaleSessionWhenDeletionWinsUserLock() throws Exception {
        // Arrange
        ManagedUser user = this.createUser();
        PendingSession pending = pendingSession(this.sessions, user, "ordinary attribute");
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Act
            var deletion = executor.submit(() ->
                new TransactionTemplate(this.transactions).executeWithoutResult(status -> {
                    this.provisioning.deleteUser(user.username());
                    deleted.countDown();
                    await(release);
                })
            );
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
            var save = executor.submit(pending.save());
            try {
                this.awaitBlockedUserTransaction();
            } finally {
                release.countDown();
            }
            deletion.get(20, TimeUnit.SECONDS);
            save.get(20, TimeUnit.SECONDS);
        }
        // Assert
        assertThat(this.sessions.findById(pending.id())).isNull();
        assertThat(this.sessionCount(user.id())).isZero();
        assertThat(this.orphanAttributeCount()).isZero();
    }

    private static <S extends Session> PendingSession pendingSession(
        SessionRepository<S> repository,
        ManagedUser user,
        Serializable attribute
    ) {
        S session = repository.createSession();
        var principal = new UserSessionPrincipal(
            user.id(),
            User.withUsername(user.username()).password(PASSWORD).roles("USER").build()
        );
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        );
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
        session.setAttribute("race-test", attribute);
        return new PendingSession(session.getId(), () -> repository.save(session));
    }

    private void awaitBlockedUserTransaction() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean blocked = this.jdbc.queryForObject(
                "select exists (select 1 from pg_stat_activity where wait_event_type = 'Lock' and query ilike '%users%')",
                Boolean.class
            );
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Competing transaction never blocked on the persisted User lock");
    }

    private static final class BlockingAttribute implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient CountDownLatch entered;
        private final transient CountDownLatch release;

        private BlockingAttribute(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Serial
        private void writeObject(ObjectOutputStream output) throws IOException {
            output.defaultWriteObject();
            this.entered.countDown();
            await(this.release);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for competing User transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record PendingSession(String id, Runnable save) {}

    private Browser login(String username) throws Exception {
        Browser browser = new Browser();
        assertThat(browser.get(this.port(), AUTHORIZE).headers().firstValue("location").orElseThrow()).contains(
            "/login"
        );
        String token = csrf(browser.get(this.port(), "/login").body());
        String beforeLogin = browser.sessionCookie();
        HttpResponse<String> response = browser.post(
            this.port(),
            "/login",
            "username=" + encode(username) + "&password=" + encode(PASSWORD) + "&_csrf=" + encode(token)
        );
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location").orElseThrow()).contains("/oauth2/authorize");
        assertThat(browser.sessionCookie()).isNotEqualTo(beforeLogin);
        return browser;
    }

    private void assertAuthorized(Browser browser, int port) throws Exception {
        HttpResponse<String> response = browser.get(port, AUTHORIZE);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location").orElseThrow()).contains("code=").doesNotContain("error=");
    }

    private void assertLoginRequired(Browser browser, int port) throws Exception {
        HttpResponse<String> response = browser.get(port, AUTHORIZE);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("location").orElseThrow()).contains("/login").doesNotContain("code=");
    }

    private void setRetention(String duration) {
        this.jdbc.update(
            "update application_configuration set configuration_value = ? where configuration_key = 'retention.user'",
            duration
        );
    }

    private int sessionCount(UUID id) {
        return this.count("select count(*) from spring_session where principal_name = ?", id.toString());
    }

    private int oauthCount(String username) {
        return this.count("select count(*) from oauth2_authorization where principal_name = ?", username);
    }

    private int consentCount(String username) {
        return this.count("select count(*) from oauth2_authorization_consent where principal_name = ?", username);
    }

    private int count(String sql, Object value) {
        return Objects.requireNonNull(this.jdbc.queryForObject(sql, Integer.class, value));
    }

    private String status(UUID id) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject("select status from users where id = ?", String.class, id)
        );
    }

    private int orphanAttributeCount() {
        return Objects.requireNonNull(
            this.jdbc.queryForObject(
                "select count(*) from spring_session_attributes a where not exists (select 1 from spring_session s where s.primary_id = a.session_primary_id)",
                Integer.class
            )
        );
    }

    private void insertConsent(String username) {
        this.jdbc.update(
            "insert into oauth2_authorization_consent (registered_client_id, principal_name, authorities) values ('session-test', ?, 'SCOPE_openid')",
            username
        );
    }

    private void installAuditFailure(UUID id) {
        this.jdbc.execute(
            """
            create or replace function fail_session_deletion_audit() returns trigger as $$
            begin
                if NEW.entity_id = '%s'::uuid then raise exception 'forced deletion audit failure'; end if;
                return NEW;
            end;
            $$ language plpgsql
            """.formatted(id)
        );
        this.jdbc.execute(
            "create trigger fail_session_deletion_audit before insert on audit_logs for each row execute function fail_session_deletion_audit()"
        );
    }

    private void removeAuditFailure() {
        this.jdbc.execute("drop trigger if exists fail_session_deletion_audit on audit_logs");
        this.jdbc.execute("drop function if exists fail_session_deletion_audit()");
    }

    private static String csrf(String html) {
        var matcher = CSRF.matcher(html);
        assertThat(matcher.find()).as("login page contains a CSRF token").isTrue();
        return Objects.requireNonNull(matcher.group(1));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ManagedUser(UUID id, String username) {}

    private static final class Browser {

        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(this.cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        private HttpResponse<String> get(int port, String path) throws Exception {
            return this.client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
        }

        private HttpResponse<String> post(int port, String path, String body) throws Exception {
            return this.client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
        }

        private String sessionCookie() {
            return this.cookies
                .getCookieStore()
                .getCookies()
                .stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(cookie -> cookie.getValue())
                .findFirst()
                .orElseThrow();
        }
    }
}
