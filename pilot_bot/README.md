# pilot_bot — Bot Telegram de pilotage pour chefs de projet LDC

> **Documentation pédagogique** — Ce guide décrit l'architecture complète du `pilot_bot`, un bot Telegram de prise de décision en lecture seule sur la base de données `lab_db`, ainsi que son interface d'administration web.

---

## Table des matières

1. [Architecture générale et infrastructure](#1-architecture-générale-et-infrastructure)
2. [Interface d'administration web](#2-interface-dadministration-web)
3. [Modules core](#3-modules-core)
4. [Package `queries/` — Requêtes métier SQL](#4-package-queries--requêtes-métier-sql)
5. [Package `formatters/` — Formatage des messages Telegram](#5-package-formatters--formatage-des-messages-telegram)
6. [Package `menus/` et `bot_handler.py`](#6-package-menus-et-bot_handlerpy)

---

## 1. Architecture générale et infrastructure

### Schéma de l'écosystème

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Utilisateurs Telegram                        │
│                    (Chefs de projet LDC)                            │
└────────────────────────────────────┬────────────────────────────────┘
                                      │
                        API Telegram (HTTPS)
                                      │
                                      ▼
        ┌─────────────────────────────────────────────────────┐
        │              nginx (Reverse Proxy)                   │
        │  Routes : /pilot/* → pilot_bot:5001                  │
        │           /api/*   → ldc_backend:8080                │
        └────────────────────┬────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────────────────┐
        │         pilot_bot (Flask + Gunicorn)               │
        │         Port : 5001                                 │
        │  - Webhook Telegram (/webhook/<TOKEN>)              │
        │  - Menus interactifs (reports, stocks, structures…) │
        │  - Sessions utilisateur (TTL 1800s)                 │
        └────────────────┬───────────────────────────────────┘
                         │
                 SQL (SELECT uniquement)
                         │
                         ▼
        ┌────────────────────────────────────────────────────┐
        │            PostgreSQL (lab_db:5432)                │
        │   Connexion READ-ONLY depuis pilot_bot             │
        │   Tables : report, intrant_mvt_data, structure…    │
        └────────────────────────────────────────────────────┘
```

### Arborescence du projet

```
pilot_bot/
├── Dockerfile                          # Image Docker (Python 3.11-slim)
├── entrypoint.sh                       # Script de démarrage (4 étapes)
├── requirements.txt                    # Dépendances Python
├── .env.example                        # Modèle de configuration
├── .env                                # Configuration locale (git-ignoré)
│
├── app.py                              # Application Flask (point d'entrée)
├── bot_handler.py                      # Dispatcher des commandes Telegram
├── database.py                         # Connexion PostgreSQL read-only + pool
├── session.py                          # Gestion des sessions utilisateur
├── security.py                         # Contrôle d'accès (chat_id → admin_db)
├── admin_db.py                         # Base SQLite admin (users, logs, events)
├── admin_routes.py                     # Blueprint Flask API REST admin
│
├── static/
│   └── admin/
│       └── index.html                  # Dashboard admin (self-contained HTML)
│
├── formatters/
│   └── telegram_formatter.py           # Messages structurés HTML pour Telegram
│
├── menus/
│   ├── main_menu.py                    # Menu principal
│   ├── reports_menu.py                 # Rapports
│   ├── stocks_menu.py                  # Stocks d'intrants
│   ├── structures_menu.py              # Structures sanitaires
│   └── users_menu.py                   # Comptes utilisateurs
│
└── queries/
    ├── report_queries.py               # SELECT sur les rapports
    ├── stock_queries.py                # SELECT sur les mouvements stocks
    ├── structure_queries.py            # SELECT sur les structures/régions
    └── user_queries.py                 # SELECT sur les comptes
```

---

## 2. Interface d'administration web

### Vue d'ensemble

L'interface admin est un dashboard web accessible à `https://ldc.lhspla-ci.org/pilot/admin/`. Elle est servie directement par Flask (pas de serveur statique séparé) et protégée par une clé secrète.

```
Navigateur admin
      │
      ▼
nginx /pilot/admin/* → pilot_bot:5001/admin/*
      │
      ▼
admin_routes.py (Blueprint Flask)
      │
      ├── GET /admin/         → sert static/admin/index.html
      └── GET/POST/DELETE /admin/api/* → API REST (protégée X-Admin-Key)
                │
                ▼
          admin_db.py (SQLite /app/data/admin.db)
          ├── authorized_users  (whitelist chat_ids)
          ├── interaction_logs  (historique des commandes)
          └── bot_events        (démarrages, erreurs, accès refusés)
```

### Fonctionnalités

| Section | Contenu |
|---------|---------|
| **Santé** | État DB SQLite, webhook Telegram configuré, uptime info |
| **Statistiques** | Utilisateurs actifs, interactions aujourd'hui, accès refusés, top 5 commandes |
| **Utilisateurs autorisés** | Liste + formulaire ajout (chat_id, nom, note) + révocation |
| **Interactions** | 50 dernières commandes avec filtre par chat_id |
| **Événements bot** | Démarrages, erreurs d'envoi, accès refusés, add/remove utilisateurs |

**Auto-refresh silencieux toutes les 30 secondes.**

### Modules admin

#### `admin_db.py` — Base SQLite

Gère 3 tables sans aucune dépendance externe (stdlib `sqlite3` uniquement) :

| Table | Rôle |
|-------|------|
| `authorized_users` | Whitelist des chat_ids avec note admin et statut actif/révoqué |
| `interaction_logs` | Journal de chaque commande reçue (chat_id, menu, commande, timestamp UTC) |
| `bot_events` | Événements système (startup, access_denied, user_added, send_error…) |

**Seed automatique :** au premier démarrage, les chat_ids présents dans `AUTHORIZED_CHAT_IDS` (env var) sont importés dans la DB. Ensuite, la DB fait autorité — l'env var sert de fallback si la DB est inaccessible.

**Révocation douce :** `remove_authorized_user()` met `is_active=0` sans supprimer la ligne. L'historique est conservé.

#### `admin_routes.py` — API REST

| Route | Méthode | Auth | Description |
|-------|---------|------|-------------|
| `/admin/` | GET | Non | Sert le dashboard HTML |
| `/admin/api/health` | GET | Oui | État du service |
| `/admin/api/stats` | GET | Oui | Statistiques agrégées |
| `/admin/api/users` | GET | Oui | Liste des utilisateurs |
| `/admin/api/users` | POST | Oui | Ajouter un utilisateur |
| `/admin/api/users/<chat_id>` | DELETE | Oui | Révoquer un utilisateur |
| `/admin/api/interactions` | GET | Oui | Journal des interactions |
| `/admin/api/events` | GET | Oui | Journal des événements |

**Authentification :** header `X-Admin-Key: <ADMIN_SECRET_KEY>` (géré automatiquement par le dashboard HTML via `localStorage`).

#### `static/admin/index.html` — Dashboard

Interface entièrement self-contained (CSS et JS inline, aucune CDN). Fonctionne sans accès internet depuis le VPS.

### Variables d'environnement admin

| Variable | Valeur par défaut | Description |
|----------|-------------------|-------------|
| `ADMIN_SECRET_KEY` | `change-me-admin-secret` | Clé d'authentification de l'interface admin. À changer impérativement. |
| `ADMIN_DB_PATH` | `/app/data/admin.db` | Chemin de la base SQLite dans le conteneur (monté en volume Docker). |

---

### Dockerfile

L'image utilise **Python 3.11-slim** (~200 Mo), optimisée pour les conteneurs.

```dockerfile
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1   # Pas de fichiers .pyc
    PYTHONUNBUFFERED=1          # Logs en temps réel (stdout/stderr)
    PIP_NO_CACHE_DIR=1          # Réduit la taille de l'image

# Utilisateur non-root (sécurité)
RUN adduser --disabled-password --gecos '' appuser
USER appuser

EXPOSE 5001
CMD ["/app/entrypoint.sh"]
```

**Points clés :**
- `PYTHONUNBUFFERED=1` : les logs Gunicorn apparaissent immédiatement dans `docker logs`
- Utilisateur `appuser` sans droits sudo : si le conteneur est compromis, l'attaquant n'a pas accès root à l'hôte
- Le port `5001` est le seul point d'accès réseau exposé

### entrypoint.sh — Les 4 étapes de démarrage

Le script s'exécute avec `set -e` (arrêt immédiat si une étape échoue) :

```
Étape 1 : Test de connexion lab_db (PostgreSQL)
         ↓ échec → arrêt (évite un démarrage silencieusement cassé)
         ↓ succès
Étape 2 : Initialisation de la base admin (SQLite)
         ↓ Crée les tables si absentes, importe les chat_ids depuis env var
         ↓
Étape 3 : Enregistrement du webhook Telegram
         ↓ TOKEN ou BASE_URL absent → avertissement (pas d'arrêt)
         ↓
Étape 4 : Démarrage de Gunicorn
         --bind 0.0.0.0:5001
         --workers 2
         --timeout 60
```

**Pourquoi enregistrer le webhook au démarrage ?** Chaque redéploiement peut changer l'URL ou le token. L'entrypoint garantit que Telegram sait toujours où envoyer les messages.

### Dépendances Python (`requirements.txt`)

| Paquet | Rôle |
|--------|------|
| `flask` | Framework web — routes HTTP + webhook |
| `flask-cors` | CORS pour les endpoints publics |
| `gunicorn` | Serveur WSGI production (multi-worker) |
| `SQLAlchemy` | ORM et pool de connexions PostgreSQL |
| `psycopg2-binary` | Adaptateur PostgreSQL natif |
| `httpx` | Client HTTP moderne pour l'API Telegram |
| `python-dotenv` | Chargement des variables `.env` |
| `APScheduler` | Planificateur (tâches périodiques) |
| `pytz` | Fuseaux horaires (Africa/Abidjan) |

### Variables d'environnement (`.env`)

| Variable | Exemple | Description |
|----------|---------|-------------|
| `TELEGRAM_BOT_TOKEN` | `1234567890:ABCD…` | Token obtenu via @BotFather |
| `BASE_URL` | `https://ldc.lhspla-ci.org/pilot` | URL publique HTTPS du webhook |
| `AUTHORIZED_CHAT_IDS` | `123456789,987654321` | IDs Telegram des chefs de projet autorisés (séparés par virgules). Utilisez `/id` dans le bot pour obtenir votre ID. |
| `LAB_DB_URL` | `postgresql://postgres:pwd@lab_db:5432/lab_db` | Injecté par docker-compose — ne pas modifier manuellement |
| `FLASK_DEBUG` | `false` | Toujours `false` en production |
| `SECRET_KEY` | chaîne aléatoire 32+ chars | Générer : `python -c "import secrets; print(secrets.token_hex(32))"` |
| `TIMEZONE` | `Africa/Abidjan` | Fuseau horaire (format IANA/pytz) |
| `SESSION_TTL_SECONDS` | `1800` | Durée de vie d'une session (30 min) |
| `ADMIN_SECRET_KEY` | chaîne forte | Clé d'accès au dashboard admin |
| `ADMIN_DB_PATH` | `/app/data/admin.db` | Injecté par docker-compose — ne pas modifier manuellement |

### Flux de démarrage récapitulatif

```
docker compose up pilot_bot
        ↓
Build image Python 3.11-slim + dépendances
        ↓
Conteneur lance entrypoint.sh
        ↓
[1] Test lab_db (PostgreSQL)  → échec = arrêt
        ↓
[2] Init admin DB (SQLite)    → crée tables + seed chat_ids
        ↓
[3] Webhook Telegram enregistré
        ↓
[4] Gunicorn démarre sur :5001
        ↓
nginx relaie /pilot/*         → pilot_bot:5001
        ↓
Messages Telegram → webhook → bot → lab_db (SELECT)
Admin navigateur  → /pilot/admin/ → dashboard HTML
```

---

## 3. Modules core

Les quatre modules fondamentaux orchestrent la réception des messages Telegram, le contrôle d'accès, l'accès aux données et la gestion du contexte conversationnel.

### `app.py` — Point d'entrée Flask et webhook Telegram

**Rôle :** expose l'endpoint unique qui reçoit les mises à jour de Telegram et coordonne les réponses.

```python
@app.route(f'/webhook/<token>', methods=['POST'])
def telegram_webhook(token):
    if token != TELEGRAM_BOT_TOKEN:
        return jsonify({'error': 'Unauthorized'}), 403

    message = request.json.get('message', {})
    chat_id  = str(message['chat']['id'])
    text     = message.get('text', '').strip()

    replies = bot_handler.handle_message(chat_id, text, username)
    for reply in replies:
        _send(chat_id, reply)          # Envoi via l'API Telegram
    return jsonify({'ok': True})
```

**Points clés :**
- **Sécurité par token** : L'URL contient le token Telegram comme paramètre. Seul Telegram (qui connaît ce token) peut poster à cette URL. Toute autre requête reçoit un `403`.
- **Vérification DB au démarrage** : `test_connection()` est appelé avant que Flask accepte des requêtes. Un service inaccessible à sa base de données ne démarre pas.
- **Endpoint `/health`** : Permet à Docker/nginx de vérifier la vivacité du service sans interagir avec Telegram.

---

### `security.py` — Whitelist et contrôle d'accès

**Rôle :** bloquer tout utilisateur dont le `chat_id` n'est pas explicitement autorisé.

```python
AUTHORIZED_CHAT_IDS: set = _load_whitelist()   # Chargé depuis .env au démarrage

def require_authorized(func):
    @functools.wraps(func)
    def wrapper(chat_id: str, *args, **kwargs):
        if chat_id not in AUTHORIZED_CHAT_IDS:
            return [UNAUTHORIZED_MSG.format(chat_id=chat_id)]
        return func(chat_id, *args, **kwargs)
    return wrapper
```

**Pourquoi une whitelist plutôt qu'une blacklist ?**
- **Plus sûr** : par défaut, personne n'a accès. L'administrateur ajoute explicitement chaque chef de projet.
- **Décorateur réutilisable** : `@require_authorized` peut être appliqué à n'importe quel handler sans dupliquer la logique.
- **Feedback utilisateur** : Le message d'accès refusé indique le `chat_id` à transmettre à l'administrateur, réduisant la friction administrative.

---

### `database.py` — Connexion read-only à lab_db

**Rôle :** gérer la connexion SQLAlchemy vers PostgreSQL avec une garantie stricte : **aucune écriture possible**.

```python
engine = create_engine(
    LAB_DB_URL,
    connect_args={"options": "-c default_transaction_read_only=on"},
    pool_size=3,
    max_overflow=5,
    pool_pre_ping=True,
    pool_recycle=300,
)
```

**Défense en profondeur à deux niveaux :**

| Niveau | Mécanisme | Effet |
|--------|-----------|-------|
| **PostgreSQL** | `default_transaction_read_only=on` | PostgreSQL refuse tout `INSERT`/`UPDATE`/`DELETE` avec une erreur explicite |
| **SQLAlchemy** | `autocommit=False`, `autoflush=False` | Pas de commit automatique, pas d'écriture implicite |

**Paramètres du pool :**
- `pool_size=3` / `max_overflow=5` : adapté à un bot à faible audience
- `pool_pre_ping=True` : vérifie la connexion avant usage (évite les connexions "zombies")
- `pool_recycle=300` : renouvelle les connexions toutes les 5 minutes

---

### `session.py` — État conversationnel en mémoire

**Rôle :** mémoriser le menu actif et les données contextuelles de chaque conversation Telegram.

```python
_sessions: dict = {}
# Clé : chat_id (str)
# Valeur : {'menu': str, 'data': dict, 'ts': float}

def get_session(chat_id: str) -> dict | None:
    sess = _sessions.get(chat_id)
    if sess and time.time() - sess['ts'] > SESSION_TTL:
        _sessions.pop(chat_id, None)   # Expiration automatique
        return None
    return sess
```

**Pourquoi un dict en mémoire (et pas Redis) ?**
- L'audience est limitée (≤5 chefs de projet) — un dict Python suffit amplement
- Pas de persistance nécessaire : si le bot redémarre, les utilisateurs relancent `/start`
- Zéro dépendance supplémentaire

**Ce module est aussi la solution à l'import circulaire** : il ne dépend d'aucun autre module du bot, donc tous peuvent l'importer librement sans créer de cycle.

---

## 4. Package `queries/` — Requêtes métier SQL

Ce package centralise toutes les interrogations de base de données. Chaque classe ne contient que des `SELECT` (read-only), organisés par domaine métier.

**Pattern commun à toutes les méthodes :**
```python
@staticmethod
def ma_methode(period_id: int):
    sql = text("SELECT ... WHERE period_id = :pid")
    try:
        session = Session()
        rows = session.execute(sql, {'pid': period_id}).mappings().all()
        return [dict(r) for r in rows]
    finally:
        release_session()   # Toujours libérer la connexion
```

### `report_queries.py` — Suivi des rapports

Répond aux questions : *Quelles structures ont soumis ? Qui est en retard ? Quel est le taux de couverture par région ?*

| Méthode | Signature | Description |
|---------|-----------|-------------|
| `get_current_period` | `() → dict` | Période active au jour J (date entre start_date et end_date) |
| `get_recent_periods` | `(limit: int) → list[dict]` | N dernières périodes (pour la sélection historique) |
| `get_reports_summary_by_period` | `(period_id) → list[dict]` | Rapports agrégés par statut (VALIDATED, SUBMITTED, REJECTED…) |
| `get_submission_rate_by_region` | `(period_id) → list[dict]` | Taux de soumission par région |
| `get_late_structures` | `(period_id) → list[dict]` | Structures actives sans rapport pour la période |
| `get_global_stats` | `(period_id) → dict` | Synthèse dashboard : totaux + comptes actifs |

**Exemple illustratif — structures en retard :**
```sql
-- Qui n'a pas encore soumis son rapport ?
SELECT DISTINCT s.name AS structure, d.name AS district, reg.name AS region
FROM structure s
JOIN district d   ON d.id  = s.district_id
JOIN region   reg ON reg.id = d.region_id
WHERE s.active = true
  AND NOT EXISTS (
      SELECT 1
      FROM account_structure acs
      JOIN account acc ON acc.id = acs.account_id AND acc.is_active = true
      JOIN report  r   ON r.account_id = acc.id
      WHERE acs.structure_id = s.id AND r.period_id = :pid
  )
ORDER BY reg.name, d.name, s.name;
```
> `NOT EXISTS` filtre par *absence de donnée* — pattern classique en audit et conformité.

---

### `stock_queries.py` — Suivi des stocks d'intrants

Répond aux questions : *Y a-t-il des ruptures ? Quels intrants sont en stock critique ?*

| Méthode | Signature | Description |
|---------|-----------|-------------|
| `get_stock_summary` | `(period_id) → list[dict]` | Entrées / distributions / disponible par intrant |
| `get_stockouts` | `(period_id) → list[dict]` | Intrants avec `available_stock = 0` (alerte critique) |
| `get_low_stocks` | `(period_id) → list[dict]` | Intrants dont `dispo < distribué` (top 30 les plus critiques) |
| `get_stock_counts` | `(period_id) → dict` | Compteurs rapides : intrants suivis, ruptures, stocks bas |

**Exemple — tri par criticité :**
```sql
ORDER BY (imd.available_stock::float / imd.distribution_stock) ASC
```
> `::float` force la division décimale (évite l'arrondi entier). Le ratio croissant place les urgences en tête.

---

### `structure_queries.py` — Couverture nationale

Répond aux questions : *Combien de structures sont actives ? Quelle est la couverture par région ?*

| Méthode | Signature | Description |
|---------|-----------|-------------|
| `get_national_coverage` | `() → dict` | Total structures : actives vs inactives |
| `get_structures_by_region` | `() → list[dict]` | Structures (actives/total) par région |
| `get_structures_in_region` | `(region_name) → list[dict]` | Structures d'une région par district |
| `get_inactive_structures` | `() → list[dict]` | Structures marquées inactives |
| `get_all_regions` | `() → list[dict]` | Référentiel complet des régions |

---

### `user_queries.py` — Comptes et utilisateurs

Répond aux questions : *Qui est actif ? Comment sont répartis les rôles ? Qui n'a pas soumis ?*

| Méthode | Signature | Description |
|---------|-----------|-------------|
| `get_accounts_summary` | `() → dict` | Total comptes : actifs vs inactifs |
| `get_accounts_by_role` | `() → list[dict]` | Comptes actifs par rôle (trié par nombre décroissant) |
| `get_inactive_accounts` | `() → list[dict]` | Comptes désactivés avec utilisateur et rôle |
| `get_accounts_without_reports` | `(period_id) → list[dict]` | Comptes actifs sans rapport pour la période |

---

### Tableau récapitulatif complet

| Module | Méthode | Entrée | Sortie |
|--------|---------|--------|--------|
| report_queries | `get_current_period` | — | `dict` |
| report_queries | `get_recent_periods` | `limit: int` | `list[dict]` |
| report_queries | `get_reports_summary_by_period` | `period_id: int` | `list[dict]` |
| report_queries | `get_submission_rate_by_region` | `period_id: int` | `list[dict]` |
| report_queries | `get_late_structures` | `period_id: int` | `list[dict]` |
| report_queries | `get_global_stats` | `period_id: int` | `dict` |
| stock_queries | `get_stock_summary` | `period_id: int` | `list[dict]` |
| stock_queries | `get_stockouts` | `period_id: int` | `list[dict]` |
| stock_queries | `get_low_stocks` | `period_id: int` | `list[dict]` |
| stock_queries | `get_stock_counts` | `period_id: int` | `dict` |
| structure_queries | `get_national_coverage` | — | `dict` |
| structure_queries | `get_structures_by_region` | — | `list[dict]` |
| structure_queries | `get_structures_in_region` | `region_name: str` | `list[dict]` |
| structure_queries | `get_inactive_structures` | — | `list[dict]` |
| structure_queries | `get_all_regions` | — | `list[dict]` |
| user_queries | `get_accounts_summary` | — | `dict` |
| user_queries | `get_accounts_by_role` | — | `list[dict]` |
| user_queries | `get_inactive_accounts` | — | `list[dict]` |
| user_queries | `get_accounts_without_reports` | `period_id: int` | `list[dict]` |

---

## 5. Package `formatters/` — Formatage des messages Telegram

### Vue d'ensemble

`telegram_formatter.py` transforme les données SQL brutes en messages HTML lisibles dans Telegram. Son rôle : prendre un `dict` ou une `list[dict]` et produire un `str` ou `list[str]` prêt à envoyer.

```
Données SQL brutes
        ↓
telegram_formatter.py
  - Calculs (%, barres)
  - Structuration (tableaux ASCII)
  - Pagination si nécessaire
        ↓
  str  ou  list[str]   (HTML Telegram)
        ↓
bot → API sendMessage (parse_mode="HTML")
        ↓
Utilisateur (beau et lisible sur mobile)
```

### La contrainte des 4000 caractères

L'API Telegram limite chaque message à **4000 caractères**. Deux stratégies sont utilisées :

- **Retour `str`** : pour les messages courts (tableaux de bord, synthèses)
- **Retour `list[str]`** : pour les listes longues — chaque élément est un message séparé

La fonction `split_if_too_long(text)` gère les cas limites en coupant **entre les lignes** (jamais au milieu d'une ligne).

### Fonctions utilitaires internes

| Fonction | Description | Exemple |
|----------|-------------|---------|
| `_sep(char, length)` | Ligne de séparation | `━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━` |
| `_safe_pct(n, d)` | Pourcentage sans division par zéro | `_safe_pct(50, 0)` → `0.0` |
| `_fmt_date(d)` | Date lisible JJ/MM/AAAA | `date(2026,3,26)` → `"26/03/2026"` |
| `_paginate(items, chunk)` | Découpe en sous-listes | `[1,2,3,4,5]` par 2 → `[[1,2],[3,4],[5]]` |

### Tableaux ASCII dans `<pre>` — pourquoi ?

Telegram affiche le contenu des balises `<pre>` en police **monospaced** (Courier New). Cela garantit l'alignement des colonnes sur tous les clients (Android, iOS, Web).

```
<pre>
+──────────────+────────+───────+──────+
| Région       | Soumis | Total |  %   |
+──────────────+────────+───────+──────+
| Abidjan      |     14 |    15 | 93.3 |
| Yamoussoukro |      8 |    10 | 80.0 |
+──────────────+────────+───────+──────+
</pre>
```

Les largeurs de colonnes sont **calculées dynamiquement** selon le contenu réel pour éviter les débordements.

### API publique — tableau récapitulatif

| Fonction | Sortie | Pagination | Description |
|----------|--------|-----------|-------------|
| `fmt_header(title, icon)` | `str` | — | En-tête `<b>` + séparateur ━ |
| `progress_bar(pct, width)` | `str` | — | `[██████░░░░░░] 50%` |
| `fmt_dashboard(period, reports, stocks, users)` | `str` | — | Tableau de bord complet |
| `fmt_reports_by_status(rows, label)` | `str` | — | Statuts avec barres de progression |
| `fmt_submission_by_region(rows, label)` | `str` | — | Tableau ASCII régions |
| `fmt_late_structures(rows, label)` | `list[str]` | 20/msg | Structures en retard |
| `fmt_stock_summary(rows, label)` | `list[str]` | 15 lignes/msg | Résumé stocks (tableau) |
| `fmt_stockouts(rows, label)` | `list[str]` | 20/msg | Ruptures de stock |
| `fmt_low_stocks(rows, label)` | `list[str]` | 20/msg | Stocks bas avec icône criticité |
| `fmt_national_coverage(data, by_region)` | `str` | — | Barre nationale + tableau régional |
| `fmt_inactive_structures(rows)` | `list[str]` | 20/msg | Structures inactives |
| `fmt_accounts_summary(data, by_role)` | `str` | — | Résumé comptes + tableau rôles |
| `fmt_inactive_accounts(rows)` | `list[str]` | 20/msg | Comptes inactifs |
| `fmt_periods_list(periods)` | `str` | — | Liste numérotée pour sélection |
| `split_if_too_long(text)` | `list[str]` | 4000 chars | Découpe générique |

**Exemple de rendu — tableau de bord :**
```
📊 Tableau de bord LDC
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📅 Période : Janvier 2026
   01/01/2026 → 31/01/2026

📋 Rapports
   Total         : 150
   ✅ Validés    : 120 (80.0%)
   📤 Soumis     : 20 (13.3%)
   ❌ Rejetés    : 10 (6.7%)

📦 Stocks
   Intrants suivis : 35
   🔴 Ruptures     : 2
   🟡 Stocks bas   : 8

👥 Utilisateurs
   Total   : 20  |  Actifs : 18  |  Inactifs : 2
   Taux activité : [███████████░] 90%

─────────────────────────────────
🕐 Données en temps réel
```

**Icônes d'alerte dans `fmt_low_stocks` :**

| Ratio dispo/distribué | Icône | Signification |
|-----------------------|-------|---------------|
| < 0.10 | 🔴 | Critique — intervention urgente |
| 0.10 – 0.25 | 🟠 | Danger — surveiller de près |
| ≥ 0.25 | 🟡 | Alerte — à commander |

---

## 6. Package `menus/` et `bot_handler.py`

### Pattern "Single Entry Point"

**Toutes les interactions passent par `handle_message()`**, décorée par `@require_authorized`. Cette centralisation garantit que le contrôle d'accès est appliqué une seule fois, de manière cohérente.

```
Message Telegram
       ↓
handle_message()  ← @require_authorized
       ↓
  Commandes globales (/start, /dashboard, /retard…)
       ↓ (si non matché)
  DISPATCH[session['menu']] → handler du menu actif
       ↓
  list[str]  →  app.py  →  Telegram API
```

### Table DISPATCH

```python
DISPATCH: dict = {
    'main':        handle_main_menu,
    'reports':     handle_reports_menu,
    'stocks':      handle_stocks_menu,
    'structures':  handle_structures_menu,
    'users':       handle_users_menu,
}
```

Chaque clé correspond à la valeur de `session['menu']`. Le dispatch est une simple lecture de dictionnaire — O(1), sans `if/elif` en cascade.

### Structure d'une session

```python
{
    'menu': 'reports',          # Menu actif (clé dans DISPATCH)
    'data': {
        'state': 'reports_period',   # Sous-état (facultatif)
        'periods': {                 # Données contextuelles
            '1': {'id': 12, 'period_name': 'Janvier 2026'},
            '2': {'id': 11, 'period_name': 'Décembre 2025'},
        }
    },
    'ts': 1742905200.0          # Timestamp (TTL 1800s)
}
```

### Arbre de navigation complet

```
COMMANDES GLOBALES (accessibles depuis n'importe quel menu)
├─ /start, /menu    → Réinitialise + Menu principal
├─ /dashboard       → Tableau de bord complet
├─ /rapports        → Menu Rapports
├─ /stocks          → Menu Stocks
├─ /structures      → Menu Structures
├─ /users           → Menu Utilisateurs
├─ /retard          → Structures en retard (période courante)
├─ /rupture         → Intrants en rupture (période courante)
├─ /id              → Afficher son chat_id
└─ /help, /aide     → Liste des commandes

MENU PRINCIPAL  [menu='main']
├─ 1  → Menu Rapports       [menu='reports']
├─ 2  → Menu Stocks         [menu='stocks']
├─ 3  → Menu Structures     [menu='structures']
├─ 4  → Menu Utilisateurs   [menu='users']
└─ 5  → Tableau de bord     [appelle _build_dashboard()]

MENU RAPPORTS  [menu='reports']
├─ 1  → Statut des rapports (période courante)
├─ 2  → Structures en retard
├─ 3  → Taux de soumission par région
├─ 4  → Choisir une autre période
│       └─ [state='reports_period'] → liste des 10 dernières périodes
│          → choix numéro → résumé pour cette période → retour normal
└─ 0  → Retour Menu principal

MENU STOCKS  [menu='stocks']
├─ 1  → Bilan global (période courante)
├─ 2  → Intrants en rupture
├─ 3  → Stocks faibles
└─ 0  → Retour Menu principal

MENU STRUCTURES  [menu='structures']
├─ 1  → Couverture nationale
├─ 2  → Vue par région
│       └─ [state='structures_region'] → liste de toutes les régions
│          → choix numéro → structures par district → retour normal
├─ 3  → Structures inactives
└─ 0  → Retour Menu principal

MENU UTILISATEURS  [menu='users']
├─ 1  → Bilan des comptes
├─ 2  → Comptes inactifs
├─ 3  → Comptes sans rapport (période courante)
└─ 0  → Retour Menu principal

Dans tous les menus :
  • Toute entrée non reconnue → réafficher le menu courant
  • 0                         → retour menu principal
```

### Mécanisme anti-import-circulaire

Sans précaution, les imports mutuels entre `bot_handler` et les menus créeraient un `ImportError`.

**Trois stratégies combinées :**

**1. `session.py` comme module neutre**

Il ne dépend d'aucun autre module du bot. Tous peuvent l'importer sans risque.

```
session.py  ←  bot_handler.py
session.py  ←  menus/main_menu.py
session.py  ←  menus/reports_menu.py
…
```

**2. Imports différés (lazy imports)**

Les imports de `bot_handler` dans les menus se font à l'intérieur de la fonction, au moment de l'appel :

```python
# menus/main_menu.py
def handle_main_menu(chat_id, msg, session_data):
    if msg == '5':
        from bot_handler import _build_dashboard  # ← lazy, pas en début de fichier
        return _build_dashboard()
```

**3. Imports différés des textes de menus**

```python
# menus/main_menu.py
def handle_main_menu(chat_id, msg, session_data):
    from menus.reports_menu import REPORTS_MENU_TEXT   # ← lazy
    if msg == '1':
        set_session(chat_id, 'reports')
        return [REPORTS_MENU_TEXT]
```

**Graphe d'imports résultant (sans cycle) :**

```
bot_handler.py
    ├─→ session.py
    ├─→ menus/*.py
    │       ├─→ session.py
    │       └─→ formatters/
    └─→ queries/
            └─→ database.py
```

### Commandes globales — référence complète

| Commande | Effet | État session après |
|----------|-------|-------------------|
| `/start`, `/menu` | Menu principal (reset session) | `menu='main'` |
| `/dashboard` | Tableau de bord agrégé | inchangé |
| `/rapports` | Menu Rapports | `menu='reports'` |
| `/stocks`, `/stock` | Menu Stocks | `menu='stocks'` |
| `/structures` | Menu Structures | `menu='structures'` |
| `/users`, `/utilisateurs` | Menu Utilisateurs | `menu='users'` |
| `/retard` | Structures sans rapport (période courante) | inchangé |
| `/rupture` | Intrants en rupture (période courante) | inchangé |
| `/id` | Affiche `chat_id` de l'utilisateur | inchangé |
| `/help`, `/aide` | Message d'aide complet | inchangé |

### Résumé des principes d'architecture

| Principe | Implémentation |
|----------|----------------|
| Point d'entrée unique | `handle_message()` avec `@require_authorized` |
| Dispatch centralisé | Table `DISPATCH` selon `session['menu']` |
| État conversationnel | Dict `{'menu', 'data', 'ts'}` + TTL 30 min |
| Anti-imports circulaires | `session.py` neutre + lazy imports |
| Réponses cohérentes | Tous les handlers retournent `list[str]` HTML |
| Réaffichage du menu | Entrée invalide → réafficher le menu courant |
| Navigation retour | Option `0` → `set_session(chat_id, 'main')` |

---

---

## Déploiement

### Prérequis

1. **Créer un bot Telegram** via [@BotFather](https://t.me/botfather) → `/newbot` → copier le token
2. **Obtenir votre chat_id** : envoyer `/id` au bot après la première authentification réussie (ou utiliser [@userinfobot](https://t.me/userinfobot))

### Configuration des secrets (sur le VPS)

```bash
# Éditer pilot_bot/.env
TELEGRAM_BOT_TOKEN=<token_botfather>
BASE_URL=https://ldc.lhspla-ci.org/pilot
AUTHORIZED_CHAT_IDS=<chat_id_chef1>,<chat_id_chef2>
ADMIN_SECRET_KEY=<cle_admin_forte_32chars>
```

```bash
# Éditer .env (racine du projet)
PILOT_ADMIN_SECRET=<meme_cle_admin_forte>
```

### Déploiement

```bash
# 1. Récupérer les sources
git pull

# 2. Builder et démarrer
docker compose up -d --build pilot_bot

# 3. Vérifier les logs
docker compose logs pilot_bot --tail 50
```

**Sortie attendue :**
```
── Vérification connexion lab_db...
✅ Connexion lab_db établie (read-only).
── Initialisation base admin...
Base admin initialisee.
── Configuration du webhook Telegram...
Webhook : https://ldc.lhspla-ci.org/pilot/webhook/<token>
Réponse : {'ok': True, 'result': True, ...}
── Démarrage Gunicorn (port 5001)...
[INFO] Starting gunicorn 22.0.0
[INFO] Listening at: http://0.0.0.0:5001
```

### Vérification post-déploiement

```bash
# Santé HTTP
curl https://ldc.lhspla-ci.org/pilot/health
# → {"status": "ok", "service": "pilot_bot"}

# API admin (remplacer <key> par ADMIN_SECRET_KEY)
curl -H "X-Admin-Key: <key>" https://ldc.lhspla-ci.org/pilot/admin/api/health
# → {"status":"ok","db_connected":true,"webhook_configured":true,...}
```

### Accès aux interfaces

| Interface | URL | Auth |
|-----------|-----|------|
| Dashboard admin | `https://ldc.lhspla-ci.org/pilot/admin/` | Clé admin dans le formulaire |
| Bot Telegram | Chercher `@ldc_pilot_bot` | Whitelist chat_id |

### Ajouter un chef de projet

**Via le dashboard admin :**
1. Ouvrir `https://ldc.lhspla-ci.org/pilot/admin/`
2. Saisir la clé admin → connexion
3. Section "Utilisateurs autorisés" → "+ Ajouter"
4. Saisir le `chat_id`, un nom et une note → Ajouter

**Via Telegram :**
Le chef de projet envoie n'importe quel message au bot → reçoit un message `Accès refusé` contenant son `chat_id` → le transmettre à l'admin pour ajout.

### Mettre à jour AUTHORIZED_CHAT_IDS

Les chat_ids ajoutés/révoqués via le dashboard admin sont **immédiatement actifs** (vérification en temps réel dans la DB à chaque message). Aucun redémarrage nécessaire.

*Généré automatiquement — Architecture pilot_bot v1.1*
