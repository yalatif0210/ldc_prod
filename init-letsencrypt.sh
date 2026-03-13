#!/bin/bash
# =============================================================================
# init-letsencrypt.sh — Bootstrap HTTPS avec Let's Encrypt (standalone port 443)
#
# À exécuter UNE SEULE FOIS sur le VPS, avant docker compose up -d.
#
# Prérequis :
#   - Le DNS du domaine pointe vers l'IP de ce VPS
#   - Le port 443 est libre au moment de l'exécution (nginx pas encore démarré)
#   - DOMAIN et LETSENCRYPT_EMAIL sont renseignés dans .env
#   - Docker est installé
# =============================================================================
set -euo pipefail

if [ ! -f .env ]; then
  echo "Erreur : fichier .env introuvable. Lancez ce script depuis la racine du projet."
  exit 1
fi
export $(grep -v '^#' .env | grep -v '^$' | xargs)

DOMAIN="${DOMAIN:?'Variable DOMAIN manquante dans .env'}"
EMAIL="${LETSENCRYPT_EMAIL:?'Variable LETSENCRYPT_EMAIL manquante dans .env'}"

echo "=================================================="
echo " Domaine : $DOMAIN"
echo " Email   : $EMAIL"
echo "=================================================="
echo ""

# ── 1. Créer le volume des certificats ───────────────────────────────────────
echo "[1/3] Création du volume certbot_certs..."
docker volume create certbot_certs 2>/dev/null || true

# ── 2. Émettre le certificat via certbot standalone (TLS-ALPN-01, port 443) ──
#       nginx n'est PAS encore démarré → port 443 disponible
echo "[2/3] Émission du certificat Let's Encrypt pour $DOMAIN (port 443)..."
docker run --rm \
  -v certbot_certs:/etc/letsencrypt \
  -p 443:443 \
  certbot/certbot:latest certonly \
    --standalone \
    --preferred-challenges tls-alpn-01 \
    --email "$EMAIL" \
    --domain "$DOMAIN" \
    --rsa-key-size 4096 \
    --agree-tos \
    --no-eff-email

# ── 3. Démarrer la stack complète ────────────────────────────────────────────
echo "[3/3] Démarrage de la stack..."
docker compose up -d

echo ""
echo "=================================================="
echo " ✓  HTTPS actif sur https://$DOMAIN"
echo "=================================================="
echo ""
echo "RENOUVELLEMENT (à ajouter dans crontab -e sur le VPS) :"
echo "─────────────────────────────────────────────────────"
echo "# Renouvellement mensuel : arrêt nginx → certbot standalone → redémarrage"
echo "0 3 1 * * docker stop frontend && \\"
echo "         docker run --rm -v certbot_certs:/etc/letsencrypt -p 443:443 \\"
echo "         certbot/certbot renew --standalone --quiet && \\"
echo "         docker start frontend"
echo ""
