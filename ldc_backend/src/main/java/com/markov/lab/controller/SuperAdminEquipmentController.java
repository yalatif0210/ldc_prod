package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Equipment;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.EquipmentInput;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.IntrantCmmConfigRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.ReportRepository;
import com.markov.lab.repository.SapNotificationRepository;
import com.markov.lab.repository.StructureRepository;
import com.markov.lab.repository.TransactionRepository;
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
import java.util.stream.Collectors;

/**
 * CRUD Super Admin sur l'entité structurelle {@link Equipment} (Ticket #13). Suit le motif déjà
 * établi par {@link SuperAdminController} (auth, forme des réponses) mais dans un contrôleur
 * dédié nommé {@code SuperAdmin<Entité>Controller} : {@link com.markov.lab.audit.SuperAdminAuditAspect}
 * s'appuie sur cette convention de nommage pour tracer automatiquement chaque mutation dans le
 * journal d'audit, sans aucun appel explicite ici.
 *
 * <p>La suppression est bloquée (409) tant qu'un enregistrement dépend encore de l'Équipement —
 * jamais de suppression forcée/cascade ici, voir la spec (Ticket #5, Out of Scope).</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/equipments", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminEquipmentController {

    private final EquipmentRepository equipmentRepository;
    private final StructureRepository structureRepository;
    private final IntrantRepository intrantRepository;
    private final InformationRepository informationRepository;
    private final ReportRepository reportRepository;
    private final IntrantCmmConfigRepository intrantCmmConfigRepository;
    private final SapNotificationRepository sapNotificationRepository;
    private final TransactionRepository transactionRepository;

    @GetMapping
    public ResponseEntity<List<Equipment>> getEquipments() {
        return ResponseEntity.ok(equipmentRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createEquipment(@RequestBody EquipmentInput input) {
        Equipment equipment = new Equipment();
        equipment.setName(input.getName());
        equipmentRepository.save(equipment);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Equipment created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateEquipment(@PathVariable Long id, @RequestBody EquipmentInput input) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Equipment not found: " + id));
        equipment.setName(input.getName());
        equipmentRepository.save(equipment);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Equipment updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteEquipment(@PathVariable Long id) {
        if (!equipmentRepository.existsById(id)) {
            throw new NotFoundException("Equipment not found: " + id);
        }
        ensureDeletable(id);
        equipmentRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Equipment deleted"));
    }

    /**
     * Vérifie l'existence de dépendants sur chaque relation entrante connue de {@link Equipment}
     * (voir les champs de l'entité) avant suppression. Lève {@link EntityHasDependentsException}
     * listant le type et le nombre de dépendants bloquants si au moins un existe.
     */
    private void ensureDeletable(Long equipmentId) {
        Map<String, Long> dependents = new LinkedHashMap<>();
        putIfPositive(dependents, "Structure(s)", structureRepository.countByEquipments_Id(equipmentId));
        putIfPositive(dependents, "Intrant(s)", intrantRepository.countByEquipment_Id(equipmentId));
        putIfPositive(dependents, "Information(s)", informationRepository.countByEquipment_Id(equipmentId));
        putIfPositive(dependents, "Rapport(s)", reportRepository.countByEquipment_Id(equipmentId));
        putIfPositive(dependents, "Configuration(s) CMM", intrantCmmConfigRepository.countByEquipment_Id(equipmentId));
        putIfPositive(dependents, "Notification(s)", sapNotificationRepository.countByEquipment_Id(equipmentId));
        long transfersAsOrigin = transactionRepository.countByEquipment_Id(equipmentId);
        long transfersAsDestination = transactionRepository.countByEquipmentDestinataireId(equipmentId);
        putIfPositive(dependents, "Transfert(s)", transfersAsOrigin + transfersAsDestination);

        if (!dependents.isEmpty()) {
            throw new EntityHasDependentsException(buildMessage("cet équipement", dependents));
        }
    }

    static void putIfPositive(Map<String, Long> map, String label, long count) {
        if (count > 0) {
            map.put(label, count);
        }
    }

    static String buildMessage(String subject, Map<String, Long> dependents) {
        String detail = dependents.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", "));
        return "Suppression impossible : " + detail + " dépendent encore de " + subject + ".";
    }
}
