# CI/CD — Pipeline & Procédures

## Table des matières

1. [Architecture générale](#1-architecture-générale)
2. [Environnements](#2-environnements)
3. [Pipeline CI — Intégration continue](#3-pipeline-ci--intégration-continue)
4. [Pipeline CD — Staging (automatique)](#4-pipeline-cd--staging-automatique)
5. [Pipeline CD — Production (manuel)](#5-pipeline-cd--production-manuel)
6. [Procédure de basculement develop ↔ main](#6-procédure-de-basculement-develop--main)
7. [Scripts utilitaires](#7-scripts-utilitaires)
8. [Commandes VPS utiles](#8-commandes-vps-utiles)

---

## 1. Architecture générale

```
Code local
    │
    ├── develop ──► push ──► CI (lint + build) ──► CD Staging (auto)
    │
    └── main ────► tag vX.Y.Z ──► CI (lint + build) ──► CD Production (approbation manuelle)
```

**Registry d'images :** GitHub Container Registry (`ghcr.io/yalatif0210`)

**Services buildés :**
| Image | Source |
|---|---|
| `ghcr.io/yalatif0210/ldc_frontend` | `./ldc_frontend` |
| `ghcr.io/yalatif0210/ldc_backend` | `./ldc_backend` |
| `ghcr.io/yalatif0210/support_bot` | `./support_ldc` |
| `ghcr.io/yalatif0210/pilot_bot` | `./pilot_bot` |

---

## 2. Environnements

| Environnement | URL | Branche | Tag image | Port | Approbation |
|---|---|---|---|---|---|
| **Staging** | `http://VPS:8081` | `develop` | `staging-XXXXXXX` | 8081 | Automatique |
| **Production** | `https://ldc.lhspla-ci.org` | `main` (tags) | `vX.Y.Z` | 443 | Manuelle (GitHub Environments) |

**Isolation des conteneurs sur le VPS :**
- Staging : préfixe `staging_` sur tous les `container_name`
- Production : pas de préfixe, port 443 via `docker-compose.prod.yml`

---

## 3. Pipeline CI — Intégration continue

**Fichier :** `.github/workflows/ci.yml`
**Déclencheur :** tout push sur n'importe quelle branche

### Étapes

| Job | Commande | Ce qui est vérifié |
|---|---|---|
| `lint-frontend` | `ng lint` + `stylelint` | TypeScript, SCSS |
| `build-frontend` | `ng build --configuration=production` | Compilation Angular AOT |
| `build-backend` | `mvn package -DskipTests` | Compilation Java |
| `lint-python` | `flake8 support_ldc/ pilot_bot/` | Style Python |

> Si le CI échoue, le CD ne se déclenche pas.

---

## 4. Pipeline CD — Staging (automatique)

**Fichier :** `.github/workflows/cd-staging.yml`
**Déclencheur :** push sur `develop`

### Étapes

1. **Build & Push** des 4 images vers `ghcr.io` avec les tags :
   - `staging`
   - `staging-XXXXXXX` (7 premiers caractères du SHA du commit)

2. **Deploy SSH** sur le VPS :
   ```bash
   cd /opt/ldc_staging
   git fetch origin develop
   git checkout origin/develop
   docker login ghcr.io
   docker compose -f docker-compose.yml -f docker-compose.staging.yml -f docker-compose.ci.yml pull
   docker compose -f docker-compose.yml -f docker-compose.staging.yml -f docker-compose.ci.yml up -d --no-build --remove-orphans
   docker image prune -f
   ```

> `down` n'est **jamais** exécuté pour préserver les données de la DB staging.

---

## 5. Pipeline CD — Production (manuel)

**Fichier :** `.github/workflows/cd-production.yml`
**Déclencheur :** création d'un tag `vX.Y.Z` sur `main`

### Étapes

1. **Build & Push** des 4 images vers `ghcr.io` avec les tags :
   - `latest`
   - `vX.Y.Z`

2. **Attente d'approbation** — GitHub Environment `production`
   - Aller sur : **GitHub → Actions → run en cours → `Review deployments` → Approve**

3. **Deploy SSH** sur le VPS :
   ```bash
   cd /opt/ldc_prod
   git fetch --tags origin
   git checkout vX.Y.Z
   docker login ghcr.io
   docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.ci.yml pull
   docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.ci.yml up -d --no-build
   docker image prune -f
   ```

---

## 6. Procédure de basculement develop ↔ main

### Mode développement (quotidien)

Tu travailles toujours sur `develop`. Pour déployer vers staging :

```bash
bash scripts/push-develop.sh "description de la modification"
```

Staging se déploie **automatiquement**.

---

### Mise en production (release)

#### Étape 1 — S'assurer que develop est propre

```bash
git checkout develop
git pull origin develop
```

#### Étape 2 — Passer sur main et merger develop

```bash
git checkout main
git pull origin main
git merge develop --no-edit
```

#### Étape 3 — Releaser la version

```bash
git stash          # si des fichiers locaux non commités
bash scripts/bump-version.sh X.Y.Z
git stash pop
```

Le script fait automatiquement :
- Met à jour `package.json`, `package-lock.json` et `pom.xml`
- Crée un commit `chore: release vX.Y.Z` + tag `vX.Y.Z`
- Pousse vers `origin/main` avec le tag

GitHub Actions déclenche le build → attendre → approuver en prod.

#### Étape 4 — Approuver le déploiement

**GitHub → dépôt → Actions → run CD Production → `Review deployments` → Approve**

#### Étape 5 — Revenir en mode développement

```bash
git checkout develop
git merge main --no-edit      # ramener le bump de version dans develop
git push origin develop
```

---

### Schéma du cycle complet

```
develop  ──●──●──●──────────────────────────●──►
                    ↘ merge                  ↑
main     ────────────●── tag vX.Y.Z ─────────●──►
                                         merge back
```

---

### Règles importantes

| Règle | Raison |
|---|---|
| Ne jamais commiter directement sur `main` | Seul `bump-version.sh` touche main |
| Toujours merger `main` → `develop` après une release | Évite les conflits de version au prochain merge |
| Tester en staging avant de releaser | La prod ne doit recevoir que du code validé |
| `git stash` avant `bump-version.sh` si working tree sale | Le script vérifie que le tree est propre |

---

### Numérotation des versions (SemVer)

| Type de changement | Exemple |
|---|---|
| Correctif (bug fix) | `1.0.3` → `1.0.4` |
| Nouvelle fonctionnalité | `1.0.4` → `1.1.0` |
| Changement majeur | `1.1.0` → `2.0.0` |

---

## 7. Scripts utilitaires

### `scripts/push-develop.sh`

Commit et push vers develop en une commande :

```bash
bash scripts/push-develop.sh "mon message de commit"
```

### `scripts/bump-version.sh`

Release une nouvelle version depuis main :

```bash
bash scripts/bump-version.sh X.Y.Z
# Exemple : bash scripts/bump-version.sh 1.1.0
```

**Prérequis :**
- Être sur la branche `main`
- Working tree propre (ou avoir fait `git stash` avant)

---

## 8. Commandes VPS utiles

### Vérifier les conteneurs en cours

```bash
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
```

### Voir les logs d'un service

```bash
docker logs ldc_backend --tail=100 -f
docker logs staging_ldc_backend --tail=100 -f
```

### Redémarrer un service

```bash
docker restart ldc_frontend
docker restart staging_ldc_backend
```

### Vérifier les variables d'environnement d'un conteneur

```bash
docker exec ldc_backend env | grep CORS
docker exec staging_ldc_backend env | grep CORS
```
