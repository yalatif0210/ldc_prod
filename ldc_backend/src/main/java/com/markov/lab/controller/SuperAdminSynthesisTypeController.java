package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.SynthesisType;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.SynthesisTypeInput;
import com.markov.lab.repository.SynthesisRepository;
import com.markov.lab.repository.SynthesisTypeRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin du référentiel {@link SynthesisType} (Ticket #12). Le nommage
 * {@code SuperAdminSynthesisTypeController} active automatiquement le journal d'audit générique
 * ({@link com.markov.lab.audit.SuperAdminAuditAspect}) — voir sa javadoc.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/synthesis-types", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminSynthesisTypeController {

    private final SynthesisTypeRepository synthesisTypeRepository;
    private final SynthesisRepository synthesisRepository;

    @GetMapping
    public ResponseEntity<List<SynthesisType>> getSynthesisTypes() {
        return ResponseEntity.ok(synthesisTypeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createSynthesisType(@RequestBody SynthesisTypeInput input) {
        SynthesisType synthesisType = new SynthesisType();
        synthesisType.setType(input.getType());
        synthesisTypeRepository.save(synthesisType);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "SynthesisType created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateSynthesisType(
            @PathVariable Long id, @RequestBody SynthesisTypeInput input) {
        SynthesisType synthesisType = synthesisTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SynthesisType not found: " + id));
        synthesisType.setType(input.getType());
        synthesisTypeRepository.save(synthesisType);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "SynthesisType updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteSynthesisType(@PathVariable Long id) {
        if (!synthesisTypeRepository.existsById(id)) {
            throw new NotFoundException("SynthesisType not found: " + id);
        }
        long dependentSyntheses = synthesisRepository.countBySynthesisType_Id(id);
        if (dependentSyntheses > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer ce type de synthèse : %d synthèse(s) y font encore référence."
                            .formatted(dependentSyntheses));
        }
        synthesisTypeRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "SynthesisType deleted"));
    }
}
