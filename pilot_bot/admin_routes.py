from flask import Blueprint, jsonify, request, send_from_directory
import os
from functools import wraps
from datetime import datetime, timezone
from admin_db import (
    get_authorized_users, add_authorized_user, remove_authorized_user,
    get_interactions, get_interactions_by_user, get_events, get_stats,
    log_event, get_authorized_chat_ids
)

ADMIN_SECRET = os.getenv('ADMIN_SECRET_KEY', 'change-me-admin-secret')
admin_bp = Blueprint('admin', __name__)


def require_admin_key(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        key = request.headers.get('X-Admin-Key') or request.args.get('key')
        if not key or key != ADMIN_SECRET:
            return jsonify({'error': 'Unauthorized'}), 401
        return f(*args, **kwargs)
    return decorated


@admin_bp.after_request
def add_cors_headers(response):
    if request.path.startswith('/admin/api/'):
        response.headers['Access-Control-Allow-Origin'] = '*'
        response.headers['Access-Control-Allow-Headers'] = 'Content-Type, X-Admin-Key'
        response.headers['Access-Control-Allow-Methods'] = 'GET, POST, DELETE, OPTIONS'
    return response


@admin_bp.route('/admin/')
def admin_index():
    static_dir = os.path.join(os.path.dirname(__file__), 'static/admin')
    return send_from_directory(static_dir, 'index.html')


@admin_bp.route('/admin/api/health')
@require_admin_key
def api_health():
    try:
        db_connected = True
        active_users = 0
        try:
            stats = get_stats()
            active_users = stats.get('active_users', 0)
        except Exception:
            db_connected = False

        telegram_token = os.getenv('TELEGRAM_BOT_TOKEN', '')
        base_url = os.getenv('BASE_URL', '')
        webhook_configured = bool(telegram_token and base_url)

        return jsonify({
            'status': 'ok',
            'db_connected': db_connected,
            'webhook_configured': webhook_configured,
            'active_users': active_users,
            'uptime_info': os.getenv('HOSTNAME', 'pilot_bot'),
            'timestamp': datetime.now(timezone.utc).isoformat()
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@admin_bp.route('/admin/api/stats')
@require_admin_key
def api_stats():
    try:
        return jsonify(get_stats())
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@admin_bp.route('/admin/api/users', methods=['GET'])
@require_admin_key
def api_get_users():
    try:
        return jsonify(get_authorized_users())
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@admin_bp.route('/admin/api/users', methods=['POST'])
@require_admin_key
def api_add_user():
    try:
        data = request.get_json() or {}
        chat_id = data.get('chat_id', '').strip()
        username = data.get('username', '')
        note = data.get('note', '')

        if not chat_id:
            return jsonify({'success': False, 'message': 'chat_id is required'}), 400
        if not chat_id.isdigit():
            return jsonify({'success': False, 'message': 'chat_id must be numeric'}), 400

        add_authorized_user(chat_id, username=username, note=note)
        log_event('user_added', f'chat_id={chat_id}, username={username}')

        return jsonify({'success': True, 'message': f'User {chat_id} added successfully'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@admin_bp.route('/admin/api/users/<chat_id>', methods=['DELETE'])
@require_admin_key
def api_remove_user(chat_id):
    try:
        remove_authorized_user(chat_id)
        log_event('user_removed', f'chat_id={chat_id}')
        return jsonify({'success': True, 'message': f'User {chat_id} removed successfully'})
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@admin_bp.route('/admin/api/interactions')
@require_admin_key
def api_interactions():
    try:
        limit = min(int(request.args.get('limit', 100)), 500)
        chat_id = request.args.get('chat_id')
        if chat_id:
            return jsonify(get_interactions_by_user(chat_id, limit=limit))
        return jsonify(get_interactions(limit=limit))
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@admin_bp.route('/admin/api/events')
@require_admin_key
def api_events():
    try:
        limit = min(int(request.args.get('limit', 50)), 200)
        return jsonify(get_events(limit))
    except Exception as e:
        return jsonify({'error': str(e)}), 500
