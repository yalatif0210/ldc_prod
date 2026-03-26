"""
menus/users_menu.py — Menu Utilisateurs du bot pilot_bot.

Choix disponibles :
  1 → Bilan comptes (actifs / inactifs / par rôle)
  2 → Comptes inactifs (liste détaillée)
  3 → Comptes sans rapport pour la période courante
  0 → Retour menu principal
"""
from session import set_session
from queries.report_queries import ReportQueries
from queries.user_queries import UserQueries
from formatters.telegram_formatter import (
    fmt_accounts_summary,
    fmt_inactive_accounts,
)

# ── Texte du menu ─────────────────────────────────────────────────────────────

USERS_MENU_TEXT = (
    "👤 <b>Utilisateurs</b>\n\n"
    "1️⃣  Bilan comptes (actifs / inactifs / par rôle)\n"
    "2️⃣  Comptes inactifs\n"
    "3️⃣  Comptes sans rapport (période courante)\n"
    "0️⃣  ↩ Retour\n\n"
    "<i>Tapez le numéro de votre choix.</i>"
)

# ── Helper interne ────────────────────────────────────────────────────────────

def _current_period():
    """Retourne la période courante ou None si aucune n'est active."""
    return ReportQueries.get_current_period()


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_users_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère les interactions dans le menu Utilisateurs.

    Args:
        chat_id      : identifiant Telegram de la conversation.
        msg          : texte brut envoyé par l'utilisateur (déjà strippé).
        session_data : données de session courantes (non utilisées dans ce menu).

    Returns:
        list[str] — liste de messages à envoyer.
    """
    from menus.main_menu import MAIN_MENU_TEXT

    if msg == '0':
        # Retour menu principal
        set_session(chat_id, 'main')
        return [MAIN_MENU_TEXT]

    if msg == '1':
        # Bilan global + répartition par rôle
        summary = UserQueries.get_accounts_summary()
        by_role = UserQueries.get_accounts_by_role()
        return [fmt_accounts_summary(summary, by_role)]

    if msg == '2':
        # Liste des comptes inactifs
        rows = UserQueries.get_inactive_accounts()
        return fmt_inactive_accounts(rows)

    if msg == '3':
        # Comptes actifs sans rapport — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = UserQueries.get_accounts_without_reports(period['id'])
        if not rows:
            return [
                f"✅ Tous les comptes actifs ont soumis un rapport\npour la période <b>{period['period_name']}</b>."
            ]
        # Construire la liste
        lines = [f"📋 <b>Comptes sans rapport — {period['period_name']}</b>\n"]
        current_role = None
        for r in rows:
            if r['role'] != current_role:
                current_role = r['role']
                lines.append(f"\n<b>{current_role}</b>")
            structure_info = f" ({r['structure']})" if r.get('structure') else ""
            lines.append(f"  • {r['user_name']}{structure_info}")
        return ["\n".join(lines)]

    # Entrée non reconnue → réafficher le menu
    return [USERS_MENU_TEXT]
