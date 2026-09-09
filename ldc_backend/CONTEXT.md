# ldc_backend

Backend Java/Spring Boot du Système de Collecte des Données de Laboratoire — expose l'API GraphQL et les endpoints REST consommés par `ldc_frontend`, et porte le modèle de données métier (comptes, structures, rapports, stocks).

## Language

**Structure**:
Un site physique (hôpital, laboratoire) rattaché à un District et une Région, auquel sont associés des Équipements et des Comptes.
_Avoid_: Platform — libellé d'affichage utilisé côté `ldc_frontend` pour la même entité, pas un concept distinct.

**Compte (Account)**:
Porte le Rôle d'un utilisateur et l'ensemble des Structures auxquelles il a accès. Distinct de l'Utilisateur.
_Avoid_: Utilisateur, User — voir Utilisateur.

**Utilisateur (User)**:
Les informations d'identité et de connexion d'une personne (nom, identifiant, téléphone, mot de passe). Lié 1:1 à un Compte, qui porte le Rôle et les Structures.
_Avoid_: Compte — voir Compte.

**Région**:
Un regroupement de Districts. N'est jamais persistée comme relation directe sur un Compte — sert uniquement de filtre d'affichage côté client pour réduire la liste de Structures proposées. La portée territoriale réelle d'un Compte est entièrement dérivée de ses Structures.
