# Taskmigo Console

`console` là service Spring Boot “2 trong 1” của Taskmigo:

- OAuth 2.1/OpenID Connect Authorization Server.
- JWT Resource Server bảo vệ `/api/**`.
- PostgreSQL persistence cho user, authority, OAuth client, authorization, consent và signing key.

Việc orchestration toàn bộ repository, PostgreSQL và các biến môi trường dùng chung được mô tả trong [README ở repository root](../README.md).

## Chạy module trực tiếp

Khởi động PostgreSQL từ repository root trước:

```bash
export DATABASE_PASSWORD='a-strong-database-password'
export BOOTSTRAP_PASSWORD='a-strong-login-password'
docker compose up -d postgres
```

Sau đó chạy Console:

```bash
cd console
export DATABASE_URL='jdbc:postgresql://localhost:5432/taskmigo'
export DATABASE_USERNAME='taskmigo'
export DATABASE_PASSWORD='a-strong-database-password'
export BOOTSTRAP_PASSWORD='a-strong-login-password'
./gradlew bootRun
```

Flyway tự áp dụng migrations trong `src/main/resources/db/migration`. Production nên thay database-backed signing-key provider bằng KMS hoặc HSM.

## OAuth client development

Public client `taskmigo-browser` sử dụng Authorization Code và bắt buộc PKCE. Redirect URI mặc định là `http://127.0.0.1:8080/callback`; các scope gồm `openid`, `profile`, `api.read` và `api.admin`.

Metadata OIDC: `http://localhost:9000/.well-known/openid-configuration`.

## API mẫu

```bash
curl http://localhost:9000/api/public
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:9000/api/me
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:9000/api/admin
```

`/api/me` yêu cầu `api.read`; `/api/admin` yêu cầu `api.admin`.

## Build và test

Project yêu cầu Java 26 và cung cấp Gradle Wrapper:

```bash
./gradlew test
./gradlew build
```
