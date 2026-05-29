# Audit Complet - app_v3_claude_production

**Date** : 2026-05-29 | **Version** : v1.0.8 | **Branch** : develop

---

## Résumé Exécutif

| Catégorie | CRITIQUE | HAUTE | MOYENNE | FAIBLE | Total |
|-----------|----------|-------|---------|--------|-------|
| Bugs / Logique | 2 | 3 | 4 | 2 | 11 |
| Sécurité | 2 | 7 | 2 | 0 | 11 |
| Performance | 3 | 8 | 2 | 0 | 13 |
| Frontend | 0 | 3 | 3 | 3 | 9 |
| **TOTAL** | **7** | **21** | **11** | **5** | **44** |

**Score global : 4.5 / 10** — projet fonctionnel mais avec des risques de production non négligeables.

> ✅ Bonne nouvelle : le fichier `.env` est bien dans `.gitignore` et n'est pas commité.

---

## 1. BUGS & LOGIQUE

| Fichier | Ligne | Gravité | Description |
|---------|-------|---------|-------------|
| `ldc_backend/.../InformationController.java` | 54 | **CRITIQUE** | Bug copier-coller : `getInformationSubUnitId()` utilisé deux fois — devrait être `getInformationSubSubUnitId()` pour setter le SubSubUnit |
| `ldc_backend/.../EquipmentController.java` | 34 | **CRITIQUE** | `Objects.requireNonNull(repo.findByName(name).orElse(null))` — logique inversée, lève NPE si non trouvé |
| `ldc_backend/.../JwtHelper.java` | 60 | **HAUTE** | `user.getUsername()` appelé sans null-check préalable sur `user` (résultat de `extractUser()`) |
| `ldc_backend/.../UserService.java` | 77 | **HAUTE** | `roleRepository.findById(...).orElse(null)` assigné directement sans vérification |
| `ldc_backend/.../ReportController.java` | 161 | **HAUTE** | `assert input.status() != null` — assertions désactivées en prod par la JVM, ne protège rien |
| `ldc_backend/.../SanguineProductController.java` | 20 | **MOYENNE** | Retour d'un `Optional<SanguineProduct>` depuis un `@QueryMapping` GraphQL — sérialisé en JSON incohérent |
| `ldc_backend/.../IntrantCmmConfigService.java` | 60-62 | **MOYENNE** | Comparaison `==` sur `Long` (wrapper) au lieu de `.equals()` — peut échouer selon auto-boxing |
| `ldc_backend/.../EquipmentProcessor.java` | 39, 67 | **MOYENNE** | `Math.toIntExact(unit.getId())` sans try-catch — `ArithmeticException` si id > Integer.MAX_VALUE |
| `ldc_backend/.../PeriodService.java` | 42 | **MOYENNE** | `System.out.println(period)` — debug oublié en production |
| `ldc_backend/.../ReportController.java` | 53 | **FAIBLE** | `System.out.println(request)` — debug oublié |
| `ldc_backend/.../StructureController.java` | 86 | **FAIBLE** | `System.out.println(platformInput)` — debug oublié |

### Pattern récurrent — Catch silencieux (16+ contrôleurs)

```java
catch (EmptyResultDataAccessException ex) {
    return false; // Aucun log — perte de traçabilité
}
```

Fichiers concernés : `AccountController`, `EquipmentController`, `IntrantTypeController`, `RoleController`, `RegionController`, `MonthController`, `StatusController`, `DistrictController`, `InformationUnitController`, `InformationSubUnitController`, `InformationSubSubUnitController`, `IntrantMvtDataController`, `LabActivityDataController`, `IntrantController`, `UserController`, `ReportController`.

---

## 2. SÉCURITÉ

