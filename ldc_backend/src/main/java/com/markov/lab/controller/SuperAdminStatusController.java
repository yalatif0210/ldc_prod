package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Status;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.StatusInput;
import com.markov.lab.repository.ReportRepository;
import com.markov.lab.repository.StatusRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin du référentiel {@link Status} (Ticket #12). Le nommage
 * {@code SuperAdminStatusController} active automatiquement le journal d'audit générique
 * ({@link com.markov.lab.audit.SuperAdminAuditAspect}) — voir sa javadoc.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/statuses", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminStatusController {

    private final StatusRepository statusRepository;
    private final ReportRepository reportRepository;

    @GetMapping
    public ResponseEntity<List<Status>> getStatuses() {
        return ResponseEntity.ok(statusRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createStatus(@RequestBody StatusInput input) {
        Status status = new Status();
        status.setStatus(input.getStatus());
        statusRepository.save(status);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Status created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateStatus(@PathVariable Long id, @RequestBody StatusInput input) {
        Status status = statusRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Status not found: " + id));
        status.setStatus(input.getStatus());
        statusRepository.save(status);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Status updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteStatus(@PathVariable Long id) {
        if (!statusRepository.existsById(id)) {
            throw new NotFoundException("Status not found: " + id);
        }
        long dependentReports = reportRepository.countByStatus_Id(id);
        if (dependentReports > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer ce statut : %d rapport(s) y font encore référence."
                            .formatted(dependentReports));
        }
        statusRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Status deleted"));
    }
}
