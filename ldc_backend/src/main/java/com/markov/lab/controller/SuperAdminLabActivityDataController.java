package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.controller.dto.LabActivityDataRequest;
import com.markov.lab.entity.LabActivityData;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.LabActivityDataRepository;
import com.markov.lab.repository.ReportRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin pour {@link LabActivityData} (Ticket #14). Entite feuille : suppression
 * directe, sans cascade. Audite automatiquement par {@code SuperAdminAuditAspect} grace au
 * nommage {@code SuperAdmin<Entite>Controller}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "CRUD des donnees d'activite labo")
@RequestMapping(path = "/api/super-admin/lab-activity-data", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminLabActivityDataController {

    private final LabActivityDataRepository labActivityDataRepository;
    private final ReportRepository reportRepository;
    private final InformationRepository informationRepository;

    @GetMapping
    public ResponseEntity<List<LabActivityData>> getAll() {
        return ResponseEntity.ok(labActivityDataRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<LabActivityData> create(@RequestBody LabActivityDataRequest request) {
        LabActivityData labActivityData = new LabActivityData();
        applyRequest(labActivityData, request);
        LabActivityData saved = labActivityDataRepository.save(labActivityData);
        log.info("LabActivityData {} created by super admin", saved.getId());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LabActivityData> update(@PathVariable Long id, @RequestBody LabActivityDataRequest request) {
        LabActivityData labActivityData = labActivityDataRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("LabActivityData not found: " + id));
        applyRequest(labActivityData, request);
        LabActivityData saved = labActivityDataRepository.save(labActivityData);
        log.info("LabActivityData {} updated by super admin", id);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> delete(@PathVariable Long id) {
        if (!labActivityDataRepository.existsById(id)) {
            throw new NotFoundException("LabActivityData not found: " + id);
        }
        labActivityDataRepository.deleteById(id);
        log.info("LabActivityData {} deleted by super admin", id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "LabActivityData deleted"));
    }

    private void applyRequest(LabActivityData labActivityData, LabActivityDataRequest request) {
        if (request.reportId() != null) {
            reportRepository.findById(request.reportId()).ifPresent(labActivityData::setReport);
        }
        if (request.informationId() != null) {
            informationRepository.findById(request.informationId()).ifPresent(labActivityData::setInformation);
        }
        labActivityData.setValue(request.value());
    }
}
