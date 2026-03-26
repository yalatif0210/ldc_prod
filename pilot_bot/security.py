"""
security.py — Whitelist des chat_id autorisés.
Seuls les chefs de projet explicitement listés peuvent utiliser ce bot.
"""
import os
import functools

UNAUTHORIZED_MSG = (
    "🔒 <b>Accès refusé.</b>\n\n"
    "Ce bot est réservé aux chefs de projet LDC.\n\n"
    "Votre identifiant Telegram : <code>{chat_id}</code>\n\n"
    "<i>Transmettez cet identifiant à l'administrateur pour demander l'accès.</i>"
)


def _load_whitelist() -> set:
    """Charge depuis admin_db (priorité) puis depuis env var (fallback/seed)."""
    try:
        from admin_db import get_authorized_chat_ids
        db_ids = get_authorized_chat_ids()
        if db_ids:
            return db_ids
    except Exception:
        pass
    # Fallback : env var
    raw = os.getenv('AUTHORIZED_CHAT_IDS', '')
    return {cid.strip() for cid in raw.split(',') if cid.strip()}


# Rechargeable sans redémarrage si besoin
AUTHORIZED_CHAT_IDS: set = _load_whitelist()


def reload_whitelist() -> None:
    """Recharge la whitelist depuis la DB (appelé après add/remove via admin)."""
    global AUTHORIZED_CHAT_IDS
    AUTHORIZED_CHAT_IDS = _load_whitelist()


def is_authorized(chat_id: str) -> bool:
    """Vérifie en temps réel dans la DB admin (sans cache)."""
    try:
        from admin_db import get_authorized_chat_ids
        return chat_id in get_authorized_chat_ids()
    except Exception:
        # Fallback sur le set en mémoire si la DB est inaccessible
        return chat_id in AUTHORIZED_CHAT_IDS


def require_authorized(func):
    """Décorateur — bloque tout message d'un chat_id non whitelisté."""
    @functools.wraps(func)
    def wrapper(chat_id: str, *args, **kwargs):
        if not is_authorized(chat_id):
            return [UNAUTHORIZED_MSG.format(chat_id=chat_id)]
        return func(chat_id, *args, **kwargs)
    return wrapper
