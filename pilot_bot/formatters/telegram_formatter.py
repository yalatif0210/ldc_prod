"""
telegram_formatter.py
---------------------
Formate les données des requêtes SQL en messages HTML pour Telegram.

Contraintes :
  - Max 4000 caractères par message (MAX_MSG_LEN)
  - Balises HTML autorisées : <b>, <i>, <code>, <pre>
  - Tableaux dans <pre> (ASCII art)
  - Les listes longues sont paginées → list[str]
"""

from __future__ import annotations
from math import floor
from datetime import date as Date

MAX_MSG_LEN = 4000

# ---------------------------------------------------------------------------
# Helpers internes
# ---------------------------------------------------------------------------

def _sep(char: str = "━", length: int = 32) -> str:
    return char * length


def _safe_pct(numerator: int, denominator: int) -> float:
    """Retourne le pourcentage (0-100) ou 0.0 si dénominateur est nul."""
    if not denominator:
        return 0.0
    return min(100.0, round(numerator * 100 / denominator, 1))


def _fmt_date(d) -> str:
    """Formate une date (date, str) lisiblement."""
    if d is None:
        return "—"
    if isinstance(d, Date):
        return d.strftime("%d/%m/%Y")
    return str(d)


def _paginate(items: list, chunk: int) -> list[list]:
    """Découpe une liste en sous-listes de taille 'chunk'."""
    return [items[i : i + chunk] for i in range(0, len(items), chunk)]


# ---------------------------------------------------------------------------
# API publique
# ---------------------------------------------------------------------------

def fmt_header(title: str, icon: str = "") -> str:
    """Titre en gras avec séparateur ━."""
    prefix = f"{icon} " if icon else ""
    return f"<b>{prefix}{title}</b>\n{_sep()}\n"


def progress_bar(pct: int, width: int = 12) -> str:
    """
    Barre de progression ASCII.
    Exemple (50%, width=12) : [██████░░░░░░] 50%
    """
    pct = max(0, min(100, int(pct)))
    filled = floor(width * pct / 100)
    empty = width - filled
    bar = "█" * filled + "░" * empty
    return f"[{bar}] {pct}%"


def fmt_dashboard(
    period: dict,
    report_stats: dict,
    stock_counts: dict,
    user_stats: dict,
) -> str:
    """
    Tableau de bord synthèse (un seul message).

    period       : {period_name, start_date, end_date}
    report_stats : {total_reports, validated, submitted, rejected,
                    active_structures, active_accounts}
    stock_counts : {tracked_intrants, stockouts, low_stocks}
    user_stats   : {total, active, inactive}
    """
    total_r = report_stats.get("total_reports", 0)
    validated = report_stats.get("validated", 0)
    submitted = report_stats.get("submitted", 0)
    rejected = report_stats.get("rejected", 0)
    active_str = report_stats.get("active_structures", 0)
    active_acc = report_stats.get("active_accounts", 0)

    tracked = stock_counts.get("tracked_intrants", 0)
    stockouts = stock_counts.get("stockouts", 0)
    low_stocks = stock_counts.get("low_stocks", 0)

    u_total = user_stats.get("total", 0)
    u_active = user_stats.get("active", 0)
    u_inactive = user_stats.get("inactive", 0)

    val_pct = _safe_pct(validated, total_r)
    sub_pct = _safe_pct(submitted, total_r)
    rej_pct = _safe_pct(rejected, total_r)
    usr_pct = _safe_pct(u_active, u_total)

    lines = [
        fmt_header("Tableau de bord LDC", "📊"),
        f"📅 <b>Période :</b> {period.get('period_name', '—')}",
        f"   <i>{_fmt_date(period.get('start_date'))} → {_fmt_date(period.get('end_date'))}</i>",
        "",
        "📋 <b>Rapports</b>",
        f"   Total         : <b>{total_r}</b>",
        f"   ✅ Validés    : {validated} ({val_pct}%)",
        f"   📤 Soumis     : {submitted} ({sub_pct}%)",
        f"   ❌ Rejetés    : {rejected} ({rej_pct}%)",
        f"   Structures act.: {active_str} | Comptes act.: {active_acc}",
        "",
        "📦 <b>Stocks</b>",
        f"   Intrants suivis : <b>{tracked}</b>",
        f"   🔴 Ruptures     : <b>{stockouts}</b>",
        f"   🟡 Stocks bas   : <b>{low_stocks}</b>",
        "",
        "👥 <b>Utilisateurs</b>",
        f"   Total   : {u_total}  |  Actifs : {u_active}  |  Inactifs : {u_inactive}",
        f"   Taux activité : {progress_bar(int(usr_pct))}",
        "",
        _sep("─"),
        "🕐 <i>Données en temps réel</i>",
    ]
    return "\n".join(lines)


