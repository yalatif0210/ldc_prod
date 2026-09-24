package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Information;
import com.markov.lab.entity.InformationSubSubUnit;
import com.markov.lab.entity.InformationSubUnit;
import com.markov.lab.entity.InformationUnit;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.SuperAdminInformationInput;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.InformationSubSubUnitRepository;
import com.markov.lab.repository.InformationSubUnitRepository;
import com.markov.lab.repository.InformationUnitRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD Super Admin (Ticket #15) sur l'entité feuille {@code Information} : suppression directe,
 * pas de vérification de dépendants. Le nommage {@code SuperAdmin<Entité>Controller} active
 * automatiquement le journal d'audit générique ({@code SuperAdminAuditAspect}, Ticket #7).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints - Information")
@RequestMapping(path = "/api/super-admin/informations", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminInformationController {

    private final InformationRepository informationRepository;
    private final InformationUnitRepository informationUnitRepository;
    private final InformationSubUnitRepository informationSubUnitRepository;
    private final InformationSubSubUnitRepository informationSubSubUnitRepository;
    private final EquipmentRepository equipmentRepository;

    @GetMapping
    public ResponseEntity<List<Information>> getInformations() {
        return ResponseEntity.ok(informationRepository.findAll());
    }

    @GetMapping("/information-units")
    public ResponseEntity<List<InformationUnit>> getInformationUnits() {
        return ResponseEntity.ok(informationUnitRepository.findAll());
    }

    @GetMapping("/information-sub-units")
    public ResponseEntity<List<InformationSubUnit>> getInformationSubUnits() {
        return ResponseEntity.ok(informationSubUnitRepository.findAll());
    }

    @GetMapping("/information-sub-sub-units")
    public ResponseEntity<List<InformationSubSubUnit>> getInformationSubSubUnits() {
        return ResponseEntity.ok(informationSubSubUnitRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createInformation(@RequestBody SuperAdminInformationInput input) {
        Information information = new Information();
        information.setIsActive(true);
        applyInput(information, input);
        informationRepository.save(information);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Information created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateInformation(@PathVariable Long id,
            @RequestBody SuperAdminInformationInput input) {
        Information information = informationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Information not found: " + id));
        applyInput(information, input);
        informationRepository.save(information);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Information updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteInformation(@PathVariable Long id) {
        if (!informationRepository.existsById(id)) {
            throw new NotFoundException("Information not found: " + id);
        }
        informationRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Information deleted"));
    }

    private void applyInput(Information information, SuperAdminInformationInput input) {
        if (input.isActive() != null) {
            information.setIsActive(input.isActive());
        }
        if (input.informationUnitId() != null) {
            information.setInformationUnit(informationUnitRepository.findById(input.informationUnitId())
                    .orElseThrow(() -> new NotFoundException("InformationUnit not found: " + input.informationUnitId())));
        }
        if (input.informationSubUnitId() != null) {
            information.setInformationSubUnit(informationSubUnitRepository.findById(input.informationSubUnitId())
                    .orElseThrow(() -> new NotFoundException("InformationSubUnit not found: " + input.informationSubUnitId())));
        }
        if (input.informationSubSubUnitId() != null) {
            information.setInformationSubSubUnit(informationSubSubUnitRepository.findById(input.informationSubSubUnitId())
                    .orElseThrow(() -> new NotFoundException("InformationSubSubUnit not found: " + input.informationSubSubUnitId())));
        }
        if (input.equipmentId() != null) {
            information.setEquipment(equipmentRepository.findById(input.equipmentId())
                    .orElseThrow(() -> new NotFoundException("Equipment not found: " + input.equipmentId())));
        }
    }
}
