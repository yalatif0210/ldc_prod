package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Month;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.MonthInput;
import com.markov.lab.repository.MonthRepository;
import com.markov.lab.repository.PeriodRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin du référentiel {@link Month} (Ticket #12). Le nommage
 * {@code SuperAdminMonthController} active automatiquement le journal d'audit générique
 * ({@link com.markov.lab.audit.SuperAdminAuditAspect}) — voir sa javadoc.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/months", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminMonthController {

    private final MonthRepository monthRepository;
    private final PeriodRepository periodRepository;

    @GetMapping
    public ResponseEntity<List<Month>> getMonths() {
        return ResponseEntity.ok(monthRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createMonth(@RequestBody MonthInput input) {
        Month month = new Month();
        month.setMonth(input.getMonth());
        monthRepository.save(month);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Month created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateMonth(@PathVariable Long id, @RequestBody MonthInput input) {
        Month month = monthRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Month not found: " + id));
        month.setMonth(input.getMonth());
        monthRepository.save(month);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Month updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteMonth(@PathVariable Long id) {
        if (!monthRepository.existsById(id)) {
            throw new NotFoundException("Month not found: " + id);
        }
        long dependentPeriods = periodRepository.countByMonth_Id(id);
        if (dependentPeriods > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer ce mois : %d période(s) y font encore référence."
                            .formatted(dependentPeriods));
        }
        monthRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Month deleted"));
    }
}
