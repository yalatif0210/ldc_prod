"""
menus/main_menu.py — Menu principal du bot pilot_bot.

Propose 8 choix numérotés :
  1 → Rapports
  2 → Stocks d'intrants (global)
  3 → Structures
  4 → Utilisateurs
  5 → Tableau de bord complet
  6 → Stocks par équipement
  7 → Données labo (par équipement)
  8 → Dashboard équipements

Les handlers de sous-menus sont importés ici pour le dispatch interne.
La session est gérée via session.py (pas de circular import).
"""
from session import set_session

# ── Texte du menu affiché à l'utilisateur ────────────────────────────────────

MAIN_MENU_TEXT = (
    "📋 <b>Menu principal</b>\n\n"
    "1️⃣  Rapports\n"
    "2️⃣  Stocks d'intrants\n"
    "3️⃣  Structures\n"
    "4️⃣  Utilisateurs\n"
    "5️⃣  Tableau de bord\n"
    "6️⃣  Stocks par équipement\n"
    "7️⃣  Données labo (par équipement)\n"
    "8️⃣  Dashboard équipements\n\n"
    "<i>Tapez le numéro de votre choix.</i>"
)


def handle_main_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère les choix du menu principal.

    Args:
        chat_id      : identifiant Telegram de la conversation.
        msg          : texte brut envoyé par l'utilisateur (déjà strippé).
        session_data : données contextuelles de la session courante (non utilisées ici).

    Returns:
        list[str] — liste de messages à envoyer à l'utilisateur.
    """
    # Import différé pour éviter les imports circulaires entre menus
    from menus.reports_menu    import REPORTS_MENU_TEXT
    from menus.stocks_menu     import STOCKS_MENU_TEXT
    from menus.structures_menu import STRUCTURES_MENU_TEXT
    from menus.users_menu      import USERS_MENU_TEXT
    from menus.dashboard_equipment_menu import DASHBOARD_EQUIPMENT_MENU_TEXT

    if msg == '1':
        set_session(chat_id, 'reports')
        return [REPORTS_MENU_TEXT]

    if msg == '2':
        set_session(chat_id, 'stocks')
        return [STOCKS_MENU_TEXT]

    if msg == '3':
        set_session(chat_id, 'structures')
        return [STRUCTURES_MENU_TEXT]

    if msg == '4':
        set_session(chat_id, 'users')
        return [USERS_MENU_TEXT]

    if msg == '5':
        # Le dashboard global est construit dans bot_handler
        from bot_handler import _build_dashboard
        return _build_dashboard()

    if msg == '6':
        from queries.equipment_queries import EquipmentQueries
        from formatters.equipment_formatter import fmt_equipment_list
        equipments = EquipmentQueries.get_all_equipment()
        set_session(chat_id, 'stocks_equipment', {
            'state': 'pick_equipment',
            'equipments': equipments,
        })
        return [fmt_equipment_list(equipments)]

    if msg == '7':
        from queries.equipment_queries import EquipmentQueries
        from formatters.equipment_formatter import fmt_equipment_list
        equipments = EquipmentQueries.get_all_equipment()
        set_session(chat_id, 'lab', {
            'state': 'pick_equipment',
            'equipments': equipments,
        })
        return [fmt_equipment_list(equipments)]

    if msg == '8':
        set_session(chat_id, 'dashboard_equipment')
        return [DASHBOARD_EQUIPMENT_MENU_TEXT]

    # Entrée non reconnue → réafficher le menu
    return [MAIN_MENU_TEXT]
