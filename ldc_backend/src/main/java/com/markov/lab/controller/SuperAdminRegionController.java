package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.Region;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.RegionInput;
import com.markov.lab.repository.DistrictRepository;
import com.markov.lab.repository.RegionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin sur le référentiel géographique {@link Region} (Ticket #10). Nommage
 * {@code SuperAdmin<Entité>Controller} : intercepté automatiquement par
 * {@link com.markov.lab.audit.SuperAdminAuditAspect} pour le journal d'audit (Ticket #7), sans
 * rien à câbler ici.
 *
 * <p>La suppression est refusée tant que des {@link com.markov.lab.entity.District} sont encore
 * rattachés à la région (jamais de suppression forcée, voir Ticket #5 / Out of Scope).</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/regions", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminRegionController {

    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;

    @GetMapping
    public ResponseEntity<List<Region>> getRegions() {
        return ResponseEntity.ok(regionRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createRegion(@RequestBody RegionInput regionInput) {
        Region region = new Region();
        region.setName(regionInput.getName());
        regionRepository.save(region);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Region created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateRegion(@PathVariable Long id, @RequestBody RegionInput regionInput) {
        Region region = regionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Region not found: " + id));
        region.setName(regionInput.getName());
        regionRepository.save(region);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Region updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteRegion(@PathVariable Long id) {
        if (!regionRepository.existsById(id)) {
            throw new NotFoundException("Region not found: " + id);
        }
        long dependentDistricts = districtRepository.countByRegion_Id(id);
        if (dependentDistricts > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer cette région : %d District(s) en dépendent encore."
                            .formatted(dependentDistricts));
        }
        regionRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Region deleted"));
    }
}
