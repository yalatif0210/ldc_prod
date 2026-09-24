package com.markov.lab.input;

/**
 * Entrée de création/modification d'une {@code SapNotification} depuis la console Super Admin
 * (Ticket #9). Distincte de {@code SapNotificationInput}/{@code SapNotificationUpdateInput} (API
 * publique {@code /api/notifications}) car la console doit pouvoir modifier l'ensemble des champs
 * métier (émetteur, équipement, intrant, quantité) en plus du statut résolu/rejeté.
 */
public record SapNotificationAdminInput(
        Long emitterId,
        Long equipmentId,
        Long intrantId,
        Integer quantity,
        boolean isResolved,
        boolean isRejected
) {
}
