package com.markov.lab.exceptions;

/**
 * Levée quand la suppression d'une entité référentielle (ex. {@code InformationUnit}) est refusée
 * car d'autres enregistrements en dépendent encore (contrainte métier "jamais de cascade forcée",
 * Ticket #11). Le message doit toujours préciser le nombre et le type des dépendants trouvés.
 */
public class EntityHasDependentsException extends RuntimeException {

    public EntityHasDependentsException(String message) {
        super(message);
    }
}
