package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.InformationSubSubUnit;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.InformationSubSubUnitInput;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.InformationSubSubUnitRepository;
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
 * CRUD Super Admin pour {@link InformationSubSubUnit} (Ticket #11). Voir la javadoc de
 * {@link SuperAdminInformationUnitController} pour le fonctionnement du journal d'audit automatique.
 *
 * <p>La suppression est refusée tant que des {@code Information} référencent encore cette
 * sous-sous-unité.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API - Information Sub Sub Units", description = "CRUD des unités d'information (niveau 3)")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
@RequestMapping(path = "/api/super-admin/information-sub-sub-units", produces = MediaType.APPLICATION_JSON_VALUE)
public class SuperAdminInformationSubSubUnitController {

    private final InformationSubSubUnitRepository informationSubSubUnitRepository;
    private final InformationRepository informationRepository;

    @GetMapping
    public ResponseEntity<List<InformationSubSubUnit>> getInformationSubSubUnits() {
        return ResponseEntity.ok(informationSubSubUnitRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<InformationSubSubUnit> createInformationSubSubUnit(@RequestBody InformationSubSubUnitInput input) {
        InformationSubSubUnit subSubUnit = new InformationSubSubUnit();
        subSubUnit.setName(input.getName());
        return ResponseEntity.ok(informationSubSubUnitRepository.save(subSubUnit));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InformationSubSubUnit> updateInformationSubSubUnit(@PathVariable Long id, @RequestBody InformationSubSubUnitInput input) {
        InformationSubSubUnit subSubUnit = informationSubSubUnitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Information sub sub unit not found: " + id));
        subSubUnit.setName(input.getName());
        return ResponseEntity.ok(informationSubSubUnitRepository.save(subSubUnit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteInformationSubSubUnit(@PathVariable Long id) {
        if (!informationSubSubUnitRepository.existsById(id)) {
            throw new NotFoundException("Information sub sub unit not found: " + id);
        }

        long informationCount = informationRepository.countByInformationSubSubUnitId(id);
        if (informationCount > 0) {
            throw new EntityHasDependentsException(
                    "Impossible de supprimer cette sous-sous-unité d'information : " + informationCount
                            + " Information" + (informationCount > 1 ? "s" : "") + " en dépendent encore.");
        }

        informationSubSubUnitRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Information sub sub unit deleted"));
    }
}
