#!/bin/sh
set -e

echo "── Vérification connexion lab_db..."
python -c "
from database import test_connection
import sys
if not test_connection():
    print('ERREUR: Impossible de se connecter à lab_db.')
    sys.exit(1)
"

echo "── Initialisation base admin..."
python -c "
from admin_db import init_db
init_db()
print('Base admin initialisee.')
"

echo "── Configuration du webhook Telegram..."
python -c "
import os, httpx
token = os.getenv('TELEGRAM_BOT_TOKEN', '')
base_url = os.getenv('BASE_URL', '').rstrip('/')
if token and base_url:
    webhook_url = f'{base_url}/webhook/{token}'
    resp = httpx.post(
        f'https://api.telegram.org/bot{token}/setWebhook',
        json={'url': webhook_url},
        timeout=10
    )
    print(f'Webhook : {webhook_url}')
    print(f'Réponse : {resp.json()}')
else:
    print('AVERTISSEMENT: TELEGRAM_BOT_TOKEN ou BASE_URL manquant — mode polling désactivé')
"

echo "── Démarrage Gunicorn (port 5001)..."
exec gunicorn \
    --bind 0.0.0.0:5001 \
    --workers 2 \
    --timeout 60 \
    --access-logfile - \
    --error-logfile - \
    app:app
