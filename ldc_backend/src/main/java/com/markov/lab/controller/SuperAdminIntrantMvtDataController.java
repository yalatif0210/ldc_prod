package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.controller.dto.IntrantMvtDataRequest;
import com.markov.lab.entity.IntrantMvtData;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.repository.IntrantMvtDataRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.ReportRepository;
import com.markov.lab.service.SuperAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin pour {@link IntrantMvtData} (Ticket #14). Supprimer un {@link IntrantMvtData}
 * supprime explicitement ses {@code Adjustment} enfants en premier (voir
 * {@link SuperAdminService#deleteIntrantMvtData(Long)}, sur le meme motif que
 * {@link SuperAdminService#deleteReport(Long)}), pour eviter une erreur de contrainte de cle
 * etrangere. Audite automatiquement par {@code SuperAdminAuditAspect} grace au nommage
 * {@code SuperAdmin<Entite>Controller}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "CRUD des mouvements d'intrants")
@RequestMapping(path = "/api/super-admin/intrant-mvt-data", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminIntrantMvtDataController {

    private final IntrantMvtDataRepository intrantMvtDataRepository;
    private final ReportRepository reportRepository;
    private final IntrantRepository intrantRepository;
    private final SuperAdminService superAdminService;

    @GetMapping
    public ResponseEntity<List<IntrantMvtData>> getAll() {
        return ResponseEntity.ok(intrantMvtDataRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<IntrantMvtData> create(@RequestBody IntrantMvtDataRequest request) {
        IntrantMvtData intrantMvtData = new IntrantMvtData();
        applyRequest(intrantMvtData, request);
        IntrantMvtData saved = intrantMvtDataRepository.save(intrantMvtData);
        log.info("IntrantMvtData {} created by super admin", saved.getId());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntrantMvtData> update(@PathVariable Long id, @RequestBody IntrantMvtDataRequest request) {
        IntrantMvtData intrantMvtData = intrantMvtDataRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("IntrantMvtData not found: " + id));
        applyRequest(intrantMvtData, request);
        IntrantMvtData saved = intrantMvtDataRepository.save(intrantMvtData);
        log.info("IntrantMvtData {} updated by super admin", id);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> delete(@PathVariable Long id) {
        superAdminService.deleteIntrantMvtData(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantMvtData and its adjustments deleted"));
    }

    private void applyRequest(IntrantMvtData intrantMvtData, IntrantMvtDataRequest request) {
        if (request.reportId() != null) {
            reportRepository.findById(request.reportId()).ifPresent(intrantMvtData::setReport);
        }
        if (request.intrantId() != null) {
            intrantRepository.findById(request.intrantId()).ifPresent(intrantMvtData::setIntrant);
        }
        intrantMvtData.setEntryStock(request.entryStock());
        intrantMvtData.setDistributionStock(request.distributionStock());
        intrantMvtData.setAvailableStock(request.availableStock());
    }
}
