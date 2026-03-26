"""
session.py — Gestion des sessions utilisateur en mémoire.

Chaque session est indexée par chat_id (str) et stocke :
  - menu  : str   — identifiant du menu actif (ex: 'reports', 'stocks')
  - data  : dict  — données contextuelles propres au menu (ex: liste de périodes)
  - ts    : float — timestamp Unix de la dernière activité (pour TTL)

Ce module est importé à la fois par bot_handler.py et les modules de menus,
ce qui évite tout import circulaire entre eux.
"""
import time

# Durée de vie d'une session inactive (30 minutes)
SESSION_TTL: int = 1800

# Stockage en mémoire : {chat_id: {'menu': str, 'data': dict, 'ts': float}}
_sessions: dict = {}


def get_session(chat_id: str) -> dict | None:
    """
    Retourne la session active pour ce chat_id, ou None si absente/expirée.
    Une session expirée est supprimée au passage.
    """
    sess = _sessions.get(chat_id)
    if not sess:
        return None
    if time.time() - sess['ts'] > SESSION_TTL:
        # Session trop ancienne → on la supprime
        _sessions.pop(chat_id, None)
        return None
    return sess


def set_session(chat_id: str, menu: str, data: dict | None = None) -> None:
    """
    Crée ou met à jour la session pour ce chat_id.
    Le timestamp est rafraîchi à chaque appel.

    Args:
        chat_id : identifiant Telegram de la conversation.
        menu    : clé du menu actif (ex: 'main', 'reports', 'stocks').
        data    : données contextuelles optionnelles à stocker dans la session.
    """
    _sessions[chat_id] = {
        'menu': menu,
        'data': data if data is not None else {},
        'ts':   time.time(),
    }


def clear_session(chat_id: str) -> None:
    """Supprime la session de ce chat_id (réinitialisation complète)."""
    _sessions.pop(chat_id, None)
