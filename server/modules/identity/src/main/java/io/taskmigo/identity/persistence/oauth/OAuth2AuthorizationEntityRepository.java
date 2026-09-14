package io.taskmigo.identity.persistence.oauth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface OAuth2AuthorizationEntityRepository extends JpaRepository<OAuth2AuthorizationEntity, String> {
    Optional<OAuth2AuthorizationEntity> findByState(String state);

    Optional<OAuth2AuthorizationEntity> findByAuthorizationCodeValue(String value);

    Optional<OAuth2AuthorizationEntity> findByAccessTokenValue(String value);

    Optional<OAuth2AuthorizationEntity> findByRefreshTokenValue(String value);

    Optional<OAuth2AuthorizationEntity> findByOidcIdTokenValue(String value);

    Optional<OAuth2AuthorizationEntity> findByUserCodeValue(String value);

    Optional<OAuth2AuthorizationEntity> findByDeviceCodeValue(String value);
}
