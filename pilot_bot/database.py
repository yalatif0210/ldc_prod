"""
database.py — Connexion READ ONLY à lab_db via SQLAlchemy Core.
Aucune écriture possible : default_transaction_read_only=on.
"""
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, scoped_session
import os

LAB_DB_URL = os.getenv('LAB_DB_URL', '')


def _build_engine():
    if not LAB_DB_URL:
        raise RuntimeError("Variable d'environnement LAB_DB_URL manquante.")
    return create_engine(
        LAB_DB_URL,
        connect_args={"options": "-c default_transaction_read_only=on"},
        pool_size=3,
        max_overflow=5,
        pool_pre_ping=True,
        pool_recycle=300,
    )


engine = _build_engine()
SessionFactory = sessionmaker(bind=engine, autocommit=False, autoflush=False)
Session = scoped_session(SessionFactory)


def get_session():
    session = Session()
    try:
        return session
    except Exception:
        Session.remove()
        raise


def release_session():
    Session.remove()


def test_connection() -> bool:
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        print("✅ Connexion lab_db établie (read-only).")
        return True
    except Exception as e:
        print(f"❌ Connexion lab_db échouée : {e}")
        return False
