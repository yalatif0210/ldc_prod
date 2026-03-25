#!/bin/bash
# ─────────────────────────────────────────────────────────────
# deploy-local.sh — Rebuild et redémarre les services Docker locaux
#
# Usage :
#   ./deploy-local.sh              → rebuild tous les services
#   ./deploy-local.sh backend      → rebuild ldc_backend (cache Docker)
#   ./deploy-local.sh backend-clean → rebuild ldc_backend SANS cache (après changement pom.xml)
#   ./deploy-local.sh frontend     → rebuild uniquement ldc_frontend
#   ./deploy-local.sh watch        → mode watch (rebuild auto sur changements)
#   ./deploy-local.sh logs         → affiche les logs backend en direct
# ─────────────────────────────────────────────────────────────

set -e

# ── Garde le terminal ouvert en cas d'erreur ─────────────────
trap 'echo ""; echo -e "\033[0;31m>>> ERREUR ligne $LINENO — script interrompu.\033[0m"; read -rp "Appuie sur Entrée pour fermer..." _' ERR


COMPOSE="docker compose -f docker-compose.yml -f docker-compose.local.yml"

# ── Couleurs ──────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# ── Attente santé backend ────────────────────────────────────
wait_backend_healthy() {
  echo ""
  echo -e "${YELLOW}>>> Attente du démarrage backend (max 90s)...${NC}"
  local max=18
  local i=0
  while [ $i -lt $max ]; do
    if docker inspect --format='{{.State.Health.Status}}' ldc_backend 2>/dev/null | grep -q "healthy"; then
      echo -e "${GREEN}>>> Backend HEALTHY ✓${NC}"
      return 0
    fi
    # Fallback : toute réponse HTTP (200 ou 403) = serveur démarré
    if docker exec ldc_backend wget --server-response -O /dev/null http://localhost:8080/actuator/health 2>&1 | grep -q "HTTP/"; then
      echo -e "${GREEN}>>> Backend UP (actuator) ✓${NC}"
      return 0
    fi
    sleep 5
    i=$((i+1))
    echo "   ... attente ($((i*5))s)"
  done
  echo -e "${RED}>>> Backend non disponible après 90s — vérifier les logs :${NC}"
  $COMPOSE logs --tail=40 ldc_backend
  return 1
}

case "${1:-}" in

  backend)
    echo ">>> Rebuild ldc_backend (avec cache Docker)..."
    echo "    Note: pom.xml modifié → la couche Maven sera reconstruite automatiquement"
    $COMPOSE --progress=plain build ldc_backend
    $COMPOSE --progress=plain up -d ldc_backend
    wait_backend_healthy
    ;;

  backend-clean)
    echo ">>> Rebuild ldc_backend SANS cache (force re-téléchargement des dépendances Maven)..."
    $COMPOSE --progress=plain build --no-cache ldc_backend
    $COMPOSE --progress=plain up -d ldc_backend
    wait_backend_healthy
    ;;

  frontend)
    echo ">>> Rebuild ldc_frontend..."
    $COMPOSE --progress=plain build ldc_frontend
    $COMPOSE --progress=plain up -d ldc_frontend
    ;;

  watch)
    echo ">>> Mode watch — rebuild automatique sur changements de fichiers..."
    $COMPOSE watch
    ;;

  logs)
    echo ">>> Logs backend en direct (Ctrl+C pour quitter)..."
    $COMPOSE logs -f ldc_backend
    ;;

  "")
    echo ">>> Rebuild complet de tous les services..."
    $COMPOSE --progress=plain build
    $COMPOSE --progress=plain up -d
    wait_backend_healthy
    ;;

  *)
    echo "Usage: $0 [backend|backend-clean|frontend|watch|logs]"
    exit 1
    ;;

esac

echo ""
echo ">>> Statut des conteneurs :"
$COMPOSE ps

read -rp "Appuie sur Entrée pour fermer..."
