#!/bin/bash
set -euo pipefail

if [ ! -f .env ]; then
  echo "Erreur : fichier .env introuvable."; exit 1
fi
export $(grep -v '^#' .env | grep -v '^$' | xargs)

DOMAIN="${DOMAIN:?'Variable DOMAIN manquante dans .env'}"
EMAIL="${LETSENCRYPT_EMAIL:?'Variable LETSENCRYPT_EMAIL manquante dans .env'}"

echo "Domaine : $DOMAIN  |  Email : $EMAIL"

# ── 1. Volume ────────────────────────────────────────────────────────────────
docker volume create certbot_certs 2>/dev/null || true

# ── 2. Stopper temporairement le conteneur qui occupe le port 80 ─────────────
PORT80=$(docker ps --format "{{.Names}}" --filter "publish=80" | head -1)
if [ -n "$PORT80" ]; then
  echo "Arrêt temporaire de '$PORT80' (port 80 nécessaire pour Let's Encrypt)..."
  docker stop "$PORT80"
fi

# ── 3. Émettre le certificat (HTTP-01, port 80) ───────────────────────────────
echo "Émission du certificat pour $DOMAIN..."
docker run --rm \
  -v certbot_certs:/etc/letsencrypt \
  -p 80:80 \
  certbot/certbot:latest certonly \
    --standalone \
    --email "$EMAIL" \
    --domain "$DOMAIN" \
    --rsa-key-size 4096 \
    --agree-tos \
    --no-eff-email \
    --keep-until-expiring

# ── 4. Redémarrer le conteneur stoppé ────────────────────────────────────────
if [ -n "$PORT80" ]; then
  echo "Redémarrage de '$PORT80'..."
  docker start "$PORT80"
fi

# ── 5. Démarrer la stack ─────────────────────────────────────────────────────
echo "Démarrage de la stack..."
docker compose up -d

echo ""
echo "✓  HTTPS actif sur https://$DOMAIN"
echo ""
echo "RENOUVELLEMENT — ajouter dans crontab -e :"
echo "0 3 1 * * cd $(pwd) && CONTAINER=\$(docker ps --format '{{.Names}}' --filter 'publish=80' | head -1) && [ -n \"\$CONTAINER\" ] && docker stop \"\$CONTAINER\"; docker run --rm -v certbot_certs:/etc/letsencrypt -p 80:80 certbot/certbot renew --standalone --quiet; [ -n \"\$CONTAINER\" ] && docker start \"\$CONTAINER\"; docker exec ldc_frontend nginx -s reload"
