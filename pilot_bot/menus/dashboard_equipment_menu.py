"""
menus/dashboard_equipment_menu.py — Menu Tableau de bord équipements.

Choix disponibles :
  1 → Taux de disponibilité tous équipements (période courante)
  2 → Risques de rupture par équipement (période courante)
  0 → Retour menu principal
"""
from session import set_session
from queries.dashboard_queries import DashboardQueries
from formatters.equipment_formatter import (
    fmt_dashboard_equipment,
    fmt_stockout_risk,
)

# ── Texte du menu ─────────────────────────────────────────────────────────────

DASHBOARD_EQUIPMENT_MENU_TEXT = (
    "🏥 <b>Dashboard équipements</b>\n\n"
    "1️⃣  Taux de disponibilité (tous équipements)\n"
    "2️⃣  Risques de rupture (stock = 0)\n"
    "0️⃣  ↩ Retour\n\n"
    "<i>Tapez le numéro de votre choix.</i>"
)


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_dashboard_equipment_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère les interactions dans le menu Dashboard équipements.

    Args:
        chat_id      : identifiant Telegram de la conversation.
        msg          : texte brut envoyé par l'utilisateur (déjà strippé).
        session_data : données de session courantes (non utilisées dans ce menu).

    Returns:
        list[str] — liste de messages à envoyer.
    """
    from menus.main_menu import MAIN_MENU_TEXT

    if msg == '0':
        set_session(chat_id, 'main')
        return [MAIN_MENU_TEXT]

    if msg == '1':
        # Taux de disponibilité par équipement — période courante
        period = DashboardQueries.get_current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rates = DashboardQueries.get_stock_rates_all_equipment(period['id'])
        return fmt_dashboard_equipment(rates, period['period_name'])

    if msg == '2':
        # Risques de rupture par équipement — période courante
        period = DashboardQueries.get_current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = DashboardQueries.get_stockout_risk_by_equipment(period['id'])
        return fmt_stockout_risk(rows, period['period_name'])

    # Entrée non reconnue → réafficher le menu
    return [DASHBOARD_EQUIPMENT_MENU_TEXT]
