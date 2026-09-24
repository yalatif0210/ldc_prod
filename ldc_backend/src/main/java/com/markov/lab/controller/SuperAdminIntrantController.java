package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.IntrantType;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.IntrantInput;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantCmmConfigRepository;
import com.markov.lab.repository.IntrantMvtDataRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.IntrantTypeRepository;
import com.markov.lab.repository.MedicinesTransactionRepository;
import com.markov.lab.repository.SapNotificationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.markov.lab.controller.SuperAdminEquipmentController.buildMessage;
import static com.markov.lab.controller.SuperAdminEquipmentController.putIfPositive;

/**
 * CRUD Super Admin sur l'entité structurelle {@link Intrant} (Ticket #13). Voir la javadoc de
 * {@link SuperAdminEquipmentController} pour le motif général (nommage du contrôleur pour
 * l'interception d'audit automatique, blocage de suppression sur dépendance).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/intrants", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminIntrantController {

    private final IntrantRepository intrantRepository;
    private final IntrantTypeRepository intrantTypeRepository;
    private final EquipmentRepository equipmentRepository;
    private final IntrantMvtDataRepository intrantMvtDataRepository;
    private final IntrantCmmConfigRepository intrantCmmConfigRepository;
    private final MedicinesTransactionRepository medicinesTransactionRepository;
    private final SapNotificationRepository sapNotificationRepository;

    @GetMapping
    public ResponseEntity<List<Intrant>> getIntrants() {
        return ResponseEntity.ok(intrantRepository.findAll());
    }

    /** Référentiels utilisés pour peupler les menus déroulants du formulaire Intrant côté console. */
    @GetMapping("/intrant-types")
    public ResponseEntity<List<IntrantType>> getIntrantTypes() {
        return ResponseEntity.ok(intrantTypeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createIntrant(@RequestBody IntrantInput input) {
        Intrant intrant = new Intrant();
        applyInput(intrant, input);
        intrantRepository.save(intrant);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Intrant created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateIntrant(@PathVariable Long id, @RequestBody IntrantInput input) {
        Intrant intrant = intrantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Intrant not found: " + id));
        applyInput(intrant, input);
        intrantRepository.save(intrant);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Intrant updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteIntrant(@PathVariable Long id) {
        if (!intrantRepository.existsById(id)) {
            throw new NotFoundException("Intrant not found: " + id);
        }
        ensureDeletable(id);
        intrantRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Intrant deleted"));
    }

    private void applyInput(Intrant intrant, IntrantInput input) {
        intrant.setName(input.getName());
        intrant.setCode(input.getCode());
        intrant.setSku(input.getSku());
        intrant.setConvertionFactor(input.getConversionFactor());
        intrant.setRoundFactor(input.getRoundFactor());
        intrant.setOtherFactor(input.getOtherFactor());
        IntrantType intrantType = intrantTypeRepository.findById(input.getIntrantTypeId())
                .orElseThrow(() -> new NotFoundException("IntrantType not found: " + input.getIntrantTypeId()));
        intrant.setIntrantType(intrantType);
        intrant.setEquipment(equipmentRepository.findById(input.getEquipmentId())
                .orElseThrow(() -> new NotFoundException("Equipment not found: " + input.getEquipmentId())));
    }

    /**
     * Voir la javadoc de {@link SuperAdminEquipmentController#ensureDeletable}. Dépendants
     * entrants connus de {@link Intrant} : mouvements, configurations CMM, transactions de
     * médicaments, notifications SAP.
     */
    private void ensureDeletable(Long intrantId) {
        Map<String, Long> dependents = new LinkedHashMap<>();
        putIfPositive(dependents, "Mouvement(s) d'intrant", intrantMvtDataRepository.countByIntrant_Id(intrantId));
        putIfPositive(dependents, "Configuration(s) CMM", intrantCmmConfigRepository.countByIntrant_Id(intrantId));
        putIfPositive(dependents, "Transaction(s) de médicament", medicinesTransactionRepository.countByIntrant_Id(intrantId));
        putIfPositive(dependents, "Notification(s)", sapNotificationRepository.countByIntrant_Id(intrantId));

        if (!dependents.isEmpty()) {
            throw new EntityHasDependentsException(buildMessage("cet intrant", dependents));
        }
    }
}
