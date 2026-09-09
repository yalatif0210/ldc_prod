# Comptes ADMIN scopables aux structures, SUPER_ADMIN toujours global

`UserService.createUser` traitait jusqu'ici `SUPER_ADMIN`(1) et `ADMIN`(2) de façon identique : les deux recevaient automatiquement *toutes* les structures (`structureRepository.findAll()`), sans tenir compte d'une éventuelle sélection. On sépare désormais les deux : `ADMIN` rejoint `SUPERVISOR` dans un régime explicite où le créateur du compte choisit les structures (avec un raccourci « Tout sélectionner » disponible), tandis que `SUPER_ADMIN` reste seul à être verrouillé sur l'ensemble des structures sans possibilité de restreindre.

Ce choix privilégie le moindre privilège pour `ADMIN` — un rôle qui n'a pas besoin d'un accès total par défaut — plutôt que la commodité d'une assignation automatique, dans un système où une faille d'autorisation touchant justement les comptes à privilèges élevés a déjà été identifiée.

**Conséquence** : le `switch` de `UserService.createUser` (actuellement `case 1: case 2: findAll()`) doit être scindé pour que seul `case 1` (`SUPER_ADMIN`) conserve `findAll()` ; `case 2` (`ADMIN`) rejoint la branche par défaut (`findByIdList(request.platforms())`).
