"""
stock_equipment_queries.py — Requêtes READ ONLY sur les stocks par équipement.
"""
from sqlalchemy import text
from database import Session, release_session


class StockEquipmentQueries:

    @staticmethod
    def get_stock_by_equipment_period(equipment_id: int, period_id: int) -> list[dict]:
        """
        Stock de chaque intrant pour un équipement et une période.
        Agrège les données par intrant sur les rapports VALIDÉS ou SOUMIS.
        Retourne : [{'intrant': str, 'intrant_type': str,
                     'entry_stock': int, 'distribution_stock': int, 'available_stock': int}]
        """
        sql = text("""
            SELECT
                i.name                          AS intrant,
                it.name                         AS intrant_type,
                SUM(imd.entry_stock)            AS entry_stock,
                SUM(imd.distribution_stock)     AS distribution_stock,
                SUM(imd.available_stock)        AS available_stock
            FROM intrant_mvt_data imd
            JOIN intrant i      ON i.id  = imd.intrant_id
                                AND i.equipment_id = :eid
            JOIN intrant_type it ON it.id = i.intrant_type_id
            JOIN report r       ON r.id  = imd.report_id
                                AND r.period_id = :pid
            JOIN status s       ON s.id  = r.status_id
                                AND s.status IN ('VALIDATED', 'SUBMITTED')
            GROUP BY i.id, i.name, it.name
            ORDER BY it.name, i.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'eid': equipment_id, 'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_availability_rate_by_equipment(equipment_id: int, period_id: int) -> list[dict]:
        """
        Taux de disponibilité par intrant pour un équipement et une période.
        Taux = (SUM(available_stock) / NULLIF(SUM(entry_stock), 0)) * 100.
        Exclut les intrants de type CONSOMMABLES_GENERAUX.
        Retourne : [{'intrant': str, 'intrant_type': str,
                     'taux_dispo': float, 'available_stock': int, 'entry_stock': int}]
        """
        sql = text("""
            SELECT
                i.name                                                              AS intrant,
                it.name                                                             AS intrant_type,
                ROUND(
                    (SUM(imd.available_stock)::numeric
                     / NULLIF(SUM(imd.entry_stock), 0)) * 100,
                    2
                )                                                                   AS taux_dispo,
                SUM(imd.available_stock)                                            AS available_stock,
                SUM(imd.entry_stock)                                                AS entry_stock
            FROM intrant_mvt_data imd
            JOIN intrant i       ON i.id  = imd.intrant_id
                                 AND i.equipment_id = :eid
            JOIN intrant_type it  ON it.id = i.intrant_type_id
                                 AND it.name <> 'CONSOMMABLES_GENERAUX'
            JOIN report r        ON r.id  = imd.report_id
                                 AND r.period_id = :pid
            JOIN status s        ON s.id  = r.status_id
                                 AND s.status IN ('VALIDATED', 'SUBMITTED')
            GROUP BY i.id, i.name, it.name
            ORDER BY it.name, i.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'eid': equipment_id, 'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_equipment_name(equipment_id: int) -> str:
        """
        Retourne le nom de l'équipement ou 'Equipement inconnu' si non trouvé.
        """
        sql = text("SELECT name FROM equipment WHERE id = :eid")
        try:
            session = Session()
            row = session.execute(sql, {'eid': equipment_id}).mappings().first()
            return row['name'] if row else 'Equipement inconnu'
        finally:
            release_session()
