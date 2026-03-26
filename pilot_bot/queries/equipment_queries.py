"""
equipment_queries.py — Requêtes READ ONLY sur les équipements, mois et périodes.
"""
from sqlalchemy import text
from database import Session, release_session


class EquipmentQueries:

    @staticmethod
    def get_all_equipment() -> list[dict]:
        """Liste de tous les équipements, triés par nom."""
        sql = text("SELECT id, name FROM equipment ORDER BY name")
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_current_period() -> dict | None:
        """
        Période active : CURRENT_DATE compris entre start_date et end_date.
        Si aucune période n'est active, retourne la plus récente (end_date DESC).
        """
        sql_active = text("""
            SELECT id, period_name, start_date, end_date, month_id
            FROM period
            WHERE CURRENT_DATE BETWEEN start_date AND end_date
            LIMIT 1
        """)
        sql_latest = text("""
            SELECT id, period_name, start_date, end_date, month_id
            FROM period
            ORDER BY end_date DESC
            LIMIT 1
        """)
        try:
            session = Session()
            row = session.execute(sql_active).mappings().first()
            if row:
                return dict(row)
            row = session.execute(sql_latest).mappings().first()
            return dict(row) if row else None
        finally:
            release_session()

    @staticmethod
    def get_period_by_id(period_id: int) -> dict | None:
        """Retourne une période par son ID, ou None si non trouvée."""
        sql = text("""
            SELECT id, period_name, start_date, end_date, month_id
            FROM period
            WHERE id = :pid
        """)
        try:
            session = Session()
            row = session.execute(sql, {'pid': period_id}).mappings().first()
            return dict(row) if row else None
        finally:
            release_session()

    @staticmethod
    def get_months_with_periods() -> list[dict]:
        """
        Mois ayant au moins une période associée, triés par ID DESC
        (les plus récents en premier).
        """
        sql = text("""
            SELECT DISTINCT m.id, m.month
            FROM month m
            JOIN period p ON p.month_id = m.id
            ORDER BY m.id DESC
        """)
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_periods_by_month(month_id: int) -> list[dict]:
        """Liste des périodes d'un mois donné, triées par date de début."""
        sql = text("""
            SELECT id, period_name, start_date, end_date
            FROM period
            WHERE month_id = :mid
            ORDER BY start_date
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'mid': month_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()
