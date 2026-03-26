"""
menus/stocks_menu.py — Menu Stocks d'intrants du bot pilot_bot.

Choix disponibles :
  1 → Bilan global stocks (période courante)
  2 → Intrants en rupture de stock (available_stock = 0)
  3 → Stocks faibles (dispo < distribué)
  0 → Retour menu principal
"""
from session import set_session
from queries.report_queries import ReportQueries
from queries.stock_queries import StockQueries
from formatters.telegram_formatter import (
    fmt_stock_summary,
    fmt_stockouts,
    fmt_low_stocks,
)

# ── Texte du menu ─────────────────────────────────────────────────────────────

STOCKS_MENU_TEXT = (
    "🏥 <b>Stocks d'intrants</b>\n\n"
    "1️⃣  Bilan global (période courante)\n"
    "2️⃣  Intrants en rupture (stock = 0)\n"
    "3️⃣  Stocks faibles (dispo < distribué)\n"
    "0️⃣  ↩ Retour\n\n"
    "<i>Tapez le numéro de votre choix.</i>"
)

# ── Helper interne ────────────────────────────────────────────────────────────

def _current_period():
    """Retourne la période courante ou None si aucune n'est active."""
    return ReportQueries.get_current_period()


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_stocks_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère les interactions dans le menu Stocks.

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
        # Bilan global des stocks — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = StockQueries.get_stock_summary(period['id'])
        return fmt_stock_summary(rows, period['period_name'])

    if msg == '2':
        # Intrants en rupture totale — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = StockQueries.get_stockouts(period['id'])
        return fmt_stockouts(rows, period['period_name'])

    if msg == '3':
        # Stocks faibles — période courante
        period = _current_period()
        if not period:
            return ["⚠️ Aucune période active trouvée."]
        rows = StockQueries.get_low_stocks(period['id'])
        return fmt_low_stocks(rows, period['period_name'])

    # Entrée non reconnue → réafficher le menu
    return [STOCKS_MENU_TEXT]
