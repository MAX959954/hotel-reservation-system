#!/usr/bin/env bash
# Daily PostgreSQL backup, keeps the last 14. Install once on the server:
#   (crontab -l 2>/dev/null; echo "30 3 * * * bash $(pwd)/deploy/backup.sh >> /var/log/folio-backup.log 2>&1") | crontab -
# Restore:  gunzip -c <file>.sql.gz | docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec -T postgres psql -U hotel_user -d hotel_db
set -euo pipefail
cd "$(dirname "$0")/.."

BACKUP_DIR=${BACKUP_DIR:-/var/backups/folio}
mkdir -p "$BACKUP_DIR"
set -a; . deploy/.env; set +a

FILE="$BACKUP_DIR/folio-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec -T postgres \
  pg_dump -U "${POSTGRES_USER:-hotel_user}" -d "${POSTGRES_DB:-hotel_db}" | gzip > "$FILE"
echo "$(date -Is) backup written: $FILE"

ls -1t "$BACKUP_DIR"/folio-*.sql.gz | tail -n +15 | xargs -r rm --
