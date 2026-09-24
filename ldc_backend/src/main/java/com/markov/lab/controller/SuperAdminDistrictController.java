package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.District;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.DistrictInput;
import com.markov.lab.repository.DistrictRepository;
import com.markov.lab.repository.RegionRepository;
import com.markov.lab.repository.StructureRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin sur le référentiel géographique {@link District} (Ticket #10). Nommage
 * {@code SuperAdmin<Entité>Controller} : intercepté automatiquement par
 * {@link com.markov.lab.audit.SuperAdminAuditAspect} pour le journal d'audit (Ticket #7), sans
 * rien à câbler ici.
 *
 * <p>La suppression est refusée tant que des {@link com.markov.lab.entity.Structure} sont encore
 * rattachées au district (jamais de suppression forcée, voir Ticket #5 / Out of Scope).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/districts", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminDistrictController {

    private final DistrictRepository districtRepository;
    private final RegionRepository regionRepository;
    private final StructureRepository structureRepository;

    @GetMapping
    public ResponseEntity<List<District>> getDistricts() {
        return ResponseEntity.ok(districtRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createDistrict(@RequestBody DistrictInput districtInput) {
        District district = new District();
        district.setName(districtInput.getName());
        district.setRegion(regionRepository.findById(districtInput.getRegionId())
                .orElseThrow(() -> new NotFoundException("Region not found: " + districtInput.getRegionId())));
        districtRepository.save(district);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "District created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateDistrict(@PathVariable Long id, @RequestBody DistrictInput districtInput) {
        District district = districtRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("District not found: " + id));
        district.setName(districtInput.getName());
        district.setRegion(regionRepository.findById(districtInput.getRegionId())
                .orElseThrow(() -> new NotFoundException("Region not found: " + districtInput.getRegionId())));
        districtRepository.save(district);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "District updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteDistrict(@PathVariable Long id) {
        if (!districtRepository.existsById(id)) {
            throw new NotFoundException("District not found: " + id);
        }
        long dependentStructures = structureRepository.countByDistrict_Id(id);
        if (dependentStructures > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer ce district : %d Structure(s) en dépendent encore."
                            .formatted(dependentStructures));
        }
        districtRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "District deleted"));
    }
}
