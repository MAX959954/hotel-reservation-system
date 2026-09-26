#!/usr/bin/env bash
# Build and (re)start the production stack. Safe to run repeatedly - this is also what
# the GitHub Actions deploy job runs on every push to main.
#   bash deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE="docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env"

if [ ! -f deploy/.env ]; then
  echo "deploy/.env is missing - copy deploy/.env.example and fill it in." >&2
  exit 1
fi

if [ -d .git ]; then
  echo "==> Pulling latest code"
  git pull --ff-only
fi

echo "==> Building and starting containers"
$COMPOSE up -d --build --remove-orphans

echo "==> Waiting for the API to become healthy"
DOMAIN=$(grep -E '^DOMAIN=' deploy/.env | cut -d= -f2-)
for i in $(seq 1 60); do
  if $COMPOSE exec -T app curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "    API is up."
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "API did not become healthy. Last log lines:" >&2
    $COMPOSE logs --tail 80 app >&2
    exit 1
  fi
  sleep 5
done

echo "==> Removing old images"
docker image prune -f >/dev/null

echo
echo "Deployed: https://${DOMAIN}"
