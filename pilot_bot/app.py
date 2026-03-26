"""
app.py — Point d'entrée Flask. Webhook Telegram + endpoints utilitaires.
"""
from flask import Flask, request, jsonify
from flask_cors import CORS
import os, httpx
from database import test_connection
from admin_db import init_db, log_event
from admin_routes import admin_bp

import bot_handler

app = Flask(__name__)
CORS(app)

# Initialiser la base admin au démarrage
init_db()

# Enregistrer le blueprint admin
app.register_blueprint(admin_bp)

TELEGRAM_BOT_TOKEN = os.getenv('TELEGRAM_BOT_TOKEN', '')


# ── Vérification DB au démarrage ─────────────────────────────────────────────
if not test_connection():
    raise RuntimeError("Impossible de se connecter à lab_db. Arrêt.")


# ── Webhook Telegram ──────────────────────────────────────────────────────────
@app.route(f'/webhook/<token>', methods=['POST'])
def telegram_webhook(token):
    if token != TELEGRAM_BOT_TOKEN:
        return jsonify({'error': 'Unauthorized'}), 403

    data = request.json or {}
    message = data.get('message', {})
    if not message:
        return jsonify({'ok': True})

    chat_id  = str(message.get('chat', {}).get('id', ''))
    text     = message.get('text', '').strip()
    username = message.get('from', {}).get('first_name', '')

    if not chat_id or not text:
        return jsonify({'ok': True})

    from security import is_authorized
    if not is_authorized(chat_id):
        from admin_db import log_event as _log
        _log('access_denied', f'chat_id={chat_id}')

    replies = bot_handler.handle_message(chat_id, text, username)

    for reply in replies:
        _send(chat_id, reply)

    return jsonify({'ok': True})


# ── Santé ─────────────────────────────────────────────────────────────────────
@app.route('/health')
def health():
    return jsonify({'status': 'ok', 'service': 'pilot_bot'})


# ── Helpers ───────────────────────────────────────────────────────────────────
def _send(chat_id: str, text: str):
    if not TELEGRAM_BOT_TOKEN:
        return
    try:
        httpx.post(
            f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage",
            json={'chat_id': chat_id, 'text': text, 'parse_mode': 'HTML'},
            timeout=10,
        )
    except Exception as e:
        print(f"❌ Envoi Telegram {chat_id}: {e}")
        try:
            from admin_db import log_event as _log
            _log('send_error', f'chat_id={chat_id}, error={str(e)[:200]}')
        except Exception:
            pass


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=False)
