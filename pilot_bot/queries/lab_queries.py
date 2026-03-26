"""
lab_queries.py — Requêtes READ ONLY sur les données d'activité laboratoire.
"""
from sqlalchemy import text
from database import Session, release_session


class LabQueries:

    @staticmethod
    def get_lab_data_by_equipment_period(equipment_id: int, period_id: int) -> list[dict]:
        """
        Données d'activité labo pour un équipement et une période.
        Joindre sur rapports VALIDATED ou SUBMITTED.
        Agrège les valeurs par information (SUM si plusieurs rapports).
        Retourne: [{'unit': str, 'sub_unit': str|None, 'sub_sub_unit': str|None, 'total_value': int}]
        """
        sql = text("""
            SELECT
                iu.name   AS unit,
                isu.name  AS sub_unit,
                issu.name AS sub_sub_unit,
                SUM(lad.value) AS total_value
            FROM lab_activity_data lad
            JOIN information inf
                ON  inf.id           = lad.information_id
                AND inf.equipment_id = :eid
                AND inf.is_active    = true
            JOIN information_unit iu
                ON  iu.id = inf.information_unit_id
            LEFT JOIN information_sub_unit isu
                ON  isu.id = inf.information_sub_unit_id
            LEFT JOIN information_sub_sub_unit issu
                ON  issu.id = inf.information_sub_sub_unit_id
            JOIN report r
                ON  r.id        = lad.report_id
                AND r.period_id = :pid
            JOIN status s
                ON  s.id     = r.status_id
                AND s.status IN ('VALIDATED', 'SUBMITTED')
            GROUP BY
                iu.id,   iu.name,
                isu.id,  isu.name,
                issu.id, issu.name
            ORDER BY
                iu.name,
                isu.name  NULLS LAST,
                issu.name NULLS LAST
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'eid': equipment_id, 'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_lab_summary_all_equipment(period_id: int) -> list[dict]:
        """
        Pour le tableau de bord : agrégation par équipement.
        Retourne: [{'equipment': str, 'unit': str, 'sub_unit': str|None, 'total_value': int}]
        """
        sql = text("""
            SELECT
                e.name   AS equipment,
                iu.name  AS unit,
                isu.name AS sub_unit,
                SUM(lad.value) AS total_value
            FROM lab_activity_data lad
            JOIN information inf
                ON  inf.id        = lad.information_id
                AND inf.is_active = true
            JOIN information_unit iu
                ON  iu.id = inf.information_unit_id
            LEFT JOIN information_sub_unit isu
                ON  isu.id = inf.information_sub_unit_id
            JOIN equipment e
                ON  e.id = inf.equipment_id
            JOIN report r
                ON  r.id        = lad.report_id
                AND r.period_id = :pid
            JOIN status s
                ON  s.id     = r.status_id
                AND s.status IN ('VALIDATED', 'SUBMITTED')
            GROUP BY
                e.id,  e.name,
                iu.id, iu.name,
                isu.id, isu.name
            ORDER BY
                e.name,
                iu.name,
                isu.name NULLS LAST
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'pid': period_id}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()
