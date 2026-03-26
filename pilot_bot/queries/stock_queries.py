"""
stock_queries.py — Requêtes READ ONLY sur les stocks d'intrants.
"""
from sqlalchemy import text
from database import Session, release_session


class StockQueries:

    @staticmethod
    def get_stock_summary(period_id: int):
        """Bilan global : entrées, distributions, disponible — par intrant."""
        sql = text("""
            SELECT
                i.name                              AS intrant,
                SUM(imd.entry_stock)                AS total_entree,
                SUM(imd.distribution_stock)         AS total_distribue,
                SUM(imd.available_stock)            AS total_disponible,
                COUNT(DISTINCT r.account_id)        AS nb_structures
            FROM intrant_mvt_data imd
            JOIN report  r ON r.id  = imd.report_id
            JOIN intrant i ON i.id  = imd.intrant_id
            WHERE r.period_id = :pid
            GROUP BY i.id, i.name
            ORDER BY i.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_stockouts(period_id: int):
        """Intrants avec available_stock = 0 pour la période."""
        sql = text("""
            SELECT
                i.name   AS intrant,
                s.name   AS structure,
                d.name   AS district,
                reg.name AS region
            FROM intrant_mvt_data imd
            JOIN report  r   ON r.id  = imd.report_id  AND r.period_id = :pid
            JOIN intrant i   ON i.id  = imd.intrant_id
            JOIN account acc ON acc.id = r.account_id
            JOIN account_structure acs ON acs.account_id = acc.id
            JOIN structure s  ON s.id  = acs.structure_id
            JOIN district  d  ON d.id  = s.district_id
            JOIN region    reg ON reg.id = d.region_id
            WHERE imd.available_stock = 0
            ORDER BY reg.name, i.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_low_stocks(period_id: int):
        """
        Intrants avec available_stock < distribution_stock (proxy pour stock < CMM)
        quand distribution_stock > 0.
        """
        sql = text("""
            SELECT
                i.name                  AS intrant,
                s.name                  AS structure,
                reg.name                AS region,
                imd.available_stock     AS dispo,
                imd.distribution_stock  AS distribue
            FROM intrant_mvt_data imd
            JOIN report  r   ON r.id   = imd.report_id  AND r.period_id = :pid
            JOIN intrant i   ON i.id   = imd.intrant_id
            JOIN account acc ON acc.id = r.account_id
            JOIN account_structure acs ON acs.account_id = acc.id
            JOIN structure s   ON s.id   = acs.structure_id
            JOIN district  d   ON d.id   = s.district_id
            JOIN region    reg ON reg.id  = d.region_id
            WHERE imd.available_stock > 0
              AND imd.distribution_stock > 0
              AND imd.available_stock < imd.distribution_stock
            ORDER BY (imd.available_stock::float / imd.distribution_stock) ASC
            LIMIT 30
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_stock_counts(period_id: int):
        """Compteurs rapides pour le dashboard."""
        sql = text("""
            SELECT
                COUNT(DISTINCT imd.intrant_id)  AS tracked_intrants,
                SUM(CASE WHEN imd.available_stock = 0 THEN 1 ELSE 0 END) AS stockouts,
                SUM(CASE WHEN imd.available_stock > 0
                          AND imd.distribution_stock > 0
                          AND imd.available_stock < imd.distribution_stock
                     THEN 1 ELSE 0 END) AS low_stocks
            FROM intrant_mvt_data imd
            JOIN report r ON r.id = imd.report_id AND r.period_id = :pid
        """)
        try:
            session = Session()
            row = session.execute(sql, {'pid': period_id}).mappings().first()
            return dict(row) if row else {'tracked_intrants': 0, 'stockouts': 0, 'low_stocks': 0}
        finally:
            release_session()
