package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.IntrantCmmConfig;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.IntrantCmmConfigInput;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantCmmConfigRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.StructureRepository;
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
 * CRUD Super Admin (Ticket #15) sur l'entité feuille {@code IntrantCmmConfig} : suppression
 * directe, pas de vérification de dépendants. Le nommage {@code SuperAdmin<Entité>Controller}
 * active automatiquement le journal d'audit générique ({@code SuperAdminAuditAspect}, Ticket #7) —
 * aucun appel explicite au journal n'est nécessaire ici.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints - IntrantCmmConfig")
@RequestMapping(path = "/api/super-admin/intrant-cmm-configs", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminIntrantCmmConfigController {

    private final IntrantCmmConfigRepository intrantCmmConfigRepository;
    private final EquipmentRepository equipmentRepository;
    private final IntrantRepository intrantRepository;
    private final StructureRepository structureRepository;

    @GetMapping
    public ResponseEntity<List<IntrantCmmConfig>> getIntrantCmmConfigs() {
        return ResponseEntity.ok(intrantCmmConfigRepository.findAll());
    }

    // Repli pour peupler le formulaire (liste des Intrants) sans marcher sur "/api/super-admin/intrants",
    // réservé à un futur contrôleur dédié à l'entité Intrant elle-même.
    @GetMapping("/intrants")
    public ResponseEntity<List<Intrant>> getIntrantsForLookup() {
        return ResponseEntity.ok(intrantRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createIntrantCmmConfig(@RequestBody IntrantCmmConfigInput input) {
        IntrantCmmConfig config = new IntrantCmmConfig();
        applyInput(config, input);
        intrantCmmConfigRepository.save(config);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantCmmConfig created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateIntrantCmmConfig(@PathVariable Long id,
            @RequestBody IntrantCmmConfigInput input) {
        IntrantCmmConfig config = intrantCmmConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("IntrantCmmConfig not found: " + id));
        applyInput(config, input);
        intrantCmmConfigRepository.save(config);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantCmmConfig updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteIntrantCmmConfig(@PathVariable Long id) {
        if (!intrantCmmConfigRepository.existsById(id)) {
            throw new NotFoundException("IntrantCmmConfig not found: " + id);
        }
        intrantCmmConfigRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantCmmConfig deleted"));
    }

    private void applyInput(IntrantCmmConfig config, IntrantCmmConfigInput input) {
        config.setCmm(input.cmm());
        config.setEquipment(equipmentRepository.findById(input.equipmentId())
                .orElseThrow(() -> new NotFoundException("Equipment not found: " + input.equipmentId())));
        config.setIntrant(intrantRepository.findById(input.intrantId())
                .orElseThrow(() -> new NotFoundException("Intrant not found: " + input.intrantId())));
        config.setStructure(structureRepository.findById(input.structureId())
                .orElseThrow(() -> new NotFoundException("Structure not found: " + input.structureId())));
    }
}
