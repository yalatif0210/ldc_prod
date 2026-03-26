"""
menus/stocks_equipment_menu.py — Menu Stocks par équipement.

Navigation à deux niveaux :
  Niveau 1 — pick_equipment : liste numérotée des équipements
  Niveau 2 — pick_action    : choix de la vue pour l'équipement sélectionné
                              1 → Stocks détaillés (période courante)
                              2 → Taux de disponibilité (période courante)
                              0 → Retour liste équipements

  0 depuis le niveau 1 → Retour menu principal
"""
from session import set_session
from queries.equipment_queries import EquipmentQueries
from queries.stock_equipment_queries import StockEquipmentQueries
from formatters.equipment_formatter import (
    fmt_equipment_list,
    fmt_stock_by_equipment,
    fmt_availability_rate,
)

# ── Texte du sous-menu action ─────────────────────────────────────────────────

def _action_menu_text(equipment_name: str) -> str:
    return (
        f"🔬 <b>{equipment_name}</b>\n\n"
        "1️⃣  Stocks détaillés (période courante)\n"
        "2️⃣  Taux de disponibilité (période courante)\n"
        "0️⃣  ↩ Retour liste équipements\n\n"
        "<i>Tapez le numéro de votre choix.</i>"
    )


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_stocks_equipment_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère la navigation multi-niveaux du menu Stocks par équipement.

    session_data attendu :
      state          : 'pick_equipment' | 'pick_action'
      equipment_id   : int   (présent si state == 'pick_action')
      equipment_name : str   (présent si state == 'pick_action')
      equipments     : list  (cache de la liste des équipements)
    """
    from menus.main_menu import MAIN_MENU_TEXT

    state = session_data.get('state', 'pick_equipment')

    # ── Retour ────────────────────────────────────────────────────────────────

    if msg == '0':
        if state == 'pick_action':
            # Retour à la liste des équipements
            equipments = session_data.get('equipments') or EquipmentQueries.get_all_equipment()
            set_session(chat_id, 'stocks_equipment', {
                'state': 'pick_equipment',
                'equipments': equipments,
            })
            return [fmt_equipment_list(equipments)]
        else:
            # Retour menu principal
            set_session(chat_id, 'main')
            return [MAIN_MENU_TEXT]

    # ── Niveau 1 : sélection de l'équipement ─────────────────────────────────

    if state == 'pick_equipment':
        equipments = session_data.get('equipments') or EquipmentQueries.get_all_equipment()

        if not equipments:
            return ["⚠️ Aucun équipement trouvé en base."]

        try:
            idx = int(msg) - 1
            if 0 <= idx < len(equipments):
                eq = equipments[idx]
                set_session(chat_id, 'stocks_equipment', {
                    'state': 'pick_action',
                    'equipment_id': eq['id'],
                    'equipment_name': eq['name'],
                    'equipments': equipments,
                })
                return [_action_menu_text(eq['name'])]
        except (ValueError, TypeError):
            pass

        # Choix invalide → réafficher la liste
        set_session(chat_id, 'stocks_equipment', {
            'state': 'pick_equipment',
            'equipments': equipments,
        })
        return [fmt_equipment_list(equipments)]

    # ── Niveau 2 : action sur l'équipement sélectionné ───────────────────────

    equipment_id = session_data.get('equipment_id')
    equipment_name = session_data.get('equipment_name', '—')

    if msg == '1':
        # Stocks détaillés — période courante
        period = EquipmentQueries.get_current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = StockEquipmentQueries.get_stock_by_equipment_period(equipment_id, period['id'])
        return fmt_stock_by_equipment(rows, equipment_name, period['period_name'])

    if msg == '2':
        # Taux de disponibilité — période courante
        period = EquipmentQueries.get_current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = StockEquipmentQueries.get_availability_rate_by_equipment(equipment_id, period['id'])
        return fmt_availability_rate(rows, equipment_name, period['period_name'])

    # Choix invalide → réafficher le sous-menu action
    return [_action_menu_text(equipment_name)]
