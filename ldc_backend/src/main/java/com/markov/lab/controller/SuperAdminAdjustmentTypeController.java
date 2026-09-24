package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.AdjustmentType;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.AdjustmentTypeInput;
import com.markov.lab.repository.AdjustmentRepository;
import com.markov.lab.repository.AdjustmentTypeRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin du référentiel {@link AdjustmentType} (Ticket #12). Le nommage
 * {@code SuperAdminAdjustmentTypeController} active automatiquement le journal d'audit générique
 * ({@link com.markov.lab.audit.SuperAdminAuditAspect}) — voir sa javadoc.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/adjustment-types", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminAdjustmentTypeController {

    private final AdjustmentTypeRepository adjustmentTypeRepository;
    private final AdjustmentRepository adjustmentRepository;

    @GetMapping
    public ResponseEntity<List<AdjustmentType>> getAdjustmentTypes() {
        return ResponseEntity.ok(adjustmentTypeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createAdjustmentType(@RequestBody AdjustmentTypeInput input) {
        AdjustmentType adjustmentType = new AdjustmentType();
        adjustmentType.setName(input.name());
        adjustmentType.setType(input.type());
        adjustmentTypeRepository.save(adjustmentType);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "AdjustmentType created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateAdjustmentType(
            @PathVariable Long id, @RequestBody AdjustmentTypeInput input) {
        AdjustmentType adjustmentType = adjustmentTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AdjustmentType not found: " + id));
        adjustmentType.setName(input.name());
        adjustmentType.setType(input.type());
        adjustmentTypeRepository.save(adjustmentType);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "AdjustmentType updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteAdjustmentType(@PathVariable Long id) {
        if (!adjustmentTypeRepository.existsById(id)) {
            throw new NotFoundException("AdjustmentType not found: " + id);
        }
        long dependentAdjustments = adjustmentRepository.countByAdjustmentType_Id(id);
        if (dependentAdjustments > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer ce type d'ajustement : %d ajustement(s) y font encore référence."
                            .formatted(dependentAdjustments));
        }
        adjustmentTypeRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "AdjustmentType deleted"));
    }
}
