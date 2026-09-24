package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.InformationUnit;
import com.markov.lab.entity.Synthesis;
import com.markov.lab.entity.SynthesisType;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.SuperAdminSynthesisInput;
import com.markov.lab.repository.InformationUnitRepository;
import com.markov.lab.repository.SynthesisRepository;
import com.markov.lab.repository.SynthesisTypeRepository;
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
 * CRUD Super Admin (Ticket #15) sur l'entité feuille {@code Synthesis} : suppression directe, pas
 * de vérification de dépendants. Le nommage {@code SuperAdmin<Entité>Controller} active
 * automatiquement le journal d'audit générique ({@code SuperAdminAuditAspect}, Ticket #7).
 *
 * <p><strong>Ne pas confondre</strong> avec l'écran frontend {@code synthesis-admin} déjà
 * existant (route {@code /super-admin/synthesis}) : celui-ci gère en réalité la suppression de
 * {@code Report} sous un nom trompeur, pas l'entité {@code Synthesis}. Le nouvel écran pour cette
 * entité vit sous une route distincte (voir {@code synthesis-item-admin} côté frontend) pour ne
 * pas entrer en collision.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints - Synthesis")
@RequestMapping(path = "/api/super-admin/syntheses", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminSynthesisController {

    private final SynthesisRepository synthesisRepository;
    private final SynthesisTypeRepository synthesisTypeRepository;
    private final InformationUnitRepository informationUnitRepository;

    @GetMapping
    public ResponseEntity<List<Synthesis>> getSyntheses() {
        return ResponseEntity.ok(synthesisRepository.findAll());
    }

    @GetMapping("/synthesis-types")
    public ResponseEntity<List<SynthesisType>> getSynthesisTypes() {
        return ResponseEntity.ok(synthesisTypeRepository.findAll());
    }

    @GetMapping("/information-units")
    public ResponseEntity<List<InformationUnit>> getInformationUnits() {
        return ResponseEntity.ok(informationUnitRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createSynthesis(@RequestBody SuperAdminSynthesisInput input) {
        Synthesis synthesis = new Synthesis();
        applyInput(synthesis, input);
        synthesisRepository.save(synthesis);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Synthesis created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateSynthesis(@PathVariable Long id,
            @RequestBody SuperAdminSynthesisInput input) {
        Synthesis synthesis = synthesisRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Synthesis not found: " + id));
        applyInput(synthesis, input);
        synthesisRepository.save(synthesis);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Synthesis updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteSynthesis(@PathVariable Long id) {
        if (!synthesisRepository.existsById(id)) {
            throw new NotFoundException("Synthesis not found: " + id);
        }
        synthesisRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Synthesis deleted"));
    }

    private void applyInput(Synthesis synthesis, SuperAdminSynthesisInput input) {
        synthesis.setItem(input.item());
        synthesis.setSynthesisType(synthesisTypeRepository.findById(input.synthesisTypeId())
                .orElseThrow(() -> new NotFoundException("SynthesisType not found: " + input.synthesisTypeId())));
        if (input.informationUnitId() != null) {
            synthesis.setInformationUnit(informationUnitRepository.findById(input.informationUnitId())
                    .orElseThrow(() -> new NotFoundException("InformationUnit not found: " + input.informationUnitId())));
        } else {
            synthesis.setInformationUnit(null);
        }
    }
}
