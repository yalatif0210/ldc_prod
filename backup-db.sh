#!/bin/bash
set -euo pipefail

# ── Installation du cron (si appelé avec --install) ───────────────────────────
if [ "${1:-}" = "--install" ]; then
  SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
  CRON_JOB="0 2 * * * ${SCRIPT_PATH} >> $(dirname "${SCRIPT_PATH}")/backups/cron.log 2>&1"
  # Ajouter uniquement si pas déjà présent
  if crontab -l 2>/dev/null | grep -qF "${SCRIPT_PATH}"; then
    echo "Cron déjà installé."
  else
    (crontab -l 2>/dev/null; echo "${CRON_JOB}") | crontab -
    echo "Cron installé — backup tous les jours à 2h00."
    echo "Vérification : crontab -l"
  fi
  exit 0
fi

# ── Config ────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backups"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

# Charger les variables d'environnement
if [ -f "${SCRIPT_DIR}/.env" ]; then
  set -a
  source "${SCRIPT_DIR}/.env"
  set +a
fi

DB_NAME="${POSTGRES_DB:-appdb}"
DB_USER="${POSTGRES_USER:-appuser}"
BACKUP_FILE="${BACKUP_DIR}/backup_${DB_NAME}_${TIMESTAMP}.sql.gz"

# ── Backup ────────────────────────────────────────────────────────────────────
mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Démarrage du backup → ${BACKUP_FILE}"

docker exec lab_db pg_dump -U "${DB_USER}" -d "${DB_NAME}" --no-password \
  | gzip > "${BACKUP_FILE}"

SIZE=$(du -sh "${BACKUP_FILE}" | cut -f1)
echo "[$(date)] Backup terminé — taille : ${SIZE}"
echo "Fichier : ${BACKUP_FILE}"

# ── Nettoyage : garder les 10 derniers backups ────────────────────────────────
KEPT=10
COUNT=$(ls -1 "${BACKUP_DIR}"/backup_*.sql.gz 2>/dev/null | wc -l)
if [ "${COUNT}" -gt "${KEPT}" ]; then
  TO_DELETE=$(( COUNT - KEPT ))
  echo "Suppression des ${TO_DELETE} backup(s) les plus anciens..."
  ls -1t "${BACKUP_DIR}"/backup_*.sql.gz | tail -n "${TO_DELETE}" | xargs rm -f
fi
