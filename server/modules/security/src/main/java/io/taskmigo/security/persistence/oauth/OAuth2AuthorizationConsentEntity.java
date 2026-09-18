package io.taskmigo.security.persistence.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "oauth2_authorization_consent")
@IdClass(OAuth2AuthorizationConsentEntity.ConsentId.class)
@SuppressWarnings("NotNullFieldNotInitialized")
final class OAuth2AuthorizationConsentEntity {

    @Id
    @Column(name = "registered_client_id", length = 100)
    String registeredClientId;

    @Id
    @Column(name = "principal_name", length = 200)
    String principalName;

    @Column(nullable = false, length = 1000)
    String authorities;

    protected OAuth2AuthorizationConsentEntity() {}

    static final class ConsentId implements Serializable {

        private static final long serialVersionUID = 1L;

        @Nullable
        String registeredClientId;

        @Nullable
        String principalName;

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConsentId id)) {
                return false;
            }
            return (
                Objects.equals(this.registeredClientId, id.registeredClientId) &&
                Objects.equals(this.principalName, id.principalName)
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.registeredClientId, this.principalName);
        }
    }
}
