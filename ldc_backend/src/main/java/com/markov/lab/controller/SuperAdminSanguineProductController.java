package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.entity.SanguineProduct;
import com.markov.lab.exceptions.EntityHasDependentsException;
import com.markov.lab.exceptions.NotFoundException;
import com.markov.lab.input.SanguineProductInput;
import com.markov.lab.repository.SanguineProductRepository;
import com.markov.lab.repository.SanguineProductTransactionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.markov.lab.controller.SuperAdminEquipmentController.buildMessage;
import static com.markov.lab.controller.SuperAdminEquipmentController.putIfPositive;

/**
 * CRUD Super Admin sur l'entité structurelle {@link SanguineProduct} (Ticket #13). Voir la
 * javadoc de {@link SuperAdminEquipmentController} pour le motif général.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin API", description = "Super Admin management endpoints")
@RequestMapping(path = "/api/super-admin/sanguine-products", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminSanguineProductController {

    private final SanguineProductRepository sanguineProductRepository;
    private final SanguineProductTransactionRepository sanguineProductTransactionRepository;

    @GetMapping
    public ResponseEntity<List<SanguineProduct>> getSanguineProducts() {
        return ResponseEntity.ok(sanguineProductRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ApiSuccessResponse> createSanguineProduct(@RequestBody SanguineProductInput input) {
        SanguineProduct sanguineProduct = new SanguineProduct();
        sanguineProduct.setName(input.getName());
        sanguineProductRepository.save(sanguineProduct);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "SanguineProduct created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> updateSanguineProduct(@PathVariable Long id, @RequestBody SanguineProductInput input) {
        SanguineProduct sanguineProduct = sanguineProductRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SanguineProduct not found: " + id));
        sanguineProduct.setName(input.getName());
        sanguineProductRepository.save(sanguineProduct);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "SanguineProduct updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteSanguineProduct(@PathVariable Long id) {
        if (!sanguineProductRepository.existsById(id)) {
            throw new NotFoundException("SanguineProduct not found: " + id);
        }
        ensureDeletable(id);
        sanguineProductRepository.deleteById(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "SanguineProduct deleted"));
    }

    private void ensureDeletable(Long sanguineProductId) {
        Map<String, Long> dependents = new LinkedHashMap<>();
        putIfPositive(dependents, "Transaction(s) de produit sanguin",
                sanguineProductTransactionRepository.countBySanguineProduct_Id(sanguineProductId));

        if (!dependents.isEmpty()) {
            throw new EntityHasDependentsException(buildMessage("ce compte sanguin", dependents));
        }
    }
}
