package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.InformationSubUnit;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.InformationSubUnitInput;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.InformationSubUnitRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD Super Admin pour {@link InformationSubUnit} (Ticket #11). Voir la javadoc de
 * {@link SuperAdminInformationUnitController} pour le fonctionnement du journal d'audit automatique.
 *
 * <p>La suppression est refusée tant que des {@code Information} référencent encore cette
 * sous-unité.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API - Information Sub Units", description = "CRUD des unités d'information (niveau 2)")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
@RequestMapping(path = "/api/super-admin/information-sub-units", produces = MediaType.APPLICATION_JSON_VALUE)
public class SuperAdminInformationSubUnitController {

    private final InformationSubUnitRepository informationSubUnitRepository;
    private final InformationRepository informationRepository;

    @GetMapping
    public ResponseEntity<List<InformationSubUnit>> getInformationSubUnits() {
        return ResponseEntity.ok(informationSubUnitRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<InformationSubUnit> createInformationSubUnit(@RequestBody InformationSubUnitInput input) {
        InformationSubUnit subUnit = new InformationSubUnit();
        subUnit.setName(input.getName());
        return ResponseEntity.ok(informationSubUnitRepository.save(subUnit));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InformationSubUnit> updateInformationSubUnit(@PathVariable Long id, @RequestBody InformationSubUnitInput input) {
        InformationSubUnit subUnit = informationSubUnitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Information sub unit not found: " + id));
        subUnit.setName(input.getName());
        return ResponseEntity.ok(informationSubUnitRepository.save(subUnit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteInformationSubUnit(@PathVariable Long id) {
        if (!informationSubUnitRepository.existsById(id)) {
            throw new NotFoundException("Information sub unit not found: " + id);
        }

        long informationCount = informationRepository.countByInformationSubUnitId(id);
        if (informationCount > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer cette sous-unité d'information : " + informationCount
                            + " Information" + (informationCount > 1 ? "s" : "") + " en dépendent encore.");
        }

        informationSubUnitRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Information sub unit deleted"));
    }
}
