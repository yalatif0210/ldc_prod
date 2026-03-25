# LDC — Logiciel de Données du Laboratoire

Plateforme web de gestion des données de laboratoire et de pharmacie pour les structures de santé en Côte d'Ivoire.
Elle couvre la saisie hebdomadaire des rapports d'activité, le suivi des stocks d'intrants, la gestion des transactions entre structures, la synthèse des données et le pilotage par un super-administrateur.

---

## Table des matières

1. [Architecture générale](#1-architecture-générale)
2. [Stack technique](#2-stack-technique)
3. [Modèle de données](#3-modèle-de-données)
4. [API Backend](#4-api-backend)
5. [Rôles et permissions](#5-rôles-et-permissions)
6. [Frontend — routing et menus](#6-frontend--routing-et-menus)
7. [Notifications temps réel](#7-notifications-temps-réel)
8. [Infrastructure Docker](#8-infrastructure-docker)
9. [Variables d'environnement](#9-variables-denvironnement)
10. [Déploiement local](#10-déploiement-local)
11. [Déploiement production (VPS)](#11-déploiement-production-vps)
12. [Prérequis VPS — premier démarrage](#12-prérequis-vps--premier-démarrage)

---

## 1. Architecture générale

```
Internet / HTTPS (443)
        │
   ┌────▼─────────────────────┐
   │  ldc_frontend            │  Nginx 1.27 (reverse proxy + SPA Angular)
   │  Angular 17 (standalone) │
   └──┬──────────┬────────────┘
      │ /api/*   │ /ws/*  /graphql  /grafana/  /support/
      │          │
   ┌──▼──────────▼────────────┐
   │  ldc_backend             │  Spring Boot 3.5 — Java 21
   │  REST + GraphQL + WS     │
   └──────────────┬───────────┘
                  │ JDBC
   ┌──────────────▼───────────┐
   │  lab_db                  │  PostgreSQL 15
   └──────────────────────────┘

   ┌──────────────────────────┐
   │  grafana                 │  Monitoring (accessible via /grafana/)
   └──────────────────────────┘
```

Tous les services communiquent sur le réseau Docker interne `default`.
Un réseau externe `shared_db_net` permet à des outils tiers (pgAdmin…) d'accéder à la base.

---

## 2. Stack technique

| Couche | Technologie | Version |
|--------|------------|---------|
| Frontend | Angular (standalone components) | 17 |
| UI | Angular Material | 17 |
| HTTP | Angular HttpClient (`withFetch`, intercepteurs JWT) | — |
| WebSocket | STOMP over SockJS | — |
| Backend | Spring Boot | 3.5.5 |
| Langage | Java | 21 |
| ORM | Spring Data JPA / Hibernate 6 | — |
| API | REST + GraphQL (`spring-graphql`) | — |
| Sécurité | Spring Security + JWT (jjwt 0.13) | — |
| Base de données | PostgreSQL | 15 |
| Reverse proxy | Nginx | 1.27 |
| Conteneurisation | Docker + Docker Compose | — |
| Monitoring | Grafana | latest |
| Doc API | SpringDoc OpenAPI (Swagger UI) | 2.8.0 |

---

## 3. Modèle de données

### Hiérarchie géographique

```
Region
  └── District (n)
        └── Structure (n)   ←─── ManyToMany ──→ Equipment
                                  ManyToMany ──→ Account
```

### Utilisateurs et comptes

```
User (identité, mot de passe, téléphone)
  └── Account (1:1)
        ├── Role (ManyToOne)
        ├── Structures (ManyToMany — structures affectées)
        └── Reports (OneToMany)
```

### Rapports et données d'activité

```
Period ──→ Report ←── Account
  Month      │ ├── Status  (Draft / Suggested / Submitted / Approved)
             │ ├── Equipment
             │ ├── LabActivityData (OneToMany)   → Information
             │ └── IntrantMvtData (OneToMany)    → Intrant
             │        └── Adjustment (OneToMany) → AdjustmentType
```

### Intrants et CMM

```
IntrantType
  └── Intrant (code, SKU, facteurs de conversion)
        └── IntrantCmmConfig (CMM par structure/équipement)
```

### Transactions entre structures

```
Transaction
  ├── Structure (origin)
  ├── Structure (destination)
  ├── Equipment + Equipment destinataire
  ├── SanguineProductTransaction (OneToMany) → SanguineProduct
  └── MedicinesTransaction (OneToMany)       → Intrant
```

### Synthèse

```
SynthesisType
  └── Synthesis → InformationUnit
```

---

## 4. API Backend

### Authentification — public (`/api/auth`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/auth/signup` | Création d'un compte utilisateur |
| POST | `/api/auth/login` | Connexion — retourne `accessToken` + `refreshToken` |
| POST | `/api/auth/refresh` | Rotation des tokens (blacklist ancien refresh) |
| POST | `/api/auth/logout` | Blacklist l'access token courant |

> Sécurité anti-bruteforce : 5 échecs en 15 min → compte verrouillé.

### Rapports — authentifié (`/api/report`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/report/create-report-details` | Création des données du rapport (lab + intrants + ajustements) |
| POST | `/api/report/update-report-details` | Mise à jour des données |

Requêtes GraphQL disponibles :
- `createReport`, `updateReport`, `deleteReport`
- `reportByAccountAndEquipmentAndPeriodAlso`
- `lastFinalizedReportByEquipmentAndAccount`
- `reportsByAccountAndEquipmentWithinDateRange`
- `reportsBySupervisedStructuresAndEquipmentWithinDateRange`

### Super Admin — authentifié (`/api/super-admin`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/stats` | Statistiques globales |
| GET | `/stats/filtered` | Statistiques filtrées (période / structure / équipement) |
| GET | `/accounts` | Liste des comptes |
| PATCH | `/accounts/{id}/activate` | Activation compte |
| PATCH | `/accounts/{id}/deactivate` | Désactivation compte |
| PATCH | `/accounts/role` | Changement de rôle |
| PATCH | `/users/reset-password` | Réinitialisation mot de passe |
| DELETE | `/users/{id}` | Suppression utilisateur |
| GET | `/reports` | Tous les rapports |
| PATCH | `/reports/status` | Changement statut rapport |
| PATCH | `/reports/reassign` | Réassignation rapport |
| DELETE | `/reports/{id}` | Suppression rapport (cascade ajustements + données) |
| GET/POST/PUT/DELETE | `/periods` | CRUD des périodes |
| GET/PUT | `/roles` | Lecture et mise à jour des rôles |
| GET | `/structures` | Structures avec équipements, district, région |
| PATCH | `/structures/{id}/toggle` | Activation / désactivation structure |
| DELETE | `/structures/{id}/equipments/{eqId}` | Retrait d'un équipement d'une structure |
| GET | `/system/login-attempts` | Historique des tentatives de connexion |
| GET | `/system/blacklist-stats` | Statistiques de la blacklist JWT |
| DELETE | `/system/blacklist` | Purge manuelle de la blacklist |

### Transactions — authentifié (`/api/transactions`)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/transactions/create` | Création transaction (produits sanguins / médicaments) |
| POST | `/api/transactions/update` | Approbation / rejet |

### Documentation API interactive

Disponible en local à : `http://localhost:8081/swagger-ui.html`
En production à : `https://<DOMAIN>/swagger-ui.html`

---

## 5. Rôles et permissions

| Rôle | Code | Accès |
|------|------|-------|
| Super Admin | `SUPER_ADMIN` | Tout — tableau de bord super admin, paramètres, rapports, structures, rôles, système |
| Administrateur | `ADMIN` | Paramètres (users, platforms, periods, factors), consultation rapports |
| Superviseur | `SUPERVISOR` | Consultation rapports et synthèse |
| Utilisateur Labo | `LABORATORY_USER` | Saisie et consultation des rapports de son équipement |
| Utilisateur Pharmacie | `PHARMACY_USER` | Saisie et consultation des rapports de son équipement |

**Affectation des structures à la création du compte :**
- Rôles 1–2 (SUPER_ADMIN, ADMIN) : toutes les structures
- Rôles 3+ : uniquement les structures (plateformes) spécifiées

---

## 6. Frontend — routing et menus

### Routes principales

| URL | Composant | Guard |
|-----|-----------|-------|
| `/auth/login` | Login | — |
| `/dashboard` | Dashboard | `authGuard` |
| `/zver/super-admin/**` | Super Admin | `superAdminGuard` |
| `/zver/admin/settings/**` | Paramètres Admin | `adminGuard` |
| `/zver/public/**` | Rapports / Synthèse | `publicUserGuard` / `synthesisGuard` |

### Routes Super Admin (`/zver/super-admin`)

| Chemin | Page |
|--------|------|
| `/` | Tableau de bord (statistiques) |
| `/periods` | Gestion des périodes |
| `/reports` | Gestion des rapports (statut, réassignation, suppression) |
| `/users` | Gestion des comptes (activation, rôle, mot de passe) |
| `/roles` | Gestion des rôles |
| `/structures` | Gestion des structures et équipements |
| `/synthesis` | Données de synthèse |
| `/intrants` | Facteurs intrants |
| `/system` | Logs de connexion, blacklist JWT |

### Routes Paramètres Admin (`/zver/admin/settings`)

| Chemin | Page |
|--------|------|
| `/users` | Création d'utilisateurs |
| `/users/manage-users` | Gestion des utilisateurs |
| `/platforms` | Création de plateformes |
| `/periods` | Création de périodes |
| `/factors` | Gestion des facteurs |

### Intercepteurs HTTP (pipeline dans l'ordre d'exécution)

1. `noopInterceptor` — placeholder
2. `settingsInterceptor` — injection des paramètres applicatifs
3. `tokenInterceptor` — ajout du header `Authorization: Bearer <token>`, gestion 401
4. `errorInterceptor` — redirections `/403`, `/404`, `/500`, toast d'erreur
5. `successInterceptor` — gestion des réponses succès
6. `loggingInterceptor` — logs debug

---

## 7. Notifications temps réel

WebSocket via SockJS + STOMP (`/ws`), proxié par Nginx.

| Topic | Destinataires |
|-------|--------------|
| `/topic/admin` | ADMIN et SUPERVISOR |
| `/topic/notifications/{structureId}` | Utilisateurs de la structure |
| `/topic/broadcast` | Tous les connectés |

Les notifications sont déclenchées lors de la création de transactions entre structures.

---

## 8. Infrastructure Docker

### Services

| Service | Image | Port exposé (local) | Port exposé (prod) |
|---------|-------|--------------------|--------------------|
| `lab_db` | postgres:15-alpine | — | — |
| `ldc_backend` | Maven → JRE 21 | 8081 | — |
| `ldc_frontend` | Node 20 → Nginx 1.27 | 80 | 443 |
| `grafana` | grafana/grafana | 3000 | — |

### Volumes

| Volume | Usage |
|--------|-------|
| `lab_db_data` | Données PostgreSQL (persistantes) |
| `grafana_data` | Dashboards Grafana (persistants) |
| `certbot_certs` | Certificats Let's Encrypt (externe, prod uniquement) |

### Build multi-étapes Backend

```
Stage 1 — maven:3.9.6-eclipse-temurin-21
  mvn dependency:go-offline   (couche cachée Docker)
  mvn package -DskipTests

Stage 2 — eclipse-temurin:21-jre-alpine
  user non-root "app"
  EXPOSE 8080
  ENTRYPOINT java -jar app.jar
```

### Build multi-étapes Frontend

```
Stage 1 — node:20-alpine
  npm ci --legacy-peer-deps
  npm run build --configuration production

Stage 2 — nginx:1.27-alpine
  nginx.conf.template (substitution ${DOMAIN} au démarrage via env.sh)
  assets/env.js généré au démarrage (window.__env)
  EXPOSE 443
```

### Injection des variables au runtime (env.sh)

```sh
# Génère /usr/share/nginx/html/assets/env.js
window.__env = {
  BASE_URL, API_BASE_URL, API_BASE_PORT,
  GRAPHQL_END_POINT, API_REST_END_POINT
}
```

L'Angular build de production lit ces valeurs via `window.__env` dans `environment.prod.ts`.

---

## 9. Variables d'environnement

Créer un fichier `.env` à la racine du projet (ne pas commiter) :

```dotenv
# Domaine & SSL
DOMAIN=ldc.example.org
LETSENCRYPT_EMAIL=admin@example.org

# Base de données
POSTGRES_DB=lab_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<mot_de_passe_fort>

SPRING_DATASOURCE_URL=jdbc:postgresql://lab_db:5432/lab_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<même_que_ci-dessus>

# JWT — générer avec : openssl rand -base64 32
JWT_SECRET=<clé_base64_256_bits>

# CORS
APP_CORS_ALLOWED_ORIGINS=https://ldc.example.org

# Frontend (runtime)
BASE_URL=
API_BASE_URL=https://ldc.example.org
API_BASE_PORT=443
GRAPHQL_END_POINT=/graphql
API_REST_END_POINT=/api

# Grafana
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=<mot_de_passe_fort>
```

---

## 10. Déploiement local

### Prérequis

- Docker Desktop (Windows/Mac) ou Docker Engine (Linux)
- Git Bash (Windows) ou bash standard
- Node 20+ et Angular CLI (pour `ng serve`)

### Lancement complet

```bash
./deploy-local.sh              # Build et démarre tous les services
./deploy-local.sh backend      # Rebuild backend uniquement (avec cache)
./deploy-local.sh backend-clean # Rebuild backend sans cache (après modif pom.xml)
./deploy-local.sh frontend     # Rebuild frontend uniquement
./deploy-local.sh logs         # Logs backend en direct
./deploy-local.sh watch        # Mode watch (rebuild auto sur changements)
```

Utilise `docker-compose.yml` + `docker-compose.local.yml` (override local : ports exposés, HTTP, CORS localhost).

### Accès local

| Service | URL |
|---------|-----|
| Application | `http://localhost` |
| API REST | `http://localhost:8081/api` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| GraphiQL | `http://localhost:8081/graphiql` |
| Grafana | `http://localhost:3000` |

### Développement Angular (`ng serve`)

```bash
cd ldc_frontend
npm install --legacy-peer-deps
ng serve
```

L'application tourne sur `http://localhost:4200` et pointe vers le backend sur `http://localhost:8081`.

---

## 11. Déploiement production (VPS)

```bash
# Sur le VPS, depuis le dossier du projet
git pull

# Premier déploiement (ou après modification de pom.xml)
./deploy-prod.sh backend-clean
./deploy-prod.sh frontend

# Déploiements suivants (avec cache Maven)
./deploy-prod.sh backend
./deploy-prod.sh frontend

# Tout rebuilder d'un coup
./deploy-prod.sh
```

Utilise uniquement `docker-compose.yml` (pas d'override local). Le frontend écoute sur le port 443 (HTTPS).

---

## 12. Prérequis VPS — premier démarrage

### 1. Réseau Docker externe

```bash
docker network create shared_db_net
```

### 2. Certificat SSL Let's Encrypt

Le volume `certbot_certs` doit exister avant le premier `docker compose up` :

```bash
# Avec Certbot installé sur la machine hôte
certbot certonly --standalone -d ldc.example.org --email admin@example.org --agree-tos
docker volume create certbot_certs
# Puis copier les certs dans le volume ou utiliser un conteneur certbot dédié
```

### 3. Fichier `.env`

Copier et remplir le fichier `.env` (voir section 9).

### 4. Rendre les scripts exécutables

```bash
chmod +x deploy-prod.sh deploy-local.sh
```

### 5. Lancer pour la première fois

```bash
./deploy-prod.sh backend-clean
./deploy-prod.sh frontend
docker compose ps          # Vérifier que tous les services sont "Up"
```

---

## Structure du projet

```
app_v3_claude_production/
├── ldc_backend/               # Spring Boot
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/markov/lab/
│       ├── configuration/     # Security, Jackson, WebSocket
│       ├── controller/        # REST + GraphQL controllers
│       ├── entity/            # Entités JPA
│       ├── repository/        # Spring Data repositories
│       └── service/           # Logique métier
├── ldc_frontend/              # Angular
│   ├── Dockerfile
│   ├── nginx.conf             # Config Nginx production (HTTPS)
│   ├── nginx.local.conf       # Config Nginx locale (HTTP)
│   ├── env.sh                 # Injection variables runtime
│   └── src/app/
│       ├── core/              # Auth, intercepteurs, bootstrap
│       ├── routes/            # Pages par rôle
│       │   ├── super-admin/
│       │   ├── admin/
│       │   └── public/
│       └── shared/            # Services, modèles, composants partagés
├── ldc_db/
│   └── init.sql               # Données initiales (régions, rôles…)
├── docker-compose.yml         # Configuration production
├── docker-compose.local.yml   # Overrides développement local
├── deploy-local.sh            # Script de déploiement local
├── deploy-prod.sh             # Script de déploiement VPS
└── .env                       # Variables d'environnement (non versionné)
```
