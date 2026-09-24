package com.markov.lab.input;

import java.util.List;

/**
 * Entrée de création/modification d'un Transfert (Ticket #8) depuis la console Super Admin.
 * Réutilise volontairement {@link SanguineProductTransactionInput} et
 * {@link MedicinesTransactionInput}, déjà utilisés par {@code TransactionService} pour
 * {@code /api/transactions}, plutôt que de dupliquer une forme équivalente.
 */
public record SuperAdminTransactionInput(
        Long originId,
        Long destinationId,
        Long equipmentId,
        Long equipmentDestinataireId,
        Boolean approved,
        Boolean isRejected,
        List<SanguineProductTransactionInput> sanguineProductTransactions,
        List<MedicinesTransactionInput> medicinesTransactions
) {
}
