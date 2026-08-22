#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly REPOSITORY_ROOT="$(dirname -- "$SCRIPT_DIRECTORY")"
readonly COMPOSE_PROJECT_NAME="taskmigo-e2e"
readonly WEB_PORT="${TASKMIGO_WEB_PORT:-18080}"
readonly EXPECTED_PERMISSIONS='["project.members.manage", "project.members.read", "project.read", "project.update"]'

export COMPOSE_PROJECT_NAME
export TASKMIGO_WEB_PORT="$WEB_PORT"

cd "$REPOSITORY_ROOT"

cleanup() {
    local exit_code=$?
    if ((exit_code != 0)); then
        docker compose --profile application logs --no-color || true
    fi
    docker compose --profile application down --volumes --remove-orphans || true
    exit "$exit_code"
}

trap cleanup EXIT

docker compose --profile application down --volumes --remove-orphans
docker compose --profile application up --build --detach --wait --wait-timeout 180

token_response=""
for attempt in {1..60}; do
    if token_response=$(curl --fail --silent \
        --user taskmigo-cli:taskmigo-dev-secret \
        --data grant_type=client_credentials \
        --data scope=taskmigo.api \
        "http://localhost:$WEB_PORT/oauth2/token"); then
        break
    fi
    sleep 1
done

token=$(jq --exit-status --raw-output '.access_token' <<<"$token_response")
curl --fail --silent --show-error \
    --header "Authorization: Bearer $token" \
    "http://localhost:$WEB_PORT/api/v0/permissions" \
    | jq --exit-status --argjson expected "$EXPECTED_PERMISSIONS" 'sort == $expected'

test "$(docker compose --profile application exec --no-TTY web id --user)" = "10001"
test "$(docker compose --profile application exec --no-TTY worker id --user)" = "10001"
docker compose --profile application logs --no-color worker | grep --quiet "Started TaskmigoWorkerApplication"

migration_count=$(docker compose exec --no-TTY postgres \
    psql --username taskmigo --dbname taskmigo --tuples-only --no-align \
    --command "SELECT COUNT(*) FROM flyway_schema_history WHERE success")
test "$migration_count" = "1"
