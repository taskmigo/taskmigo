# Taskmigo

Repository Taskmigo chứa các service và hạ tầng dùng chung. Hiện tại repository cung cấp service [Taskmigo Console](console/README.md), vừa là OAuth 2.1/OpenID Connect Authorization Server, vừa là Resource Server bảo vệ `/api/**`.

## Cấu trúc repository

- `console/`: service xác thực và API Console; xem [tài liệu riêng của Console](console/README.md).
- `compose.yaml`: orchestration dùng chung ở repository root, sẵn sàng bổ sung các service khác.

## Khởi động stack bằng Docker Compose

Ứng dụng dùng PostgreSQL 18 cho user, authority, OAuth client, authorization, consent và signing key; không còn repository in-memory. Các migration Flyway tự chạy khi service khởi động.

```bash
export DATABASE_PASSWORD='a-strong-database-password'
export BOOTSTRAP_PASSWORD='a-strong-login-password'
docker compose up --build
```

Compose tạo volume `postgres-data`, vì vậy dữ liệu OAuth và user vẫn tồn tại sau khi container được tạo lại. User bootstrap mặc định là `developer`; username có thể đổi qua `BOOTSTRAP_USERNAME`. Không commit các password vào repository.

Hướng dẫn chạy riêng module Console và các chi tiết OAuth nằm trong [console/README.md](console/README.md). Nếu chỉ cần PostgreSQL:

```bash
export DATABASE_PASSWORD='a-strong-database-password'
docker compose up -d postgres
```

Production nên thay bảng signing key bằng KMS/HSM; implementation hiện tại lưu JWK bền vững trong PostgreSQL để hỗ trợ restart và nhiều replica, thay vì sinh key mới trong memory.
