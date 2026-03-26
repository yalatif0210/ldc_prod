"""
report_queries.py — Requêtes READ ONLY sur les rapports, périodes, statuts.
"""
from sqlalchemy import text
from database import Session, release_session


class ReportQueries:

    @staticmethod
    def get_current_period():
        """Période dont la date du jour est comprise entre start_date et end_date."""
        sql = text("""
            SELECT p.id, p.period_name, p.start_date, p.end_date,
                   m.month AS month_name
            FROM period p
            JOIN month m ON m.id = p.month_id
            WHERE CURRENT_DATE BETWEEN p.start_date AND p.end_date
            ORDER BY p.id DESC
            LIMIT 1
        """)
        try:
            session = Session()
            row = session.execute(sql).mappings().first()
            return dict(row) if row else None
        finally:
            release_session()

    @staticmethod
    def get_recent_periods(limit: int = 10):
        """Dernières périodes, triées par date décroissante."""
        sql = text("""
            SELECT p.id, p.period_name, p.start_date, p.end_date,
                   m.month AS month_name
            FROM period p
            JOIN month m ON m.id = p.month_id
            ORDER BY p.start_date DESC
            LIMIT :limit
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'limit': limit}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_reports_summary_by_period(period_id: int):
        """Nombre de rapports groupés par statut pour une période."""
        sql = text("""
            SELECT s.status, COUNT(r.id) AS count
            FROM report r
            JOIN status s ON s.id = r.status_id
            WHERE r.period_id = :pid
            GROUP BY s.id, s.status
            ORDER BY s.id
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_submission_rate_by_region(period_id: int):
        """Taux de soumission par région : structures actives vs rapports reçus."""
        sql = text("""
            SELECT
                reg.name                            AS region,
                COUNT(DISTINCT s.id)                AS total_structures,
                COUNT(DISTINCT r.id)                AS submitted
            FROM region reg
            JOIN district d  ON d.region_id  = reg.id
            JOIN structure s ON s.district_id = d.id AND s.active = true
            LEFT JOIN account_structure acs ON acs.structure_id = s.id
            LEFT JOIN account acc ON acc.id = acs.account_id AND acc.is_active = true
            LEFT JOIN report r   ON r.account_id = acc.id AND r.period_id = :pid
            GROUP BY reg.id, reg.name
            ORDER BY reg.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_late_structures(period_id: int):
        """Structures actives sans aucun rapport pour la période donnée."""
        sql = text("""
            SELECT DISTINCT
                s.name    AS structure,
                d.name    AS district,
                reg.name  AS region
            FROM structure s
            JOIN district d   ON d.id  = s.district_id
            JOIN region reg   ON reg.id = d.region_id
            WHERE s.active = true
              AND NOT EXISTS (
                  SELECT 1
                  FROM account_structure acs
                  JOIN account  acc ON acc.id = acs.account_id AND acc.is_active = true
                  JOIN report   r   ON r.account_id = acc.id
                  WHERE acs.structure_id = s.id
                    AND r.period_id = :pid
              )
            ORDER BY reg.name, d.name, s.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_global_stats(period_id: int):
        """Statistiques globales pour le tableau de bord (rapports + structures)."""
        sql = text("""
            SELECT
                (SELECT COUNT(*) FROM report WHERE period_id = :pid)                        AS total_reports,
                (SELECT COUNT(*) FROM report r JOIN status s ON s.id = r.status_id
                    WHERE r.period_id = :pid AND LOWER(s.status) LIKE '%valid%')            AS validated,
                (SELECT COUNT(*) FROM report r JOIN status s ON s.id = r.status_id
                    WHERE r.period_id = :pid AND LOWER(s.status) LIKE '%soumis%')           AS submitted,
                (SELECT COUNT(*) FROM report r JOIN status s ON s.id = r.status_id
                    WHERE r.period_id = :pid AND LOWER(s.status) LIKE '%rejet%')            AS rejected,
                (SELECT COUNT(*) FROM structure WHERE active = true)                        AS active_structures,
                (SELECT COUNT(*) FROM account  WHERE is_active = true)                      AS active_accounts
        """)
        try:
            session = Session()
            row = session.execute(sql, {'pid': period_id}).mappings().first()
            return dict(row) if row else {}
        finally:
            release_session()
