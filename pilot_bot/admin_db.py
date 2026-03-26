"""
admin_db.py — Base SQLite dédiée à l'administration du pilot_bot.
Module entièrement autonome (dépendances : stdlib uniquement).
"""

import os
import sqlite3
from datetime import datetime, timezone

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DB_PATH: str = os.environ.get("ADMIN_DB_PATH", "/app/data/admin.db")

# ---------------------------------------------------------------------------
# Initialisation
# ---------------------------------------------------------------------------


def init_db() -> None:
    """Crée le répertoire parent, les tables, importe le seed initial et logue le démarrage."""
    parent_dir = os.path.dirname(DB_PATH)
    if parent_dir:
        os.makedirs(parent_dir, exist_ok=True)

    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS authorized_users (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id    TEXT UNIQUE NOT NULL,
                username   TEXT,
                note       TEXT,
                added_at   TEXT,
                is_active  INTEGER DEFAULT 1
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS interaction_logs (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_id         TEXT NOT NULL,
                username        TEXT,
                command         TEXT NOT NULL,
                menu            TEXT,
                response_count  INTEGER DEFAULT 1,
                timestamp       TEXT NOT NULL
            )
        """)

        cursor.execute("""
            CREATE TABLE IF NOT EXISTS bot_events (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                event_type  TEXT NOT NULL,
                details     TEXT,
                timestamp   TEXT NOT NULL
            )
        """)

        conn.commit()

    # Seed depuis la variable d'environnement AUTHORIZED_CHAT_IDS
    raw_ids = os.environ.get("AUTHORIZED_CHAT_IDS", "")
    if raw_ids:
        now = _utcnow()
        chat_ids = [cid.strip() for cid in raw_ids.split(",") if cid.strip()]
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            for cid in chat_ids:
                cursor.execute(
                    "INSERT OR IGNORE INTO authorized_users (chat_id, added_at) VALUES (?, ?)",
                    (cid, now),
                )
            conn.commit()

    log_event("startup", f"DB initialisée : {DB_PATH}")


# ---------------------------------------------------------------------------
# Gestion des utilisateurs autorisés
# ---------------------------------------------------------------------------


def get_authorized_chat_ids() -> set:
    """Retourne l'ensemble des chat_ids actifs (is_active=1)."""
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT chat_id FROM authorized_users WHERE is_active = 1")
        return {row["chat_id"] for row in cursor.fetchall()}


def get_authorized_users() -> list:
    """Retourne tous les utilisateurs (actifs et inactifs) avec tous leurs champs."""
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM authorized_users ORDER BY added_at DESC")
        return [dict(row) for row in cursor.fetchall()]


def add_authorized_user(chat_id: str, username: str = "", note: str = "") -> bool:
    """
    Insère un nouvel utilisateur ou réactive (is_active=1) un utilisateur révoqué.
    Retourne True si succès, False si erreur.
    """
    try:
        now = _utcnow()
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            # Tenter une insertion ; si le chat_id existe déjà, on réactive
            cursor.execute(
                """
                INSERT INTO authorized_users (chat_id, username, note, added_at, is_active)
                VALUES (?, ?, ?, ?, 1)
                ON CONFLICT(chat_id) DO UPDATE SET
                    is_active = 1,
                    username  = excluded.username,
                    note      = excluded.note
                """,
                (chat_id, username or None, note or None, now),
            )
            conn.commit()
        return True
    except Exception as exc:
        log_event("db_error", f"add_authorized_user({chat_id}): {exc}")
        return False


def remove_authorized_user(chat_id: str) -> bool:
    """
    Révocation soft : met is_active=0 (pas de suppression physique).
    Retourne True si succès.
    """
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                "UPDATE authorized_users SET is_active = 0 WHERE chat_id = ?",
                (chat_id,),
            )
            conn.commit()
        return True
    except Exception as exc:
        log_event("db_error", f"remove_authorized_user({chat_id}): {exc}")
        return False


# ---------------------------------------------------------------------------
# Logs d'interactions
# ---------------------------------------------------------------------------


