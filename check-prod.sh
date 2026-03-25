#!/bin/bash
# Vérifie que tous les pré-requis sont en place avant docker compose up en production
set -euo pipefail

ERRORS=0

check() {
  local label="$1"
  local cmd="$2"
  if eval "$cmd" &>/dev/null; then
    echo "  ✔ $label"
  else
    echo "  ✘ $label"
    ERRORS=$((ERRORS + 1))
  fi
}

echo ""
echo "=== Vérification pré-déploiement ==="
echo ""

echo "── Réseaux Docker ──"
check "shared_db_net existe" "docker network inspect shared_db_net"

echo ""
echo "── Volumes Docker ──"
check "certbot_certs existe" "docker volume inspect certbot_certs"

echo ""
echo "── Variables .env ──"
source .env 2>/dev/null || true
check "DOMAIN défini"                  "[ -n '${DOMAIN:-}' ]"
check "POSTGRES_PASSWORD défini"       "[ -n '${POSTGRES_PASSWORD:-}' ]"
check "JWT_SECRET défini"              "[ -n '${JWT_SECRET:-}' ]"
check "GRAFANA_ADMIN_PASSWORD défini"  "[ -n '${GRAFANA_ADMIN_PASSWORD:-}' ]"

echo ""
echo "── Certificats SSL ──"
check "Certificat Let's Encrypt présent" \
  "docker run --rm -v certbot_certs:/etc/letsencrypt alpine ls /etc/letsencrypt/live/"

echo ""
if [ "$ERRORS" -eq 0 ]; then
  echo "✔ Tout est prêt — tu peux lancer : docker compose up -d --build"
else
  echo "✘ $ERRORS problème(s) à corriger avant le déploiement."
  echo ""
  echo "Commandes de correction rapide :"
  echo "  docker network create shared_db_net"
  echo "  ./init-letsencrypt.sh   # pour certbot_certs"
fi
echo ""
