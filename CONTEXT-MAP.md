# Context Map

## Contexts

- [ldc_backend](./ldc_backend/CONTEXT.md): API GraphQL/REST et modèle de données métier (comptes, structures, rapports, stocks)
- ldc_frontend (pas encore de CONTEXT.md) : client Angular consommant l'API `ldc_backend`
- pilot_bot (pas encore de CONTEXT.md) : bot Telegram de reporting lecture-seule pour chefs de projet, accès direct à la base de `ldc_backend`
- support_ldc (pas encore de CONTEXT.md) : bot Telegram + dashboard de ticketing support technique, isolé (base et code propres)

## Relationships

- **ldc_frontend → ldc_backend** : API GraphQL (majorité des opérations) + quelques endpoints REST (`/api/auth/**`, `/api/report/**`, `/api/super-admin/**`)
- **pilot_bot → ldc_backend** : accès SQL direct en lecture seule sur la même base Postgres (`lab_db`), en dehors de toute API — couplage de schéma non contractuel
- **support_ldc** : aucune relation applicative avec les autres contextes, uniquement un partage de reverse-proxy Nginx