| Fichier | Ligne | Gravité | Description |
|---------|-------|---------|-------------|
| `ldc_backend/.../WebSocketConfig.java` | 16 | **CRITIQUE** | `setAllowedOriginPatterns("*")` — accepte toutes origines WebSocket, vulnérable au CSRF/hijacking |
| `ldc_backend/.../SecurityConfig.java` | 53-54 | **CRITIQUE** | `setAllowedHeaders(List.of("*"))` + `setAllowCredentials(true)` combinés — incompatible et dangereux |
| `ldc_backend/.../UserController.java` | 45-54 | **HAUTE** | Mutation GraphQL `deleteUser` sans `@PreAuthorize` — tout utilisateur authentifié peut supprimer n'importe quel compte |
| `ldc_backend/.../UserInput.java` | 6-11 | **HAUTE** | Zéro validation (`@NotBlank`, `@NotNull`) sur `username`, `password`, `name`, `phone` |
| `ldc_backend/.../ReportInput.java` | 6-12 | **HAUTE** | Aucune validation sur `period_name`, `equipment_name`, `account_id` |
| `ldc_backend/.../ReportDetailInput.java` | 5-10 | **HAUTE** | Aucune validation sur `report_id`, `status_id`, listes |
| `ldc_backend/.../PeriodInput.java` | 6-12 | **HAUTE** | Aucune validation sur `monthName`, `periodName`, `startDate`, `endDate` |
| `ldc_backend/.../ReportController.java` | 53 | **HAUTE** | `System.out.println(request)` — données requête potentiellement sensibles exposées en stdout |
| `ldc_backend/.../StructureController.java` | 86 | **HAUTE** | `System.out.println(platformInput)` — idem |
| `ldc_backend/.../JwtHelper.java` | 71 | **MOYENNE** | `"Access denied: " + e.getMessage()` — détails d'erreur JWT exposés au client |
| `ldc_backend/src/main/resources/application.properties` | 61 | **MOYENNE** | `logging.level.com.markov.lab=DEBUG` — verbosité trop haute pour la production |

> ✅ `.env` correctement exclu du dépôt via `.gitignore` — secrets non commités.

---

## 3. PERFORMANCE

| Fichier | Ligne | Gravité | Description |
|---------|-------|---------|-------------|
| `ldc_backend/.../IntrantCmmConfigService.java` | 59-64 | **CRITIQUE** | `findAll().stream().filter(...)` — charge TOUTE la table en mémoire pour trouver 1 enregistrement |
| `ldc_backend/.../UserService.java` | 61 | **CRITIQUE** | `structureRepository.findAll()` dans une boucle de création utilisateur |
| `ldc_backend/.../SuperAdminService.java` | 44 | **CRITIQUE** | `accountRepository.findAll().stream().filter(...).count()` — devrait être un `COUNT(*)` SQL |
| `ldc_backend/.../ReportService.java` | 76-101 | **HAUTE** | Boucles `labInformation` + `intrantInformation` avec appels `findById` × N |
| `ldc_backend/.../TransactionService.java` | 53-66 | **HAUTE** | Appels repository individuels dans des boucles de transaction |
| `ldc_backend/.../PlatformService.java` | 30-32 | **HAUTE** | `findByIdList` suivi de boucle d'ajout — à vérifier en N+1 |
| `ldc_backend/.../Report.java` (entity) | 24-38 | **MOYENNE** | Relations `@ManyToOne` sans `FetchType` explicite → LAZY par défaut → N+1 en sérialisation de listes |

### Pattern récurrent — findAll() sans pagination (20+ endpoints)

Tous les `@QueryMapping` retournent `repository.findAll()` sans `Pageable`. Charge l'intégralité des tables à chaque requête GraphQL.

Endpoints concernés (non exhaustif) : `reports()`, `structures()`, `equipments()`, `periods()`, `accounts()`, `users()`, `intrants()`, `regions()`, `districts()`, `roles()`, `statuses()`, `months()`, `labActivityDatas()`, `intrantMvtDatas()`, `sapNotifications()`, `sanguineProducts()`, `informationUnits()`, `informationSubUnits()`, `informationSubSubUnits()`, `loginAttempts()`

---

## 4. FRONTEND ANGULAR

