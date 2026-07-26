#!/usr/bin/env bash
set -euo pipefail

sample_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$sample_directory/.." && pwd)"

# Compose gives exported shell variables precedence over --env-file. Remove every variable used
# by the sample so its documented local credentials are deterministic.
unset COMPOSE_PROJECT_NAME
unset DATABASE_PASSWORD
unset POSTGRES_PORT
unset BOOTSTRAP_USERNAME
unset BOOTSTRAP_PASSWORD
unset SPRING_APPLICATION_JSON
unset SPRING_SECURITY_OAUTH2_AUTHORIZATIONSERVER_ISSUER
unset SERVER_PORT

cd "$repository_root"
exec docker compose --env-file "$sample_directory/docker-compose.env" "$@"
