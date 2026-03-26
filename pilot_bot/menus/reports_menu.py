"""
menus/reports_menu.py — Menu Rapports du bot pilot_bot.

Choix disponibles :
  1 → Statut des rapports (période courante)
  2 → Structures en retard (période courante)
  3 → Taux de soumission par région (période courante)
  4 → Choisir une autre période (liste numérotée → attend un choix → affiche stats)
  0 → Retour menu principal

État de session géré :
  menu='reports'        — navigation normale dans ce menu
  menu='reports_period' — en attente d'un numéro de période (après choix 4)
    session_data['periods'] : list[dict] — périodes disponibles indexées par numéro (str)
"""
from session import set_session
from queries.report_queries import ReportQueries
from formatters.telegram_formatter import (
    fmt_reports_by_status,
    fmt_late_structures,
    fmt_submission_by_region,
    fmt_periods_list,
)

# ── Texte du menu ─────────────────────────────────────────────────────────────

REPORTS_MENU_TEXT = (
    "📊 <b>Rapports</b>\n\n"
    "1️⃣  Statut des rapports (période courante)\n"
    "2️⃣  Structures en retard\n"
    "3️⃣  Taux de soumission par région\n"
    "4️⃣  Choisir une autre période\n"
    "0️⃣  ↩ Retour\n\n"
    "<i>Tapez le numéro de votre choix.</i>"
)

# ── Helper interne ────────────────────────────────────────────────────────────

def _current_period():
    """Retourne la période courante ou None si aucune n'est active."""
    return ReportQueries.get_current_period()


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_reports_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère les interactions dans le menu Rapports.

    Deux états possibles selon la valeur de session 'menu' :
      - 'reports'        → navigation dans les options 0-4
      - 'reports_period' → l'utilisateur choisit une période dans la liste affichée

    Args:
        chat_id      : identifiant Telegram de la conversation.
        msg          : texte brut envoyé par l'utilisateur (déjà strippé).
        session_data : données de session courantes (contient éventuellement 'periods').

    Returns:
        list[str] — liste de messages à envoyer.
    """
    from menus.main_menu import MAIN_MENU_TEXT

    # ── État : en attente d'un choix de période ───────────────────────────────
    if session_data.get('state') == 'reports_period':
        return _handle_period_choice(chat_id, msg, session_data)

    # ── Navigation normale dans le menu Rapports ──────────────────────────────

    if msg == '0':
        # Retour menu principal
        set_session(chat_id, 'main')
        return [MAIN_MENU_TEXT]

    if msg == '1':
        # Statut des rapports — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = ReportQueries.get_reports_summary_by_period(period['id'])
        return [fmt_reports_by_status(rows, period['period_name'])]

    if msg == '2':
        # Structures en retard — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = ReportQueries.get_late_structures(period['id'])
        return fmt_late_structures(rows, period['period_name'])

    if msg == '3':
        # Taux de soumission par région — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = ReportQueries.get_submission_rate_by_region(period['id'])
        return [fmt_submission_by_region(rows, period['period_name'])]

    if msg == '4':
        # Afficher la liste des périodes récentes et passer en mode attente
        return _show_period_list(chat_id)

    # Entrée non reconnue → réafficher le menu
    return [REPORTS_MENU_TEXT]


# ── Helpers ───────────────────────────────────────────────────────────────────

def _show_period_list(chat_id: str) -> list[str]:
    """
    Charge les 10 dernières périodes, les numérote, les stocke en session
    et affiche la liste à l'utilisateur.
    """
    periods_raw = ReportQueries.get_recent_periods(limit=10)
    if not periods_raw:
        return ["⚠️ Aucune période trouvée en base."]

    # Indexation 1-based sous forme de dict {str → dict_period}
    indexed: dict[str, dict] = {str(i + 1): p for i, p in enumerate(periods_raw)}

    # Passer en état d'attente de choix de période
    set_session(chat_id, 'reports', data={'state': 'reports_period', 'periods': indexed})

    header = fmt_periods_list(periods_raw)
    footer = "\n<i>Tapez le numéro de la période souhaitée.\nTapez <b>0</b> pour annuler.</i>"
    return [header + footer]


def _handle_period_choice(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Traite le choix d'une période dans la liste.
    Affiche le résumé des rapports pour la période sélectionnée.
    """
    if msg == '0':
        # Annulation → retour au menu rapports
        set_session(chat_id, 'reports')
        return [REPORTS_MENU_TEXT]

    periods: dict = session_data.get('periods', {})
    chosen = periods.get(msg)

    if not chosen:
        nb = len(periods)
        return [f"⚠️ Choix invalide. Entrez un numéro entre 1 et {nb}, ou 0 pour annuler."]

    # Récupérer les stats pour la période choisie
    pid = chosen['id']
    period_name = chosen['period_name']
    rows = ReportQueries.get_reports_summary_by_period(pid)

    # Revenir au menu rapports normal après l'affichage
    set_session(chat_id, 'reports')
    return [fmt_reports_by_status(rows, period_name), REPORTS_MENU_TEXT]
