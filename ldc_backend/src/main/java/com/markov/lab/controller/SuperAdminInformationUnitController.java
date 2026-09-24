package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.InformationUnit;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.InformationUnitInput;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.InformationUnitRepository;
import com.markov.lab.repository.SynthesisRepository;
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

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD Super Admin pour {@link InformationUnit} (Ticket #11). Le nom de cette classe suit la
 * convention {@code SuperAdmin<Entité>Controller} : {@link com.markov.lab.audit.SuperAdminAuditAspect}
 * journalise donc automatiquement chaque mutation, sans aucun appel explicite ici.
 *
 * <p>La suppression est refusée (jamais de cascade forcée) tant que des {@code Information} ou des
 * {@code Synthesis} référencent encore cette unité.</p>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API - Information Units", description = "CRUD des unités d'information (niveau 1)")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
@RequestMapping(path = "/api/super-admin/information-units", produces = MediaType.APPLICATION_JSON_VALUE)
public class SuperAdminInformationUnitController {

    private final InformationUnitRepository informationUnitRepository;
    private final InformationRepository informationRepository;
    private final SynthesisRepository synthesisRepository;

    @GetMapping
    public ResponseEntity<List<InformationUnit>> getInformationUnits() {
        return ResponseEntity.ok(informationUnitRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<InformationUnit> createInformationUnit(@RequestBody InformationUnitInput input) {
        InformationUnit unit = new InformationUnit();
        unit.setName(input.getName());
        return ResponseEntity.ok(informationUnitRepository.save(unit));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InformationUnit> updateInformationUnit(@PathVariable Long id, @RequestBody InformationUnitInput input) {
        InformationUnit unit = informationUnitRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Information unit not found: " + id));
        unit.setName(input.getName());
        return ResponseEntity.ok(informationUnitRepository.save(unit));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteInformationUnit(@PathVariable Long id) {
        if (!informationUnitRepository.existsById(id)) {
            throw new NotFoundException("Information unit not found: " + id);
        }

        long informationCount = informationRepository.countByInformationUnitId(id);
        long synthesisCount = synthesisRepository.countByInformationUnitId(id);
        if (informationCount > 0 || synthesisCount > 0) {
            List<String> parts = new ArrayList<>();
            if (informationCount > 0) {
                parts.add(informationCount + " Information" + (informationCount > 1 ? "s" : ""));
            }
            if (synthesisCount > 0) {
                parts.add(synthesisCount + " Synthese" + (synthesisCount > 1 ? "s" : ""));
            }
            throw new EntityHasDependentsException(
                    "Impossible de supprimer cette unité d'information : "
                            + String.join(" et ", parts) + " en dépendent encore.");
        }

        informationUnitRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Information unit deleted"));
    }
}
