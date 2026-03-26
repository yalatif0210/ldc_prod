"""
user_queries.py — Requêtes READ ONLY sur les comptes et utilisateurs.
"""
from sqlalchemy import text
from database import Session, release_session


class UserQueries:

    @staticmethod
    def get_accounts_summary():
        """Bilan rapide : total, actifs, inactifs."""
        sql = text("""
            SELECT
                COUNT(*)                                                AS total,
                SUM(CASE WHEN a.is_active = true  THEN 1 ELSE 0 END)   AS active,
                SUM(CASE WHEN a.is_active = false THEN 1 ELSE 0 END)    AS inactive
            FROM account a
        """)
        try:
            session = Session()
            row = session.execute(sql).mappings().first()
            return dict(row) if row else {}
        finally:
            release_session()

    @staticmethod
    def get_accounts_by_role():
        """Répartition des comptes actifs par rôle."""
        sql = text("""
            SELECT
                r.role                                                  AS role,
                COUNT(a.id)                                             AS total,
                SUM(CASE WHEN a.is_active = true THEN 1 ELSE 0 END)    AS active
            FROM account a
            JOIN role r ON r.id = a.role_id
            GROUP BY r.id, r.role
            ORDER BY active DESC
        """)
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_inactive_accounts():
        """Comptes désactivés avec leur utilisateur et structures."""
        sql = text("""
            SELECT
                u.name      AS user_name,
                u.username  AS username,
                r.role      AS role
            FROM account a
            JOIN users u ON u.id = a.user_id
            JOIN role  r ON r.id = a.role_id
            WHERE a.is_active = false
            ORDER BY r.role, u.name
        """)
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_accounts_without_reports(period_id: int):
        """Comptes actifs n'ayant soumis aucun rapport pour la période."""
        sql = text("""
            SELECT
                u.name     AS user_name,
                r.role     AS role,
                s.name     AS structure
            FROM account a
            JOIN users u ON u.id = a.user_id
            JOIN role  r ON r.id = a.role_id
            LEFT JOIN account_structure acs ON acs.account_id = a.id
            LEFT JOIN structure s ON s.id = acs.structure_id
            WHERE a.is_active = true
              AND NOT EXISTS (
                  SELECT 1 FROM report rep
                  WHERE rep.account_id = a.id AND rep.period_id = :pid
              )
            ORDER BY r.role, u.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()
