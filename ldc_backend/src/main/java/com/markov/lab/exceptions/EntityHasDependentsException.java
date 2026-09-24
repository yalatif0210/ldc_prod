package com.markov.lab.exceptions;

/**
 * Levée quand une suppression est refusée parce que d'autres enregistrements dépendent encore de
 * l'entité ciblée (ex. suppression d'une {@code Region} qui a des {@code District} rattachés, ou
 * d'une {@code InformationUnit} référencée par des {@code InformationSubUnit}).
 * Jamais de suppression forcée : l'appelant doit d'abord traiter les dépendants. Le message doit
 * toujours préciser le nombre et le type des dépendants trouvés. Mappée en HTTP 409 (Conflict)
 * par {@link com.markov.lab.controller.RestExceptionHandler}.
 */
public class EntityHasDependentsException extends RuntimeException {

    public EntityHasDependentsException(String message) {
        super(message);
    }
}
