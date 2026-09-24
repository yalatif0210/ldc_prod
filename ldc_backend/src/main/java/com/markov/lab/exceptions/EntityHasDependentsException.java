package com.markov.lab.exceptions;

/**
 * Levée quand une entité référentielle/structurelle ne peut pas être supprimée car des
 * enregistrements en dépendent encore (voir Ticket #12 - CRUD référentiels de types).
 * Le message doit indiquer le nombre et le type de dépendants bloquants.
 */
public class EntityHasDependentsException extends RuntimeException {

    public EntityHasDependentsException(String message) {
        super(message);
    }
}
