package com.markov.lab.controller.dto;

import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Structure;

import java.time.Instant;
import java.util.List;

/**
 * Forme de réponse d'un Transfert (entité {@code Transaction}) pour la console Super Admin
 * (Ticket #8). Expose les Structures origine/destination, l'Équipement, le statut d'approbation
 * ainsi que les lignes filles ({@code SanguineProductTransaction}/{@code MedicinesTransaction})
 * nécessaires à l'écran de création/modification — voir {@link SuperAdminTransactionLineDTO}
 * pour la raison de ne pas exposer ces lignes en tant qu'entités JPA brutes.
 */
public record SuperAdminTransactionDTO(
        Long id,
        Instant createdAt,
        Instant feedbackAt,
        Structure origin,
        Structure destination,
        Equipment equipment,
        Equipment equipmentDestinataire,
        Boolean approved,
        Boolean isRejected,
        List<SuperAdminTransactionLineDTO> sanguineProductTransactions,
        List<SuperAdminTransactionLineDTO> medicinesTransactions
) {
}
