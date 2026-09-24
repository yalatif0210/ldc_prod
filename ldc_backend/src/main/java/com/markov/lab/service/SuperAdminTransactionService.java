package com.markov.lab.service;

import com.markov.lab.controller.dto.SuperAdminTransactionDTO;
import com.markov.lab.controller.dto.SuperAdminTransactionLineDTO;
import com.markov.lab.entity.MedicinesTransaction;
import com.markov.lab.entity.SanguineProductTransaction;
import com.markov.lab.entity.Transaction;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.MedicinesTransactionInput;
import com.markov.lab.input.SanguineProductTransactionInput;
import com.markov.lab.input.SuperAdminTransactionInput;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.MedicinesTransactionRepository;
import com.markov.lab.repository.SanguineProductRepository;
import com.markov.lab.repository.SanguineProductTransactionRepository;
import com.markov.lab.repository.StructureRepository;
import com.markov.lab.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Logique métier du CRUD Super Admin sur les Transferts (entité {@code Transaction}) et leurs
 * lignes filles ({@code SanguineProductTransaction}/{@code MedicinesTransaction}) — Ticket #8.
 *
 * <p>La suppression d'un Transfert supprime explicitement ses lignes filles AVANT de supprimer
 * le Transfert lui-même (voir {@link #delete(Long)}) : aucune cascade JPA n'est déclarée sur
 * {@link Transaction#getSanguineProductTransactions()}/{@link Transaction#getMedicinesTransaction()},
 * une suppression directe du Transfert échouerait donc sur la contrainte de clé étrangère
 * {@code transaction_id} s'il reste des lignes.</p>
 */
@Service
@RequiredArgsConstructor
public class SuperAdminTransactionService {

    private final TransactionRepository transactionRepository;
    private final StructureRepository structureRepository;
    private final EquipmentRepository equipmentRepository;
    private final SanguineProductRepository sanguineProductRepository;
    private final SanguineProductTransactionRepository sanguineProductTransactionRepository;
    private final IntrantRepository intrantRepository;
    private final MedicinesTransactionRepository medicinesTransactionRepository;

    @Transactional(readOnly = true)
    public List<SuperAdminTransactionDTO> findAll() {
        return transactionRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public SuperAdminTransactionDTO findById(Long id) {
        return toDTO(getOrThrow(id));
    }

    @Transactional
    public SuperAdminTransactionDTO create(SuperAdminTransactionInput input) {
        Transaction transaction = new Transaction();
        applyScalarFields(transaction, input);
        Transaction saved = transactionRepository.save(transaction);
        replaceLines(saved, input);
        return toDTO(getOrThrow(saved.getId()));
    }

    @Transactional
    public SuperAdminTransactionDTO update(Long id, SuperAdminTransactionInput input) {
        Transaction transaction = getOrThrow(id);
        applyScalarFields(transaction, input);
        transactionRepository.save(transaction);
        replaceLines(transaction, input);
        return toDTO(getOrThrow(id));
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = getOrThrow(id);
        // Cascade explicite (voir javadoc de classe) : les lignes filles sont supprimées avant
        // le Transfert lui-même, pour éviter une violation de contrainte de clé étrangère.
        sanguineProductTransactionRepository.deleteAll(transaction.getSanguineProductTransactions());
        medicinesTransactionRepository.deleteAll(transaction.getMedicinesTransaction());
        transactionRepository.delete(transaction);
    }

    private Transaction getOrThrow(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction introuvable : " + id));
    }

    private void applyScalarFields(Transaction transaction, SuperAdminTransactionInput input) {
        transaction.setOrigin(input.originId() == null ? null
                : structureRepository.findById(input.originId()).orElse(null));
        transaction.setDestination(input.destinationId() == null ? null
                : structureRepository.findById(input.destinationId()).orElse(null));
        transaction.setEquipment(input.equipmentId() == null ? null
                : equipmentRepository.findById(input.equipmentId()).orElse(null));
        transaction.setEquipment_destinataire(input.equipmentDestinataireId() == null ? null
                : equipmentRepository.findById(input.equipmentDestinataireId()).orElse(null));
        transaction.setApproved(input.approved() != null ? input.approved() : Boolean.FALSE);
        transaction.setIsRejected(input.isRejected() != null ? input.isRejected() : Boolean.FALSE);
    }

    /**
     * Remplace intégralement les lignes filles existantes du Transfert par celles fournies en
     * entrée. Utilisé à la fois pour la création (aucune ligne existante) et la modification
     * (permet d'ajouter/retirer/modifier des lignes depuis l'écran Super Admin).
     */
    private void replaceLines(Transaction transaction, SuperAdminTransactionInput input) {
        List<SanguineProductTransaction> existingSanguine = transaction.getSanguineProductTransactions();
        if (existingSanguine != null && !existingSanguine.isEmpty()) {
            sanguineProductTransactionRepository.deleteAll(existingSanguine);
        }
        List<MedicinesTransaction> existingMedicines = transaction.getMedicinesTransaction();
        if (existingMedicines != null && !existingMedicines.isEmpty()) {
            medicinesTransactionRepository.deleteAll(existingMedicines);
        }

        List<SanguineProductTransactionInput> sanguineInputs = input.sanguineProductTransactions();
        if (sanguineInputs != null && !sanguineInputs.isEmpty()) {
            List<SanguineProductTransaction> lines = new ArrayList<>();
            for (SanguineProductTransactionInput lineInput : sanguineInputs) {
                SanguineProductTransaction line = new SanguineProductTransaction();
                line.setTransaction(transaction);
                sanguineProductRepository.findById(lineInput.sanguine_product_id()).ifPresent(line::setSanguineProduct);
                line.setQuantity(lineInput.quantity());
                lines.add(line);
            }
            sanguineProductTransactionRepository.saveAll(lines);
        }

        List<MedicinesTransactionInput> medicineInputs = input.medicinesTransactions();
        if (medicineInputs != null && !medicineInputs.isEmpty()) {
            List<MedicinesTransaction> lines = new ArrayList<>();
            for (MedicinesTransactionInput lineInput : medicineInputs) {
                MedicinesTransaction line = new MedicinesTransaction();
                line.setTransaction(transaction);
                intrantRepository.findById(lineInput.intrant_id()).ifPresent(line::setIntrant);
                line.setQuantity(lineInput.quantity());
                lines.add(line);
            }
            medicinesTransactionRepository.saveAll(lines);
        }
    }

    private SuperAdminTransactionDTO toDTO(Transaction transaction) {
        List<SuperAdminTransactionLineDTO> sanguineLines = transaction.getSanguineProductTransactions() == null
                ? List.of()
                : transaction.getSanguineProductTransactions().stream()
                        .map(line -> SuperAdminTransactionLineDTO.ofSanguineProduct(
                                line.getId(), line.getSanguineProduct(), line.getQuantity()))
                        .toList();
        List<SuperAdminTransactionLineDTO> medicineLines = transaction.getMedicinesTransaction() == null
                ? List.of()
                : transaction.getMedicinesTransaction().stream()
                        .map(line -> SuperAdminTransactionLineDTO.ofIntrant(
                                line.getId(), line.getIntrant(), line.getQuantity()))
                        .toList();

        return new SuperAdminTransactionDTO(
                transaction.getId(),
                transaction.getCreatedAt(),
                transaction.getFeedbackAt(),
                transaction.getOrigin(),
                transaction.getDestination(),
                transaction.getEquipment(),
                transaction.getEquipment_destinataire(),
                transaction.getApproved(),
                transaction.getIsRejected(),
                sanguineLines,
                medicineLines
        );
    }
}
