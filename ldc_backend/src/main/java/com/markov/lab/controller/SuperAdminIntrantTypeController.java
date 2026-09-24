package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.IntrantType;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.IntrantTypeInput;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.IntrantTypeRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin du référentiel {@link IntrantType} (Ticket #12). Le nommage
 * {@code SuperAdminIntrantTypeController} active automatiquement le journal d'audit générique
 * ({@link com.markov.lab.audit.SuperAdminAuditAspect}) — voir sa javadoc.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/intrant-types", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminIntrantTypeController {

    private final IntrantTypeRepository intrantTypeRepository;
    private final IntrantRepository intrantRepository;

    @GetMapping
    public ResponseEntity<List<IntrantType>> getIntrantTypes() {
        return ResponseEntity.ok(intrantTypeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createIntrantType(@RequestBody IntrantTypeInput input) {
        IntrantType intrantType = new IntrantType();
        intrantType.setName(input.getName());
        intrantTypeRepository.save(intrantType);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantType created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateIntrantType(
            @PathVariable Long id, @RequestBody IntrantTypeInput input) {
        IntrantType intrantType = intrantTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("IntrantType not found: " + id));
        intrantType.setName(input.getName());
        intrantTypeRepository.save(intrantType);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantType updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteIntrantType(@PathVariable Long id) {
        if (!intrantTypeRepository.existsById(id)) {
            throw new NotFoundException("IntrantType not found: " + id);
        }
        long dependentIntrants = intrantRepository.countByIntrantType_Id(id);
        if (dependentIntrants > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer ce type d'intrant : %d intrant(s) y font encore référence."
                            .formatted(dependentIntrants));
        }
        intrantTypeRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "IntrantType deleted"));
    }
}