def fmt_reports_by_status(rows: list, period_label: str) -> str:
    """
    rows = [{'status': str, 'count': int}]
    Affiche chaque statut avec barre de progression et pourcentage.
    """
    STATUS_ICONS = {
        "VALIDATED": "✅",
        "SUBMITTED": "📤",
        "REJECTED": "❌",
        "DRAFT": "📝",
        "IN_PROGRESS": "🔄",
    }

    total = sum(r.get("count", 0) for r in rows)
    lines = [
        fmt_header(f"Rapports par statut — {period_label}", "📋"),
        f"Total : <b>{total}</b> rapport(s)\n",
    ]

    for row in rows:
        status = str(row.get("status", "—"))
        count = int(row.get("count", 0))
        pct = _safe_pct(count, total)
        icon = STATUS_ICONS.get(status.upper(), "•")
        bar = progress_bar(int(pct))
        lines.append(f"{icon} <b>{status}</b>")
        lines.append(f"   {count} rapport(s)  {bar}")
        lines.append("")

    return "\n".join(lines).rstrip()


def fmt_submission_by_region(rows: list, period_label: str) -> str:
    """
    rows = [{'region': str, 'total_structures': int, 'submitted': int}]
    Tableau ASCII dans <pre>, colonnes : Région / Soumis / Total / %
    """
    header_line = fmt_header(f"Soumission par région — {period_label}", "🗺️")

    # Calcul largeur colonne Région
    col_w = max((len(str(r.get("region", ""))) for r in rows), default=10)
    col_w = max(col_w, 6)  # minimum "Région"

    sep_line = "+" + "-" * (col_w + 2) + "+" + "-" * 8 + "+" + "-" * 7 + "+" + "-" * 6 + "+"
    title_row = (
        f"| {'Région':<{col_w}} | {'Soumis':>6} | {'Total':>5} | {'%':>4} |"
    )

    table_lines = [sep_line, title_row, sep_line]

    for row in rows:
        region = str(row.get("region", "—"))
        total_s = int(row.get("total_structures", 0))
        submitted = int(row.get("submitted", 0))
        pct = _safe_pct(submitted, total_s)
        table_lines.append(
            f"| {region:<{col_w}} | {submitted:>6} | {total_s:>5} | {pct:>4.0f} |"
        )

    table_lines.append(sep_line)

    table_str = "\n".join(table_lines)
    return f"{header_line}<pre>{table_str}</pre>"


def fmt_late_structures(rows: list, period_label: str) -> list[str]:
    """
    rows = [{'structure': str, 'district': str, 'region': str}]
    Retourne list[str] — paginé par 20.
    Si vide : message succès "Aucun retard".
    """
    header = fmt_header(f"Structures en retard — {period_label}", "⏰")

    if not rows:
        return [header + "\n✅ <b>Aucun retard !</b> Toutes les structures ont soumis leur rapport."]

    pages = _paginate(rows, 20)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} structures</i>\n\n"
        else:
            page_header += f"<i>{total} structure(s) en retard</i>\n\n"

        items = []
        start_num = (page_idx - 1) * 20 + 1
        for i, row in enumerate(page, start=start_num):
            structure = row.get("structure", "—")
            district = row.get("district", "—")
            region = row.get("region", "—")
            items.append(f"{i}. <b>{structure}</b>\n   📍 {district} / {region}")

        messages.append(page_header + "\n".join(items))

    return messages


