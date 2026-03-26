"""
structure_queries.py — Requêtes READ ONLY sur les structures, districts, régions.
"""
from sqlalchemy import text
from database import Session, release_session


class StructureQueries:

    @staticmethod
    def get_national_coverage():
        """Nombre total de structures actives vs total."""
        sql = text("""
            SELECT
                COUNT(*)                                            AS total,
                SUM(CASE WHEN active = true  THEN 1 ELSE 0 END)   AS active,
                SUM(CASE WHEN active = false THEN 1 ELSE 0 END)    AS inactive
            FROM structure
        """)
        try:
            session = Session()
            row = session.execute(sql).mappings().first()
            return dict(row) if row else {}
        finally:
            release_session()

    @staticmethod
    def get_structures_by_region():
        """Nombre de structures (actives / total) par région."""
        sql = text("""
            SELECT
                reg.name                                            AS region,
                COUNT(s.id)                                         AS total,
                SUM(CASE WHEN s.active = true THEN 1 ELSE 0 END)   AS active
            FROM region reg
            LEFT JOIN district d  ON d.region_id  = reg.id
            LEFT JOIN structure s ON s.district_id = d.id
            GROUP BY reg.id, reg.name
            ORDER BY reg.name
        """)
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_structures_in_region(region_name: str):
        """Liste des structures d'une région (par district)."""
        sql = text("""
            SELECT
                d.name   AS district,
                s.name   AS structure,
                s.active AS active
            FROM structure s
            JOIN district d   ON d.id   = s.district_id
            JOIN region   reg ON reg.id  = d.region_id
            WHERE LOWER(reg.name) LIKE LOWER(:region)
            ORDER BY d.name, s.name
        """)
        try:
            session = Session()
            rows = session.execute(sql, {'region': f'%{region_name}%'}).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_inactive_structures():
        """Structures marquées inactives."""
        sql = text("""
            SELECT
                s.name   AS structure,
                d.name   AS district,
                reg.name AS region
            FROM structure s
            JOIN district d   ON d.id   = s.district_id
            JOIN region   reg ON reg.id  = d.region_id
            WHERE s.active = false
            ORDER BY reg.name, d.name, s.name
        """)
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()

    @staticmethod
    def get_all_regions():
        """Liste de toutes les régions."""
        sql = text("SELECT id, name FROM region ORDER BY name")
        try:
            session = Session()
            rows = session.execute(sql).mappings().all()
            return [dict(r) for r in rows]
        finally:
            release_session()
