package com.markov.lab.controller;

import com.markov.lab.controller.dto.AdjustmentRequest;
import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Adjustment;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.repository.AdjustmentRepository;
import com.markov.lab.repository.AdjustmentTypeRepository;
import com.markov.lab.repository.IntrantMvtDataRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin pour {@link Adjustment} (Ticket #14). Entite feuille (enfant de
 * {@code IntrantMvtData}) : suppression directe, sans cascade propre. Audite automatiquement par
 * {@code SuperAdminAuditAspect} grace au nommage {@code SuperAdmin<Entite>Controller}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "CRUD des ajustements")
@RequestMapping(path = "/api/super-admin/adjustments", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminAdjustmentController {

    private final AdjustmentRepository adjustmentRepository;
    private final IntrantMvtDataRepository intrantMvtDataRepository;
    private final AdjustmentTypeRepository adjustmentTypeRepository;

    @GetMapping
    public ResponseEntity<List<Adjustment>> getAll() {
        return ResponseEntity.ok(adjustmentRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Adjustment> create(@RequestBody AdjustmentRequest request) {
        Adjustment adjustment = new Adjustment();
        applyRequest(adjustment, request);
        Adjustment saved = adjustmentRepository.save(adjustment);
        log.info("Adjustment {} created by super admin", saved.getId());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Adjustment> update(@PathVariable Long id, @RequestBody AdjustmentRequest request) {
        Adjustment adjustment = adjustmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Adjustment not found: " + id));
        applyRequest(adjustment, request);
        Adjustment saved = adjustmentRepository.save(adjustment);
        log.info("Adjustment {} updated by super admin", id);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> delete(@PathVariable Long id) {
        if (!adjustmentRepository.existsById(id)) {
            throw new NotFoundException("Adjustment not found: " + id);
        }
        adjustmentRepository.deleteById(id);
        log.info("Adjustment {} deleted by super admin", id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Adjustment deleted"));
    }

    private void applyRequest(Adjustment adjustment, AdjustmentRequest request) {
        if (request.intrantMvtDataId() != null) {
            intrantMvtDataRepository.findById(request.intrantMvtDataId()).ifPresent(adjustment::setIntrantMvtData);
        }
        if (request.adjustmentTypeId() != null) {
            adjustmentTypeRepository.findById(request.adjustmentTypeId()).ifPresent(adjustment::setAdjustmentType);
        }
        adjustment.setQuantity(request.quantity());
        adjustment.setComment(request.comment());
    }
}
