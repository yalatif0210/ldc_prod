"""
bot_handler.py — Cœur du bot pilot_bot.

Point d'entrée unique pour tous les messages Telegram.
Responsabilités :
  - Vérification de l'autorisation via @require_authorized (security.py)
  - Gestion des commandes globales (indépendantes du menu actif)
  - Dispatch vers le handler du menu actif (via DISPATCH)
  - Construction des vues transversales : dashboard, /retard, /rupture

Architecture anti-import-circulaire :
  - Les sessions sont gérées dans session.py (importé ici ET dans les menus)
  - Les menus importent bot_handler uniquement en différé (import local dans la fonction)
"""
from security import require_authorized
from session import get_session, set_session, clear_session

from menus.main_menu       import handle_main_menu,    MAIN_MENU_TEXT
from menus.reports_menu    import handle_reports_menu,  REPORTS_MENU_TEXT
from menus.stocks_menu     import handle_stocks_menu,   STOCKS_MENU_TEXT
from menus.structures_menu import handle_structures_menu, STRUCTURES_MENU_TEXT
from menus.users_menu      import handle_users_menu,    USERS_MENU_TEXT

from queries.report_queries import ReportQueries
from queries.stock_queries  import StockQueries
from queries.user_queries   import UserQueries
from formatters.telegram_formatter import fmt_dashboard


# ── Table de dispatch : menu actif → handler ──────────────────────────────────
#
# Les clés correspondent aux valeurs possibles du champ 'menu' en session.
# 'reports_period' et 'structures_region' sont des sous-états des menus parents ;
# ils sont routés vers le même handler, qui lit session_data['state'] pour
# discriminer le comportement.

DISPATCH: dict = {
    'main':               handle_main_menu,
    'reports':            handle_reports_menu,
    'stocks':             handle_stocks_menu,
    'structures':         handle_structures_menu,
    'users':              handle_users_menu,
}


# ── Texte d'aide ──────────────────────────────────────────────────────────────

HELP_TEXT = (
    "ℹ️ <b>Commandes disponibles</b>\n\n"
    "<b>Navigation</b>\n"
    "/start, /menu   — Menu principal\n"
    "/dashboard      — Tableau de bord complet\n"
    "/rapports       — Menu Rapports\n"
    "/stocks         — Menu Stocks\n"
    "/structures     — Menu Structures\n"
    "/users          — Menu Utilisateurs\n\n"
    "<b>Raccourcis</b>\n"
    "/retard         — Structures en retard (période courante)\n"
    "/rupture        — Intrants en rupture (période courante)\n\n"
    "<b>Utilitaires</b>\n"
    "/id             — Affiche votre chat_id Telegram\n"
    "/help, /aide    — Ce message d'aide\n\n"
    "<i>Dans les menus, tapez le numéro affiché.\n"
    "Tapez <b>0</b> pour revenir au menu précédent.</i>"
)


# ── Handler principal ─────────────────────────────────────────────────────────

@require_authorized
def handle_message(chat_id: str, text: str, user_name: str = '') -> list[str]:
    """
    Point d'entrée unique pour tous les messages reçus par le bot.

    Traitement dans l'ordre :
      1. Commandes globales (indépendantes du menu actif)
      2. Dispatch vers le handler du menu actif en session

    Décorée par @require_authorized : tout chat_id absent de la whitelist
    reçoit un message de refus et la fonction n'est pas exécutée.

    Args:
        chat_id   : identifiant Telegram de la conversation (str).
        text      : texte brut du message Telegram.
        user_name : prénom Telegram de l'expéditeur (optionnel).

    Returns:
        list[str] — liste de messages HTML à envoyer via l'API Telegram.
    """
    msg = text.strip()

    # ── Commandes globales ────────────────────────────────────────────────────

    if msg in ('/start', '/menu'):
        clear_session(chat_id)
        set_session(chat_id, 'main')
        greeting = f"👋 Bonjour {user_name} !\n\n" if user_name else ""
        result = [greeting + MAIN_MENU_TEXT]
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg == '/id':
        return [f"Votre chat_id Telegram : <code>{chat_id}</code>"]

    if msg in ('/dashboard', 'dashboard'):
        result = _build_dashboard()
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg in ('/rapports', '/reports'):
        set_session(chat_id, 'reports')
        result = [REPORTS_MENU_TEXT]
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg in ('/stocks', '/stock'):
        set_session(chat_id, 'stocks')
        result = [STOCKS_MENU_TEXT]
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg == '/structures':
        set_session(chat_id, 'structures')
        result = [STRUCTURES_MENU_TEXT]
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg in ('/users', '/utilisateurs'):
        set_session(chat_id, 'users')
        result = [USERS_MENU_TEXT]
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg == '/retard':
        result = _shortcut_late()
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg == '/rupture':
        result = _shortcut_stockouts()
        try:
            from admin_db import log_interaction
            log_interaction(chat_id, user_name, msg, 'global', len(result))
        except Exception:
            pass
        return result

    if msg in ('/help', '/aide'):
        return [HELP_TEXT]

    # ── Dispatch vers le menu actif ───────────────────────────────────────────

    sess = get_session(chat_id)

    if not sess:
        # Pas de session (première visite ou session expirée) → menu principal
        set_session(chat_id, 'main')
        return [MAIN_MENU_TEXT]

    current_menu = sess.get('menu', 'main')
    session_data = sess.get('data', {})

    handler = DISPATCH.get(current_menu, handle_main_menu)
    replies = handler(chat_id, msg, session_data)

    # Logging de l'interaction
    try:
        from admin_db import log_interaction
        log_interaction(
            chat_id=chat_id,
            username=user_name,
            command=msg[:200],
            menu=current_menu,
            response_count=len(replies)
        )
    except Exception:
        pass

    return replies


# ── Builders transversaux ─────────────────────────────────────────────────────

def _build_dashboard() -> list[str]:
    """
    Construit le tableau de bord complet en agrégeant :
      - les statistiques rapports de la période courante
      - les compteurs de stocks
      - le bilan des comptes utilisateurs

    Returns:
        list[str] — un seul message formaté ou un message d'erreur.
    """
    period = ReportQueries.get_current_period()
    if not period:
        return ["⚠️ Aucune période active trouvée."]

    pid          = period['id']
    report_stats = ReportQueries.get_global_stats(pid)
    stock_counts = StockQueries.get_stock_counts(pid)
    user_stats   = UserQueries.get_accounts_summary()

    return [fmt_dashboard(period, report_stats, stock_counts, user_stats)]


def _shortcut_late() -> list[str]:
    """
    Raccourci /retard : structures sans rapport pour la période courante.

    Returns:
        list[str] — liste de messages (peut en contenir plusieurs si la liste est longue).
    """
    from formatters.telegram_formatter import fmt_late_structures

    period = ReportQueries.get_current_period()
    if not period:
        return ["⚠️ Aucune période active trouvée."]

    rows = ReportQueries.get_late_structures(period['id'])
    return fmt_late_structures(rows, period['period_name'])


def _shortcut_stockouts() -> list[str]:
    """
    Raccourci /rupture : intrants en rupture totale pour la période courante.

    Returns:
        list[str] — liste de messages (peut en contenir plusieurs si la liste est longue).
    """
    from formatters.telegram_formatter import fmt_stockouts

    period = ReportQueries.get_current_period()
    if not period:
        return ["⚠️ Aucune période active trouvée."]

    rows = StockQueries.get_stockouts(period['id'])
    return fmt_stockouts(rows, period['period_name'])