def fmt_stock_summary(rows: list, period_label: str) -> list[str]:
    """
    rows = [{'intrant': str, 'total_entree': int, 'total_distribue': int,
              'total_disponible': int, 'nb_structures': int}]
    Tableau ASCII <pre> — paginé par 15 lignes.
    """
    header_base = fmt_header(f"Résumé des stocks — {period_label}", "📦")

    if not rows:
        return [header_base + "\n<i>Aucune donnée de stock disponible.</i>"]

    # Largeur colonne intrant
    col_w = max((len(str(r.get("intrant", ""))) for r in rows), default=8)
    col_w = max(col_w, 7)

    def _build_table(chunk: list) -> str:
        sep = "+" + "-" * (col_w + 2) + "+" + "-" * 9 + "+" + "-" * 11 + "+" + "-" * 7 + "+"
        title_row = f"| {'Intrant':<{col_w}} | {'Entrée':>7} | {'Distribué':>9} | {'Dispo':>5} |"
        lines = [sep, title_row, sep]
        for row in chunk:
            intrant = str(row.get("intrant", "—"))[:col_w]
            entree = int(row.get("total_entree", 0))
            distrib = int(row.get("total_distribue", 0))
            dispo = int(row.get("total_disponible", 0))
            lines.append(
                f"| {intrant:<{col_w}} | {entree:>7} | {distrib:>9} | {dispo:>5} |"
            )
        lines.append(sep)
        return "\n".join(lines)

    pages = _paginate(rows, 15)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        header = header_base
        if len(pages) > 1:
            header += f"<i>Page {page_idx}/{len(pages)} — {total} intrant(s)</i>\n\n"
        table = _build_table(page)
        messages.append(header + f"<pre>{table}</pre>")

    return messages


def fmt_stockouts(rows: list, period_label: str) -> list[str]:
    """
    rows = [{'intrant': str, 'structure': str, 'district': str, 'region': str}]
    Liste paginée par 20. Message succès si vide.
    """
    header = fmt_header(f"Ruptures de stock — {period_label}", "🔴")

    if not rows:
        return [header + "\n✅ <b>Aucune rupture de stock</b> détectée sur cette période."]

    pages = _paginate(rows, 20)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} rupture(s)</i>\n\n"
        else:
            page_header += f"<i>{total} rupture(s)</i>\n\n"

        items = []
        start_num = (page_idx - 1) * 20 + 1
        for i, row in enumerate(page, start=start_num):
            intrant = row.get("intrant", "—")
            structure = row.get("structure", "—")
            district = row.get("district", "—")
            region = row.get("region", "—")
            items.append(
                f"{i}. 🔴 <b>{intrant}</b>\n"
                f"   🏥 {structure}\n"
                f"   📍 {district} / {region}"
            )

        messages.append(page_header + "\n".join(items))

    return messages


def fmt_low_stocks(rows: list, period_label: str) -> list[str]:
    """
    rows = [{'intrant': str, 'structure': str, 'region': str,
              'dispo': int, 'distribue': int}]
    Affiche avec ratio dispo/distribué et icône d'alerte.
    """
    header = fmt_header(f"Stocks bas — {period_label}", "🟡")

    if not rows:
        return [header + "\n✅ <b>Aucun stock bas</b> détecté sur cette période."]

    def _alert_icon(dispo: int, distribue: int) -> str:
        if distribue == 0:
            return "🟡"
        ratio = dispo / distribue
        if ratio < 0.1:
            return "🔴"
        if ratio < 0.25:
            return "🟠"
        return "🟡"

    pages = _paginate(rows, 20)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} alerte(s)</i>\n\n"
        else:
            page_header += f"<i>{total} alerte(s)</i>\n\n"

        items = []
        start_num = (page_idx - 1) * 20 + 1
        for i, row in enumerate(page, start=start_num):
            intrant = row.get("intrant", "—")
            structure = row.get("structure", "—")
            region = row.get("region", "—")
            dispo = int(row.get("dispo", 0))
            distribue = int(row.get("distribue", 0))
            icon = _alert_icon(dispo, distribue)
            ratio_str = f"{dispo}/{distribue}" if distribue else f"{dispo}/—"
            items.append(
                f"{i}. {icon} <b>{intrant}</b>\n"
                f"   🏥 {structure} <i>({region})</i>\n"
                f"   Dispo/Distribué : <code>{ratio_str}</code>"
            )

        messages.append(page_header + "\n".join(items))

    return messages


