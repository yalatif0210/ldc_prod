# Claude Intervention Log

Ce fichier trace toutes les interventions de Claude sur ce projet.

---

## [2026-03-19] — Infrastructure & DevOps

### Exposition port PostgreSQL
- Ajout du mapping `127.0.0.1:5432:5432` sur le service `lab_db` dans `docker-compose.yml`
- Bind sur localhost uniquement (pas d'exposition publique) — accès via SSH tunnel depuis PyCharm/Grafana

### Ajout service Grafana
- Nouveau service `grafana` dans `docker-compose.yml` (image `grafana/grafana:latest`)
- Connexion à `lab_db` via réseau Docker interne (`lab_db:5432`) — aucun port DB exposé
- Reverse proxy `/grafana/` ajouté dans `ldc_frontend/nginx.conf`
- Variables `.env` requises : `GRAFANA_ADMIN_PASSWORD`
- Volume persistant `grafana_data` ajouté

### Backup base de données
- Création de `backup-db.sh` : backup via `pg_dump` + compression gzip
- Rotation automatique : garde les 10 derniers backups
- Argument `--install` : installe automatiquement le cron (backup quotidien à 2h00)
- Backups stockés dans `backups/`, logs dans `backups/cron.log`

---

## [2026-03-19] — Interface Super Admin (en cours)

### Objectif
Construire une interface d'administration réservée au rôle `SUPER_ADMIN`, accessible depuis un nouveau menu dédié.

### Périmètre fonctionnel
- Dashboard avec statistiques globales (users, reports, periods, comptes actifs)
- Gestion complète (CRUD) des **Périodes de reporting** et leurs mois
- Gestion complète des **Reports** (statut, réaffectation, suppression)
- Gestion complète des **Utilisateurs & Comptes** (activation, rôle, reset password, suppression)
- Gestion des **Rôles**
- Gestion des **Structures/Plateformes** + hiérarchie Région/District
- Gestion des **Synthèses & SynthesisType**
- Gestion des **Intrants & Équipements**
- **Logs système** : tentatives de connexion, statistiques blacklist tokens, purge manuelle

### Architecture technique

**Backend (Spring Boot)**
- `SuperAdminController.java` — nouveau contrôleur REST `/api/super-admin/**`
- Sécurisé via `@PreAuthorize("hasRole('SUPER_ADMIN')")` + règle URL dans `SecurityConfig`
- Correction de `UserDetailsServiceImpl` pour charger les `GrantedAuthority` depuis le rôle compte
- Nouveaux DTOs : `AppStatsDTO`, `ChangeRoleRequest`, `ResetPasswordRequest`, `ReportStatusRequest`

**Frontend (Angular 20)**
- Nouveau guard `superAdminGuard` dans `role-guard.ts`
- Nouvelles routes lazy-loaded : `/zver/super-admin/**` dans `super-admin.routes.ts`
- Nouveau menu dédié dans `startup.service.ts` (visible SUPER_ADMIN uniquement)
- Nouveau service `SuperAdminService` (HTTP direct pour GET/PATCH/DELETE)
- Composants ag-grid avec inline editing : PeriodAdmin, ReportAdmin, UserAdmin, RoleAdmin, StructureAdmin, SynthesisAdmin, IntrantAdmin, SystemLogs
- Dashboard avec stat cards Material

### Fichiers clés à modifier
- `ldc_backend/.../service/UserDetailsServiceImpl.java`
- `ldc_backend/.../configuration/SecurityConfig.java`
- `ldc_frontend/.../core/authentication/role-guard.ts`
- `ldc_frontend/.../core/bootstrap/startup.service.ts`
- `ldc_frontend/.../app.routes.ts`

### Fichiers clés à créer
- `ldc_backend/.../controller/SuperAdminController.java`
- `ldc_backend/.../service/SuperAdminService.java`
- `ldc_backend/.../controller/dto/AppStatsDTO.java` (+ autres DTOs)
- `ldc_frontend/.../routes/super-admin/super-admin.routes.ts`
- `ldc_frontend/.../routes/super-admin/dashboard/super-admin-dashboard.ts`
- `ldc_frontend/.../routes/super-admin/periods/period-admin.ts`
- `ldc_frontend/.../routes/super-admin/reports/report-admin.ts`
- `ldc_frontend/.../routes/super-admin/users/user-admin.ts`
- `ldc_frontend/.../routes/super-admin/roles/role-admin.ts`
- `ldc_frontend/.../routes/super-admin/structures/structure-admin.ts`
- `ldc_frontend/.../routes/super-admin/synthesis/synthesis-admin.ts`
- `ldc_frontend/.../routes/super-admin/intrants/intrant-admin.ts`
- `ldc_frontend/.../routes/super-admin/system/system-logs.ts`
- `ldc_frontend/.../shared/services/super-admin.service.ts`

### Statut : EN COURS
