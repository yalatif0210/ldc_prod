"""
equipment_formatter.py
-----------------------
Formate les données équipements/stocks/labo/dashboard en messages HTML Telegram.

Fonctions publiques :
  fmt_equipment_list         — liste numérotée d'équipements (sélection)
  fmt_stock_by_equipment     — tableau stocks intrants pour un équipement
  fmt_availability_rate      — taux de disponibilité par intrant
  fmt_lab_data_by_equipment  — données d'activité labo par équipement
  fmt_dashboard_equipment    — tableau de bord multi-équipements
  fmt_stockout_risk          — risques de rupture par équipement
"""
from __future__ import annotations
from math import floor

MAX_MSG_LEN = 4000


def _sep(char: str = "━", length: int = 32) -> str:
    return char * length


def _fmt_date(d) -> str:
    from datetime import date as Date
    if d is None:
        return "—"
    if isinstance(d, Date):
        return d.strftime("%d/%m/%Y")
    return str(d)


def _paginate(items: list, chunk: int) -> list[list]:
    return [items[i: i + chunk] for i in range(0, len(items), chunk)]


def _header(title: str, icon: str = "") -> str:
    prefix = f"{icon} " if icon else ""
    return f"<b>{prefix}{title}</b>\n{_sep()}\n"


def _progress_bar(pct: float, width: int = 10) -> str:
    pct = max(0.0, min(100.0, float(pct or 0)))
    filled = floor(width * pct / 100)
    empty = width - filled
    return f"[{'█' * filled}{'░' * empty}] {pct:.0f}%"


# ---------------------------------------------------------------------------
# Sélection d'équipement
# ---------------------------------------------------------------------------

def fmt_equipment_list(equipments: list) -> str:
    """
    equipments = [{'id': int, 'name': str}]
    Liste numérotée pour sélection par l'utilisateur.
    """
    lines = [
        _header("Sélection de l'équipement", "🔬"),
        "Tapez le <b>numéro</b> de l'équipement :\n",
    ]
    for i, eq in enumerate(equipments, start=1):
        lines.append(f"<b>{i}.</b> {eq.get('name', '—')}")
    lines.append("")
    lines.append(_sep("─"))
    lines.append("0️⃣  ↩ Retour menu principal")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Stocks par équipement
# ---------------------------------------------------------------------------

def fmt_stock_by_equipment(
    rows: list,
    equipment_name: str,
    period_label: str,
) -> list[str]:
    """
    rows = [{'intrant': str, 'intrant_type': str,
             'entry_stock': int, 'distribution_stock': int, 'available_stock': int}]
    Tableau ASCII <pre> paginé par 15 lignes.
    """
    header_base = _header(f"{equipment_name} — Stocks", "📦")
    sub = f"<i>Période : {period_label}</i>\n\n"

    if not rows:
        return [header_base + sub + "<i>Aucune donnée de stock disponible.</i>"]

    col_w = max((len(str(r.get("intrant", ""))) for r in rows), default=7)
    col_w = max(col_w, 7)

    def _build_table(chunk: list) -> str:
        sep = "+" + "-" * (col_w + 2) + "+" + "-" * 8 + "+" + "-" * 9 + "+" + "-" * 7 + "+"
        title_row = f"| {'Intrant':<{col_w}} | {'Entrée':>6} | {'Distrib.':>7} | {'Dispo':>5} |"
        lines = [sep, title_row, sep]
        current_type = None
        for row in chunk:
            itype = str(row.get("intrant_type", ""))
            if itype != current_type:
                current_type = itype
                label = itype[:col_w + 30]
                lines.append(f"  ── {label} ──")
            intrant = str(row.get("intrant", "—"))[:col_w]
            entry = int(row.get("entry_stock") or 0)
            distrib = int(row.get("distribution_stock") or 0)
            dispo = int(row.get("available_stock") or 0)
            lines.append(f"| {intrant:<{col_w}} | {entry:>6} | {distrib:>7} | {dispo:>5} |")
        lines.append(sep)
        return "\n".join(lines)

    pages = _paginate(rows, 15)
    messages = []
    for page_idx, page in enumerate(pages, start=1):
        page_header = header_base + sub
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)}</i>\n\n"
        messages.append(page_header + f"<pre>{_build_table(page)}</pre>")
    return messages


# ---------------------------------------------------------------------------
# Taux de disponibilité
# ---------------------------------------------------------------------------

