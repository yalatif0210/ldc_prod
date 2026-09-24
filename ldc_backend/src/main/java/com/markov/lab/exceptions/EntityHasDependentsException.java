package com.markov.lab.exceptions;

/**
 * Levée quand une suppression est refusée parce que d'autres enregistrements dépendent encore de
 * l'entité ciblée (ex. suppression d'une {@code Region} qui a des {@code District} rattachés).
 * Jamais de suppression forcée : l'appelant doit d'abord traiter les dépendants (Ticket #10).
 */
public class EntityHasDependentsException extends RuntimeException {

    public EntityHasDependentsException(String message) {
        super(message);
    }
}
