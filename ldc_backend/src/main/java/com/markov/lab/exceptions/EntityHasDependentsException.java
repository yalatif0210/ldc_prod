package com.markov.lab.exceptions;

/**
 * Levée quand une suppression d'entité référentielle/structurelle (Ticket #13, et futurs tickets
 * similaires) est refusée parce que d'autres enregistrements en dépendent encore. Le message
 * porte le détail (type et nombre) des dépendants bloquants, pour que le Super Admin sache quoi
 * traiter avant de réessayer. Mappée en HTTP 409 (Conflict) par {@link com.markov.lab.controller.RestExceptionHandler}.
 */
public class EntityHasDependentsException extends RuntimeException {

    public EntityHasDependentsException(String message) {
        super(message);
    }
}
