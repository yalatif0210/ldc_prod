"""
menus/main_menu.py — Menu principal du bot pilot_bot.

Propose 5 choix numérotés :
  1 → Rapports
  2 → Stocks
  3 → Structures
  4 → Utilisateurs
  5 → Tableau de bord complet

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
    "5️⃣  Tableau de bord\n\n"
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
        # Le dashboard est construit dans bot_handler pour centraliser la logique
        from bot_handler import _build_dashboard
        return _build_dashboard()

    # Entrée non reconnue → réafficher le menu
    return [MAIN_MENU_TEXT]