def log_interaction(
    chat_id: str,
    username: str,
    command: str,
    menu: str,
    response_count: int,
) -> None:
    """Insère une interaction dans interaction_logs. Silencieux en cas d'erreur."""
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """
                INSERT INTO interaction_logs
                    (chat_id, username, command, menu, response_count, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    chat_id,
                    username or None,
                    (command or "")[:200],
                    menu or None,
                    response_count,
                    _utcnow(),
                ),
            )
            conn.commit()
    except Exception:
        pass


def get_interactions(limit: int = 100) -> list:
    """Retourne les N dernières interactions triées par timestamp DESC."""
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute(
            "SELECT * FROM interaction_logs ORDER BY timestamp DESC LIMIT ?",
            (limit,),
        )
        return [dict(row) for row in cursor.fetchall()]


def get_interactions_by_user(chat_id: str, limit: int = 50) -> list:
    """Retourne les interactions d'un utilisateur spécifique."""
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute(
            """
            SELECT * FROM interaction_logs
            WHERE chat_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """,
            (chat_id, limit),
        )
        return [dict(row) for row in cursor.fetchall()]


# ---------------------------------------------------------------------------
# Événements bot
# ---------------------------------------------------------------------------


def log_event(event_type: str, details: str = "") -> None:
    """Insère un événement dans bot_events. Silencieux en cas d'erreur."""
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                "INSERT INTO bot_events (event_type, details, timestamp) VALUES (?, ?, ?)",
                (event_type, details or None, _utcnow()),
            )
            conn.commit()
    except Exception:
        pass


def get_events(limit: int = 50) -> list:
    """Retourne les derniers événements bot triés par timestamp DESC."""
    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        cursor.execute(
            "SELECT * FROM bot_events ORDER BY timestamp DESC LIMIT ?",
            (limit,),
        )
        return [dict(row) for row in cursor.fetchall()]


# ---------------------------------------------------------------------------
# Statistiques
# ---------------------------------------------------------------------------


def get_stats() -> dict:
    """
    Retourne un dict avec les indicateurs clés :
      - total_users, active_users
      - total_interactions, interactions_today, active_users_today
      - access_denied_count
      - top_commands (top 5)
    """
    today_prefix = datetime.now(timezone.utc).strftime("%Y-%m-%d")

    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()

        cursor.execute("SELECT COUNT(*) AS cnt FROM authorized_users")
        total_users = cursor.fetchone()["cnt"]

        cursor.execute("SELECT COUNT(*) AS cnt FROM authorized_users WHERE is_active = 1")
        active_users = cursor.fetchone()["cnt"]

        cursor.execute("SELECT COUNT(*) AS cnt FROM interaction_logs")
        total_interactions = cursor.fetchone()["cnt"]

        cursor.execute(
            "SELECT COUNT(*) AS cnt FROM interaction_logs WHERE timestamp LIKE ?",
            (f"{today_prefix}%",),
        )
        interactions_today = cursor.fetchone()["cnt"]

        cursor.execute(
            "SELECT COUNT(*) AS cnt FROM bot_events WHERE event_type = 'access_denied'"
        )
        access_denied_count = cursor.fetchone()["cnt"]

        cursor.execute(
            """
            SELECT command, COUNT(*) AS count
            FROM interaction_logs
            GROUP BY command
            ORDER BY count DESC
            LIMIT 5
            """,
        )
        top_commands = [dict(row) for row in cursor.fetchall()]

        cursor.execute(
            """
            SELECT COUNT(DISTINCT chat_id) AS cnt
            FROM interaction_logs
            WHERE timestamp LIKE ?
            """,
            (f"{today_prefix}%",),
        )
        active_users_today = cursor.fetchone()["cnt"]

    return {
        "total_users": total_users,
        "active_users": active_users,
        "total_interactions": total_interactions,
        "interactions_today": interactions_today,
        "access_denied_count": access_denied_count,
        "top_commands": top_commands,
        "active_users_today": active_users_today,
    }


# ---------------------------------------------------------------------------
# Utilitaires internes
# ---------------------------------------------------------------------------


def _utcnow() -> str:
    """Retourne l'heure actuelle UTC en format ISO 8601."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S")


# ---------------------------------------------------------------------------
# Point d'entrée pour tests manuels
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    init_db()
    print("DB initialisée.")