def fmt_national_coverage(data: dict, by_region: list) -> str:
    """
    data      : {total, active, inactive}
    by_region : [{'region': str, 'total': int, 'active': int}]
    Barre de progression nationale + tableau par région.
    """
    total = int(data.get("total", 0))
    active = int(data.get("active", 0))
    inactive = int(data.get("inactive", 0))
    nat_pct = _safe_pct(active, total)

    lines = [
        fmt_header("Couverture nationale — Structures", "🏥"),
        f"Total : <b>{total}</b>  |  Actives : <b>{active}</b>  |  Inactives : <b>{inactive}</b>",
        f"Taux  : {progress_bar(int(nat_pct))}",
        "",
    ]

    if by_region:
        col_w = max((len(str(r.get("region", ""))) for r in by_region), default=6)
        col_w = max(col_w, 6)
        sep = "+" + "-" * (col_w + 2) + "+" + "-" * 7 + "+" + "-" * 8 + "+" + "-" * 6 + "+"
        title_row = f"| {'Région':<{col_w}} | {'Act.':>5} | {'Total':>6} | {'%':>4} |"
        table_lines = [sep, title_row, sep]

        for row in by_region:
            region = str(row.get("region", "—"))
            r_total = int(row.get("total", 0))
            r_active = int(row.get("active", 0))
            r_pct = _safe_pct(r_active, r_total)
            table_lines.append(
                f"| {region:<{col_w}} | {r_active:>5} | {r_total:>6} | {r_pct:>4.0f} |"
            )

        table_lines.append(sep)
        lines.append("<pre>" + "\n".join(table_lines) + "</pre>")

    return "\n".join(lines)


def fmt_inactive_structures(rows: list) -> list[str]:
    """
    rows = [{'structure': str, 'district': str, 'region': str}]
    Liste paginée par 20.
    """
    header = fmt_header("Structures inactives", "🏚️")

    if not rows:
        return [header + "\n✅ <b>Aucune structure inactive.</b>"]

    pages = _paginate(rows, 20)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} structure(s)</i>\n\n"
        else:
            page_header += f"<i>{total} structure(s) inactive(s)</i>\n\n"

        items = []
        start_num = (page_idx - 1) * 20 + 1
        for i, row in enumerate(page, start=start_num):
            structure = row.get("structure", "—")
            district = row.get("district", "—")
            region = row.get("region", "—")
            items.append(f"{i}. <b>{structure}</b>\n   📍 {district} / {region}")

        messages.append(page_header + "\n".join(items))

    return messages