def fmt_availability_rate(
    rows: list,
    equipment_name: str,
    period_label: str,
) -> list[str]:
    """
    rows = [{'intrant': str, 'intrant_type': str,
             'taux_dispo': float, 'available_stock': int, 'entry_stock': int}]
    Barre de progression par intrant, paginé par 12.
    """
    header_base = _header(f"{equipment_name} — Taux de disponibilité", "📊")
    sub = f"<i>Période : {period_label}</i>\n\n"

    if not rows:
        return [header_base + sub + "<i>Aucune donnée disponible.</i>"]

    pages = _paginate(rows, 12)
    messages = []
    total = len(rows)

    for page_idx, page in enumerate(pages, start=1):
        page_header = header_base + sub
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} intrant(s)</i>\n\n"

        items = []
        current_type = None
        for row in page:
            itype = str(row.get("intrant_type", ""))
            if itype != current_type:
                current_type = itype
                items.append(f"\n<b>── {itype} ──</b>")
            intrant = row.get("intrant", "—")
            taux = float(row.get("taux_dispo") or 0)
            dispo = int(row.get("available_stock") or 0)
            entry = int(row.get("entry_stock") or 0)

            if taux >= 80:
                icon = "✅"
            elif taux >= 50:
                icon = "🟡"
            elif taux > 0:
                icon = "🟠"
            else:
                icon = "🔴"

            items.append(
                f"{icon} <b>{intrant}</b>\n"
                f"   {_progress_bar(taux)}  ({dispo}/{entry})"
            )

        messages.append(page_header + "\n".join(items))
    return messages


# ---------------------------------------------------------------------------
# Données labo
# ---------------------------------------------------------------------------

def fmt_lab_data_by_equipment(
    rows: list,
    equipment_name: str,
    period_label: str,
) -> list[str]:
    """
    rows = [{'unit': str, 'sub_unit': str|None, 'sub_sub_unit': str|None,
             'total_value': int}]
    Affichage hiérarchique unit > sub_unit > sub_sub_unit.
    """
    header_base = _header(f"{equipment_name} — Données labo", "🧪")
    sub = f"<i>Période : {period_label}</i>\n\n"

    if not rows:
        return [header_base + sub + "<i>Aucune donnée laboratoire disponible.</i>"]

    pages = _paginate(rows, 20)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header_base + sub
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)}</i>\n\n"

        items = []
        current_unit = None
        current_sub = None

        for row in page:
            unit = row.get("unit", "—")
            sub_unit = row.get("sub_unit")
            sub_sub = row.get("sub_sub_unit")
            value = int(row.get("total_value") or 0)

            if unit != current_unit:
                current_unit = unit
                current_sub = None
                items.append(f"\n<b>📂 {unit}</b>")

            if sub_unit and sub_unit != current_sub:
                current_sub = sub_unit
                items.append(f"  <b>├ {sub_unit}</b>")

            if sub_sub:
                items.append(f"  │  └ {sub_sub} : <code>{value:,}</code>")
            elif sub_unit:
                items.append(f"  └ <code>{value:,}</code>")
            else:
                items.append(f"  Total : <code>{value:,}</code>")

        messages.append(page_header + "\n".join(items))
    return messages


# ---------------------------------------------------------------------------
# Dashboard multi-équipements
# ---------------------------------------------------------------------------

def fmt_dashboard_equipment(rates: list, period_label: str) -> list[str]:
    """
    rates = [{'equipment': str, 'taux_dispo': float,
               'total_available': int, 'total_entry': int}]
    Tableau récapitulatif taux de disponibilité par équipement.
    """
    header = _header(f"Dashboard équipements — {period_label}", "🏥")

    if not rates:
        return [header + "\n<i>Aucune donnée disponible pour cette période.</i>"]

    col_w = max((len(str(r.get("equipment", ""))) for r in rates), default=10)
    col_w = max(col_w, 10)

    sep = "+" + "-" * (col_w + 2) + "+" + "-" * 8 + "+" + "-" * 7 + "+" + "-" * 7 + "+"
    title_row = f"| {'Équipement':<{col_w}} | {'Taux':>6} | {'Dispo':>5} | {'Entrée':>5} |"

    table_lines = [sep, title_row, sep]
    for row in rates:
        equip = str(row.get("equipment", "—"))[:col_w]
        taux = float(row.get("taux_dispo") or 0)
        dispo = int(row.get("total_available") or 0)
        entry = int(row.get("total_entry") or 0)
        icon = "✅" if taux >= 80 else ("🟡" if taux >= 50 else "🔴")
        table_lines.append(
            f"| {equip:<{col_w}} | {icon}{taux:>4.0f}% | {dispo:>5} | {entry:>5} |"
        )
    table_lines.append(sep)

    table_str = "\n".join(table_lines)
    return [header + f"<pre>{table_str}</pre>"]


def fmt_stockout_risk(rows: list, period_label: str) -> list[str]:
    """
    rows = [{'equipment': str, 'intrant': str, 'structure': str, 'region': str}]
    Liste paginée par 20 des risques de rupture par équipement.
    """
    header = _header(f"Risques de rupture — {period_label}", "🔴")

    if not rows:
        return [header + "\n✅ <b>Aucun risque de rupture</b> détecté sur cette période."]

    pages = _paginate(rows, 20)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} cas</i>\n\n"
        else:
            page_header += f"<i>{total} cas détecté(s)</i>\n\n"

        items = []
        start_num = (page_idx - 1) * 20 + 1
        for i, row in enumerate(page, start=start_num):
            equipment = row.get("equipment", "—")
            intrant = row.get("intrant", "—")
            structure = row.get("structure", "—")
            region = row.get("region", "—")
            items.append(
                f"{i}. 🔴 <b>{intrant}</b> <i>({equipment})</i>\n"
                f"   🏥 {structure} — {region}"
            )
        messages.append(page_header + "\n".join(items))
    return messages