| Fichier | Ligne | Gravité | Description |
|---------|-------|---------|-------------|
| `ldc_frontend/src/environments/environment.ts` | 14 | **HAUTE** | Typo `API_BASE8URL` au lieu de `API_BASE_URL` — URL API potentiellement `undefined` en dev |
| `ldc_frontend/.../rest.service.ts` | 33 | **HAUTE** | `catchError(error => of(error))` — retourne l'erreur comme un succès, masque les erreurs HTTP |
| `ldc_frontend/.../cmm-settings.ts` | 80, 87, 94 | **HAUTE** | 3 subscriptions (`valueChanges`, `forkJoin`) sans `takeUntil(destroy$)` — fuite mémoire |
| `ldc_frontend/.../validation.service.ts` | multiple | **MOYENNE** | 27 usages de `: any` — perte de type-safety |
| `ldc_frontend/.../dashboard.service.ts` | multiple | **MOYENNE** | 9+ usages de `: any` |
| `ldc_frontend/.../report-history.service.ts` | 110 | **MOYENNE** | `.subscribe()` sans gestion d'erreur et sans `takeUntil` |
| `ldc_frontend/.../admin-layout.ts` | 117 | **FAIBLE** | `TODO: Trigger when transition end` + `setTimeout` hardcodé à 400ms |
| Multiples | — | **FAIBLE** | 9 `console.log()` actifs : `dashboard.ts:268`, `cmm-settings.ts:158/160`, `synthesis.service.ts:258`, `structure-admin.ts:57`, `synthesis.ts:197`, `report-history.ts:258/351` |
| Multiples | — | **FAIBLE** | ~10 `//console.log` commentés à nettoyer (`validation.service.ts`, `synthesis.service.ts`, `report.service.ts`, `report-history.service.ts`) |

---

## Plan d'Action Prioritisé

### Quick Wins < 1 jour

1. **Bug critique** `InformationController.java:54` — changer `getInformationSubUnitId()` en `getInformationSubSubUnitId()`
2. **Supprimer tous les `System.out.println`** — `PeriodService.java:42`, `ReportController.java:53`, `StructureController.java:86`
3. **Typo frontend** `environment.ts:14` — `API_BASE8URL` → `API_BASE_URL`
4. **Supprimer les 9 `console.log()`** frontend
5. **Logging production** `application.properties:61` — `DEBUG` → `INFO`
6. **Sécurité** `UserController.java:45` — ajouter `@PreAuthorize("hasRole('SUPER_ADMIN')")` sur `deleteUser`

### Moyen terme (1–5 jours)

7. **CORS** : restreindre `allowedHeaders` et origines WebSocket à la liste blanche
8. **Validation des inputs** : ajouter `@NotBlank`/`@NotNull` sur `UserInput`, `ReportInput`, `ReportDetailInput`, `PeriodInput` + activer `@Valid` dans les controllers
9. **Remplacer les 3 `findAll()` critiques** :
   - `IntrantCmmConfigService` → méthode `@Query` paramétrée
   - `SuperAdminService` → `countByIsActiveTrue()`
   - `UserService` → requête avec jointure au lieu de `findAll()` en boucle
10. **Frontend** `rest.service.ts:33` — `catchError(error => throwError(() => error))`
11. **Frontend** `cmm-settings.ts` + `report-history.service.ts` — ajouter `takeUntil(destroy$)`
12. **NPE** `EquipmentController.java:34` et `JwtHelper.java:60` — null-checks explicites

### Long terme (> 5 jours)

13. **Pagination** sur les 20+ endpoints `findAll()` via `Pageable` Spring Data + côté frontend
14. **Batch repository** dans les boucles de `ReportService`, `TransactionService`
15. **Typage TypeScript** — éliminer les `: any` dans `validation.service.ts` et `dashboard.service.ts`
16. **Centraliser `ObjectMapper`** en bean Spring singleton
17. **Modèle de permissions** GraphQL — `@PreAuthorize` systématique sur toutes les mutations sensibles

---

## Vérification

- Backend : `mvn test` + tester les mutations GraphQL sensibles (delete, create) avec différents rôles
- Frontend : `ng build --configuration production` doit passer sans erreur TypeScript
- Sécurité CORS : tester depuis une origine non autorisée
- Performance : `spring.jpa.show-sql=true` temporairement pour monitorer les requêtes SQL en développement
