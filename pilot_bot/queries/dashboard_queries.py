"""
dashboard_queries.py — Requêtes SQL pour le tableau de bord du bot.

Fournit des méthodes de lecture agrégées sur les données de stock et de
disponibilité par équipement, pour la période en cours ou une période donnée.

Pattern : from database import Session, release_session
          from sqlalchemy import text
"""
from database import Session, release_session
from sqlalchemy import text


class DashboardQueries:

    @staticmethod
    def get_current_period() -> dict | None:
        """
        Retourne la période en cours (CURRENT_DATE BETWEEN start_date AND end_date).
        Fallback : la période la plus récente (ORDER BY end_date DESC LIMIT 1).
        Retourne un dict avec les clés : id, period_name, start_date, end_date
        ou None en cas d'erreur.
        """
        session = Session()
        try:
            # Tentative : période dont la date du jour est dans l'intervalle
            result = session.execute(text("""
                SELECT id, period_name, start_date, end_date
                FROM period
                WHERE CURRENT_DATE BETWEEN start_date AND end_date
                LIMIT 1
            """))
            row = result.mappings().fetchone()

            if row:
                return dict(row)

            # Fallback : période la plus récente
            result = session.execute(text("""
                SELECT id, period_name, start_date, end_date
                FROM period
                ORDER BY end_date DESC
                LIMIT 1
            """))
            row = result.mappings().fetchone()
            return dict(row) if row else None

        except Exception:
            return None
        finally:
            release_session()

    @staticmethod
    def get_stock_rates_all_equipment(period_id: int) -> list[dict]:
        """
        Taux de disponibilité par équipement pour une période donnée.
        Exclut les intrants de type CONSOMMABLES_GENERAUX.
        Ne prend en compte que les rapports VALIDATED ou SUBMITTED.

        Retourne une liste de dicts :
            equipment, taux_dispo, total_available, total_entry
        """
        session = Session()
        try:
            result = session.execute(text("""
                SELECT e.name AS equipment,
                       ROUND(
                           SUM(imd.available_stock)::numeric
                           / NULLIF(SUM(imd.entry_stock), 0) * 100,
                           1
                       ) AS taux_dispo,
                       SUM(imd.available_stock) AS total_available,
                       SUM(imd.entry_stock)     AS total_entry
                FROM intrant_mvt_data imd
                JOIN intrant      i   ON i.id   = imd.intrant_id
                JOIN intrant_type it  ON it.id  = i.intrant_type_id
                                     AND it.name != 'CONSOMMABLES_GENERAUX'
                JOIN equipment    e   ON e.id   = i.equipment_id
                JOIN report       r   ON r.id   = imd.report_id
                                     AND r.period_id = :pid
                JOIN status       s   ON s.id   = r.status_id
                                     AND s.status IN ('VALIDATED', 'SUBMITTED')
                GROUP BY e.id, e.name
                ORDER BY e.name
            """), {'pid': period_id})

            return [dict(row) for row in result.mappings().fetchall()]

        except Exception:
            return []
        finally:
            release_session()

    @staticmethod
    def get_stockout_risk_by_equipment(period_id: int) -> list[dict]:
        """
        Structures présentant un risque de rupture de stock (available_stock = 0)
        par équipement et intrant, pour une période donnée.
        Exclut les intrants de type CONSOMMABLES_GENERAUX.
        Ne prend en compte que les rapports VALIDATED ou SUBMITTED.

        Retourne une liste de dicts :
            equipment, intrant, structure, region
        """
        session = Session()
        try:
            result = session.execute(text("""
                SELECT e.name   AS equipment,
                       i.name   AS intrant,
                       s.name   AS structure,
                       reg.name AS region
                FROM intrant_mvt_data imd
                JOIN intrant      i   ON i.id   = imd.intrant_id
                JOIN intrant_type it  ON it.id  = i.intrant_type_id
                                     AND it.name != 'CONSOMMABLES_GENERAUX'
                JOIN equipment    e   ON e.id   = i.equipment_id
                JOIN report       r   ON r.id   = imd.report_id
                                     AND r.period_id = :pid
                JOIN status       s2  ON s2.id  = r.status_id
                                     AND s2.status IN ('VALIDATED', 'SUBMITTED')
                JOIN account      acc ON acc.id = r.account_id
                JOIN account_structure acs ON acs.account_id = acc.id
                JOIN structure    s   ON s.id   = acs.structure_id
                JOIN district     d   ON d.id   = s.district_id
                JOIN region       reg ON reg.id = d.region_id
                WHERE imd.available_stock = 0
                ORDER BY e.name, i.name, s.name
            """), {'pid': period_id})

            return [dict(row) for row in result.mappings().fetchall()]

        except Exception:
            return []
        finally:
            release_session()