def fmt_accounts_summary(data: dict, by_role: list) -> str:
    """
    data    : {total, active, inactive}
    by_role : [{'role': str, 'total': int, 'active': int}]
    """
    total = int(data.get("total", 0))
    active = int(data.get("active", 0))
    inactive = int(data.get("inactive", 0))
    act_pct = _safe_pct(active, total)

    lines = [
        fmt_header("Résumé des comptes utilisateurs", "👥"),
        f"Total    : <b>{total}</b>",
        f"Actifs   : <b>{active}</b>  |  Inactifs : <b>{inactive}</b>",
        f"Activité : {progress_bar(int(act_pct))}",
        "",
    ]

    if by_role:
        col_w = max((len(str(r.get("role", ""))) for r in by_role), default=4)
        col_w = max(col_w, 4)
        sep = "+" + "-" * (col_w + 2) + "+" + "-" * 7 + "+" + "-" * 8 + "+" + "-" * 6 + "+"
        title_row = f"| {'Rôle':<{col_w}} | {'Act.':>5} | {'Total':>6} | {'%':>4} |"
        table_lines = [sep, title_row, sep]

        for row in by_role:
            role = str(row.get("role", "—"))
            r_total = int(row.get("total", 0))
            r_active = int(row.get("active", 0))
            r_pct = _safe_pct(r_active, r_total)
            table_lines.append(
                f"| {role:<{col_w}} | {r_active:>5} | {r_total:>6} | {r_pct:>4.0f} |"
            )

        table_lines.append(sep)
        lines.append("<b>Détail par rôle :</b>")
        lines.append("<pre>" + "\n".join(table_lines) + "</pre>")

    return "\n".join(lines)


def fmt_inactive_accounts(rows: list) -> list[str]:
    """
    rows = [{'user_name': str, 'username': str, 'role': str}]
    Liste paginée par 20.
    """
    header = fmt_header("Comptes inactifs", "🔒")

    if not rows:
        return [header + "\n✅ <b>Aucun compte inactif.</b>"]

    pages = _paginate(rows, 20)
    total = len(rows)
    messages = []

    for page_idx, page in enumerate(pages, start=1):
        page_header = header
        if len(pages) > 1:
            page_header += f"<i>Page {page_idx}/{len(pages)} — {total} compte(s)</i>\n\n"
        else:
            page_header += f"<i>{total} compte(s) inactif(s)</i>\n\n"

        items = []
        start_num = (page_idx - 1) * 20 + 1
        for i, row in enumerate(page, start=start_num):
            user_name = row.get("user_name", "—")
            username = row.get("username", "—")
            role = row.get("role", "—")
            items.append(
                f"{i}. <b>{user_name}</b> (<code>@{username}</code>)\n"
                f"   🏷️ {role}"
            )

        messages.append(page_header + "\n".join(items))

    return messages


def fmt_periods_list(periods: list) -> str:
    """
    periods = [{'id': int, 'period_name': str, 'start_date': date, 'end_date': date}]
    Affiche numérotée pour que l'utilisateur choisisse par numéro (1, 2, 3...).
    """
    lines = [
        fmt_header("Sélection de la période", "📅"),
        "Tapez le <b>numéro</b> de la période souhaitée :\n",
    ]

    for i, p in enumerate(periods, start=1):
        name = p.get("period_name", "—")
        start = _fmt_date(p.get("start_date"))
        end = _fmt_date(p.get("end_date"))
        lines.append(f"<b>{i}.</b> {name}")
        lines.append(f"   <i>{start} → {end}</i>")
        lines.append("")

    lines.append(_sep("─"))
    lines.append("💡 <i>Répondez avec le numéro uniquement (ex: 1)</i>")

    return "\n".join(lines)


def split_if_too_long(text: str) -> list[str]:
    """
    Découpe un message > MAX_MSG_LEN en plusieurs parties.
    La coupure se fait entre les lignes (jamais au milieu d'une ligne).
    """
    if len(text) <= MAX_MSG_LEN:
        return [text]

    parts = []
    current_lines: list[str] = []
    current_len = 0

    for line in text.split("\n"):
        line_len = len(line) + 1  # +1 pour le \n

        # Si une seule ligne dépasse MAX_MSG_LEN, on la force quand même
        if current_len + line_len > MAX_MSG_LEN and current_lines:
            parts.append("\n".join(current_lines))
            current_lines = [line]
            current_len = line_len
        else:
            current_lines.append(line)
            current_len += line_len

    if current_lines:
        parts.append("\n".join(current_lines))

    return parts
