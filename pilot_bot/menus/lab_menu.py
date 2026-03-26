"""
menus/lab_menu.py — Menu Données laboratoire par équipement.

Navigation à deux niveaux :
  Niveau 1 — pick_equipment : liste numérotée des équipements
  Niveau 2 — (direct)       : affiche les données labo pour la période courante
                              0 → Retour liste équipements

  0 depuis le niveau 1 → Retour menu principal
"""
from session import set_session
from queries.equipment_queries import EquipmentQueries
from queries.lab_queries import LabQueries
from formatters.equipment_formatter import (
    fmt_equipment_list,
    fmt_lab_data_by_equipment,
)


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_lab_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère la navigation du menu Données labo par équipement.

    session_data attendu :
      state          : 'pick_equipment' | 'show_data'
      equipment_id   : int   (présent si state == 'show_data')
      equipment_name : str   (présent si state == 'show_data')
      equipments     : list  (cache de la liste des équipements)
    """
    from menus.main_menu import MAIN_MENU_TEXT

    state = session_data.get('state', 'pick_equipment')

    # ── Retour ────────────────────────────────────────────────────────────────

    if msg == '0':
        if state == 'show_data':
            # Retour à la liste des équipements
            equipments = session_data.get('equipments') or EquipmentQueries.get_all_equipment()
            set_session(chat_id, 'lab', {
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
                period = EquipmentQueries.get_current_period()
                if not period:
                    return ["⚠️ Aucune période active trouvée."]

                rows = LabQueries.get_lab_data_by_equipment_period(eq['id'], period['id'])
                messages = fmt_lab_data_by_equipment(rows, eq['name'], period['period_name'])

                # Maintenir la session pour permettre le retour
                set_session(chat_id, 'lab', {
                    'state': 'show_data',
                    'equipment_id': eq['id'],
                    'equipment_name': eq['name'],
                    'equipments': equipments,
                })

                # Ajouter un rappel navigation en fin de dernier message
                nav = "\n\n" + "─" * 20 + "\n<i>0 → Retour liste équipements</i>"
                if messages:
                    messages[-1] += nav
                return messages
        except (ValueError, TypeError):
            pass

        # Choix invalide → réafficher la liste
        set_session(chat_id, 'lab', {
            'state': 'pick_equipment',
            'equipments': equipments,
        })
        return [fmt_equipment_list(equipments)]

    # ── Niveau 2 : données affichées, on attend 0 pour revenir ───────────────

    # Tout message autre que '0' (géré plus haut) réaffiche le résultat
    equipment_id = session_data.get('equipment_id')
    equipment_name = session_data.get('equipment_name', '—')
    equipments = session_data.get('equipments') or []

    period = EquipmentQueries.get_current_period()
    if not period:
        return ["⚠️ Aucune période active trouvée."]

    rows = LabQueries.get_lab_data_by_equipment_period(equipment_id, period['id'])
    messages = fmt_lab_data_by_equipment(rows, equipment_name, period['period_name'])
    nav = "\n\n" + "─" * 20 + "\n<i>0 → Retour liste équipements</i>"
    if messages:
        messages[-1] += nav
    return messages
