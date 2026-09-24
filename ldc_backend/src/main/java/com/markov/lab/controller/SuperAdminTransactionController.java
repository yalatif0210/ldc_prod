package com.markov.lab.controller;

import com.markov.lab.controller.dto.ApiSuccessResponse;
import com.markov.lab.controller.dto.SuperAdminTransactionDTO;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.SanguineProduct;
import com.markov.lab.input.SuperAdminTransactionInput;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.SanguineProductRepository;
import com.markov.lab.service.SuperAdminTransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD Super Admin sur les Transferts (entité {@code Transaction}) et leurs lignes filles
 * (Ticket #8). Nommage {@code SuperAdmin<Entité>Controller} obligatoire : c'est la convention
 * utilisée par {@link com.markov.lab.audit.SuperAdminAuditAspect} (Ticket #7) pour tracer
 * automatiquement chaque mutation dans le journal d'audit, sans câblage manuel ici.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Super Admin Transaction API", description = "CRUD Super Admin sur les Transferts")
@RequestMapping(path = "/api/super-admin/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class SuperAdminTransactionController {

    private final SuperAdminTransactionService superAdminTransactionService;
    private final SanguineProductRepository sanguineProductRepository;
    private final IntrantRepository intrantRepository;

    @GetMapping
    public ResponseEntity<List<SuperAdminTransactionDTO>> getTransactions() {
        return ResponseEntity.ok(superAdminTransactionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuperAdminTransactionDTO> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(superAdminTransactionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<SuperAdminTransactionDTO> createTransaction(@RequestBody SuperAdminTransactionInput input) {
        return ResponseEntity.ok(superAdminTransactionService.create(input));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SuperAdminTransactionDTO> updateTransaction(
            @PathVariable Long id, @RequestBody SuperAdminTransactionInput input) {
        return ResponseEntity.ok(superAdminTransactionService.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiSuccessResponse> deleteTransaction(@PathVariable Long id) {
        superAdminTransactionService.delete(id);
        return ResponseEntity.ok(new ApiSuccessResponse(200, "Transaction deleted"));
    }

    // ---- Données de référence pour les selects du formulaire de lignes ----

    @GetMapping("/reference/sanguine-products")
    public ResponseEntity<List<SanguineProduct>> getSanguineProducts() {
        return ResponseEntity.ok(sanguineProductRepository.findAll());
    }

    @GetMapping("/reference/intrants")
    public ResponseEntity<List<Intrant>> getIntrants() {
        return ResponseEntity.ok(intrantRepository.findAll());
    }
}
