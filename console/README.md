# Taskmigo Console

Một ứng dụng Spring Boot “2 trong 1”: vừa là OAuth 2.1/OpenID Connect Authorization Server phát JWT, vừa là Resource Server bảo vệ API `/api/**`.

## Khởi động bằng Docker Compose

Ứng dụng dùng PostgreSQL 18 cho user, authority, OAuth client, authorization, consent và signing key; không còn repository in-memory. Các migration Flyway tự chạy khi service khởi động.

```bash
cd console
export DATABASE_PASSWORD='a-strong-database-password'
export BOOTSTRAP_PASSWORD='a-strong-login-password'
docker compose up --build
```

Compose tạo volume `postgres-data`, vì vậy dữ liệu OAuth và user vẫn tồn tại sau khi container được tạo lại. User bootstrap mặc định là `developer`; username có thể đổi qua `BOOTSTRAP_USERNAME`. Không commit các password vào repository.

Để chạy application trực tiếp trong khi chỉ chạy PostgreSQL bằng Compose:

```bash
docker compose up -d postgres
export DATABASE_URL='jdbc:postgresql://localhost:5432/taskmigo'
export DATABASE_USERNAME='taskmigo'
export DATABASE_PASSWORD='a-strong-database-password'
export BOOTSTRAP_PASSWORD='a-strong-login-password'
./gradlew bootRun
```

Production nên thay bảng signing key bằng KMS/HSM; implementation hiện tại lưu JWK bền vững trong PostgreSQL để hỗ trợ restart và nhiều replica, thay vì sinh key mới trong memory.

## Lấy access token bằng Authorization Code + PKCE

Client public `taskmigo-browser` không có secret và bắt buộc PKCE. Tạo verifier/challenge:

```bash
VERIFIER=$(openssl rand -base64 64 | tr -d '=+/' | cut -c1-64)
CHALLENGE=$(printf %s "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
echo "$VERIFIER"
```

Mở URL sau trong trình duyệt, đăng nhập bằng `developer`, chấp thuận scope, rồi lấy tham số `code` từ URL callback (callback không cần chạy để copy URL):

```text
http://localhost:9000/oauth2/authorize?response_type=code&client_id=taskmigo-browser&redirect_uri=http://127.0.0.1:8080/callback&scope=openid%20profile%20api.read&code_challenge=CHALLENGE_VALUE&code_challenge_method=S256
```

Thay `CHALLENGE_VALUE`, sau đó đổi code lấy token:

```bash
curl -sS -X POST http://localhost:9000/oauth2/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=authorization_code \
  --data-urlencode client_id=taskmigo-browser \
  --data-urlencode redirect_uri=http://127.0.0.1:8080/callback \
  --data-urlencode code="$CODE" \
  --data-urlencode code_verifier="$VERIFIER"
```

## Gọi API

```bash
curl http://localhost:9000/api/public
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:9000/api/me
```

Để gọi `/api/admin`, yêu cầu thêm scope `api.admin` trong bước authorize và dùng access token nhận được:

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:9000/api/admin
```

Metadata OIDC ở `http://localhost:9000/.well-known/openid-configuration`; JWK Set URI được công bố trong metadata.

## Kiểm thử

```bash
./gradlew test
./gradlew build
```
