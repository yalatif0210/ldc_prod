"""
menus/structures_menu.py — Menu Structures du bot pilot_bot.

Choix disponibles :
  1 → Couverture nationale (total / actives / inactives)
  2 → Vue par région (liste des régions numérotées → attend un choix → liste structures)
  3 → Structures inactives
  0 → Retour menu principal

États de session gérés :
  menu='structures'        — navigation normale dans ce menu
  menu='structures_region' — en attente d'un numéro de région (après choix 2)
    session_data['regions'] : dict {str → dict_region} — régions indexées 1-based
"""
from session import set_session
from queries.structure_queries import StructureQueries
from formatters.telegram_formatter import (
    fmt_national_coverage,
    fmt_inactive_structures,
)

# ── Texte du menu ─────────────────────────────────────────────────────────────

STRUCTURES_MENU_TEXT = (
    "🏛 <b>Structures</b>\n\n"
    "1️⃣  Couverture nationale\n"
    "2️⃣  Vue par région\n"
    "3️⃣  Structures inactives\n"
    "0️⃣  ↩ Retour\n\n"
    "<i>Tapez le numéro de votre choix.</i>"
)


# ── Handler principal ─────────────────────────────────────────────────────────

def handle_structures_menu(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Gère les interactions dans le menu Structures.

    Deux états possibles selon la valeur de session_data['state'] :
      - absent / autre    → navigation dans les options 0-3
      - 'structures_region' → l'utilisateur choisit une région dans la liste affichée

    Args:
        chat_id      : identifiant Telegram de la conversation.
        msg          : texte brut envoyé par l'utilisateur (déjà strippé).
        session_data : données de session courantes (contient éventuellement 'regions').

    Returns:
        list[str] — liste de messages à envoyer.
    """
    from menus.main_menu import MAIN_MENU_TEXT

    # ── État : en attente d'un choix de région ────────────────────────────────
    if session_data.get('state') == 'structures_region':
        return _handle_region_choice(chat_id, msg, session_data)

    # ── Navigation normale dans le menu Structures ────────────────────────────

    if msg == '0':
        set_session(chat_id, 'main')
        return [MAIN_MENU_TEXT]

    if msg == '1':
        # Couverture nationale + répartition par région
        coverage = StructureQueries.get_national_coverage()
        by_region = StructureQueries.get_structures_by_region()
        return [fmt_national_coverage(coverage, by_region)]

    if msg == '2':
        # Afficher la liste des régions et passer en mode attente
        return _show_region_list(chat_id)

    if msg == '3':
        # Structures inactives
        rows = StructureQueries.get_inactive_structures()
        return fmt_inactive_structures(rows)

    # Entrée non reconnue → réafficher le menu
    return [STRUCTURES_MENU_TEXT]


# ── Helpers ───────────────────────────────────────────────────────────────────

def _show_region_list(chat_id: str) -> list[str]:
    """
    Charge toutes les régions, les numérote, les stocke en session
    et affiche la liste à l'utilisateur pour qu'il choisisse.
    """
    regions_raw = StructureQueries.get_all_regions()
    if not regions_raw:
        return ["⚠️ Aucune région trouvée en base."]

    # Indexation 1-based : {'1': {'id': ..., 'name': ...}, ...}
    indexed: dict[str, dict] = {str(i + 1): r for i, r in enumerate(regions_raw)}

    # Construire le message de liste
    lines = ["🗺 <b>Régions disponibles</b>\n"]
    for num, region in indexed.items():
        lines.append(f"{num}. {region['name']}")
    lines.append("\n<i>Tapez le numéro de la région souhaitée.\nTapez <b>0</b> pour annuler.</i>")
    list_msg = "\n".join(lines)

    # Passer en état d'attente de choix de région
    set_session(chat_id, 'structures', data={'state': 'structures_region', 'regions': indexed})
    return [list_msg]


def _handle_region_choice(chat_id: str, msg: str, session_data: dict) -> list[str]:
    """
    Traite le choix d'une région dans la liste.
    Affiche les structures de la région sélectionnée.
    """
    if msg == '0':
        # Annulation → retour au menu structures
        set_session(chat_id, 'structures')
        return [STRUCTURES_MENU_TEXT]

    regions: dict = session_data.get('regions', {})
    chosen = regions.get(msg)

    if not chosen:
        nb = len(regions)
        return [f"⚠️ Choix invalide. Entrez un numéro entre 1 et {nb}, ou 0 pour annuler."]

    region_name = chosen['name']
    rows = StructureQueries.get_structures_in_region(region_name)

    # Revenir au menu structures normal après l'affichage
    set_session(chat_id, 'structures')

    if not rows:
        return [f"⚠️ Aucune structure trouvée pour la région <b>{region_name}</b>.", STRUCTURES_MENU_TEXT]

    # Construire l'affichage des structures par district
    lines = [f"🏛 <b>Structures — {region_name}</b>\n"]
    current_district = None
    for r in rows:
        if r['district'] != current_district:
            current_district = r['district']
            lines.append(f"\n<b>{current_district}</b>")
        status = "✅" if r['active'] else "❌"
        lines.append(f"  {status} {r['structure']}")

    return ["\n".join(lines), STRUCTURES_MENU_TEXT]
