# Taskmigo Console

## Quick start

From the repository root:

```bash
bash examples/docker-compose.sh up --build
```

The sample credentials are for local development only. It creates:

- Browser client: `taskmigo-browser` / `browser-secret`
- Worker client: `taskmigo-worker` / `worker-secret`
- Bootstrap user: `developer` / `developer-password`

Use the wrapper for the sample rather than invoking `docker compose --env-file` directly. Docker
Compose gives existing exported shell variables higher precedence than the env file; the wrapper
removes those conflicts so the listed credentials are deterministic.

To intentionally reset all local sample data and credentials:

```bash
bash examples/docker-compose.sh down --volumes
```

Obtain a worker access token with `curl` and `jq`:

```bash
ACCESS_TOKEN="$(
  curl --silent \
    --user 'taskmigo-worker:worker-secret' \
    --data 'grant_type=client_credentials' \
    --data 'scope=api.read api.admin' \
    http://localhost:9000/oauth2/token |
  jq --raw-output '.access_token'
)"

curl http://localhost:9000/api/public
curl --header "Authorization: Bearer $ACCESS_TOKEN" http://localhost:9000/api/me
curl --header "Authorization: Bearer $ACCESS_TOKEN" http://localhost:9000/api/admin
```

## Run without Compose

Java 26 and PostgreSQL 18 are required. Supply the datasource, bootstrap password and at least one
OAuth client through normal Spring environment configuration, then run:

```bash
./gradlew bootRun
```

## Build

```bash
./gradlew test
./gradlew build
```

See the [repository documentation index](../README.md) and
[architecture overview](../docs/Architecture.md).
