package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.MedicinesTransaction;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.SanguineProduct;
import com.markov.lab.entity.SanguineProductTransaction;
import com.markov.lab.entity.Structure;
import com.markov.lab.entity.Transaction;
import com.markov.lab.entity.User;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.MedicinesTransactionRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.SanguineProductRepository;
import com.markov.lab.repository.SanguineProductTransactionRepository;
import com.markov.lab.repository.StructureRepository;
import com.markov.lab.repository.TransactionRepository;
import com.markov.lab.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticket #8 : CRUD Super Admin sur les Transferts (entité {@code Transaction}) et leurs lignes
 * filles ({@code SanguineProductTransaction}/{@code MedicinesTransaction}).
 *
 * <p>Le test central ({@link #deletingATransactionWithExistingLinesCascadesAndSucceeds()}) est
 * le seam TDD demandé par le ticket : avant l'implémentation du service, aucune cascade
 * (ni JPA, ni applicative) n'existe entre {@code Transaction} et ses lignes filles — supprimer un
 * Transfert qui a des lignes échoue alors sur la contrainte de clé étrangère {@code transaction_id}
 * (violation détectée, 500). Ce test prouve que la suppression réussit désormais et que les
 * lignes filles sont bien supprimées de la base.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminTransactionControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private StructureRepository structureRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private SanguineProductRepository sanguineProductRepository;

    @Autowired
    private IntrantRepository intrantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private SanguineProductTransactionRepository sanguineProductTransactionRepository;

    @Autowired
    private MedicinesTransactionRepository medicinesTransactionRepository;

    private static Long superAdminRoleId;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository) {
        superAdminRoleId = roleRepository.save(newRole("SUPER_ADMIN")).getId();
    }

    private static Role newRole(String name) {
        Role role = new Role();
        role.setRole(name);
        return role;
    }

    private String signupAndGetToken(String username) {
        SignupRequest signup = new SignupRequest(
                username, "Test User", "0000000000", "Password1!",
                superAdminRoleId.intValue(), List.of(), List.of()
        );
        ResponseEntity<Object> response = restTemplate.postForEntity("/api/auth/signup", signup, Object.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, "Password1!"), LoginResponse.class);
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        return loginResponse.getBody().access_token();
    }

    private Long accountIdOf(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return user.getAccount().getId();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Structure newStructure(String name) {
        Structure structure = new Structure();
        structure.setName(name);
        structure.setActive(true);
        return structureRepository.save(structure);
    }

    private Equipment newEquipment(String name) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        return equipmentRepository.save(equipment);
    }

    private SanguineProduct newSanguineProduct(String name) {
        SanguineProduct product = new SanguineProduct();
        product.setName(name);
        return sanguineProductRepository.save(product);
    }

    private Intrant newIntrant(String name) {
        Intrant intrant = new Intrant();
        intrant.setName(name);
        return intrantRepository.save(intrant);
    }

    @Test
    void deletingATransactionWithExistingLinesCascadesAndSucceeds() {
        String token = signupAndGetToken("txn-delete-actor-" + System.nanoTime());
        long suffix = System.nanoTime();
        Structure origin = newStructure("Origin-" + suffix);
        Structure destination = newStructure("Destination-" + suffix);
        Equipment equipment = newEquipment("Equipment-" + suffix);
        Equipment equipmentDestinataire = newEquipment("EquipmentDest-" + suffix);
        SanguineProduct product = newSanguineProduct("Product-" + suffix);
        Intrant intrant = newIntrant("Intrant-" + suffix);

        Transaction transaction = new Transaction();
        transaction.setOrigin(origin);
        transaction.setDestination(destination);
        transaction.setEquipment(equipment);
        transaction.setEquipment_destinataire(equipmentDestinataire);
        transaction = transactionRepository.save(transaction);

        SanguineProductTransaction sanguineLine = new SanguineProductTransaction();
        sanguineLine.setTransaction(transaction);
        sanguineLine.setSanguineProduct(product);
        sanguineLine.setQuantity(5);
        sanguineProductTransactionRepository.save(sanguineLine);

        MedicinesTransaction medicineLine = new MedicinesTransaction();
        medicineLine.setTransaction(transaction);
        medicineLine.setIntrant(intrant);
        medicineLine.setQuantity(3);
        medicinesTransactionRepository.save(medicineLine);

        Long transactionId = transaction.getId();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/transactions/" + transactionId, HttpMethod.DELETE, deleteEntity, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transactionRepository.existsById(transactionId)).isFalse();
        assertThat(sanguineProductTransactionRepository.findById(sanguineLine.getId())).isEmpty();
        assertThat(medicinesTransactionRepository.findById(medicineLine.getId())).isEmpty();

        List<AuditLog> deletions = auditLogRepository.findAll().stream()
                .filter(a -> "Transaction".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> transactionId.equals(a.getEntityId()))
                .toList();
        assertThat(deletions).hasSize(1);
    }

    @Test
    void creatingUpdatingAndListingATransactionThroughTheConsoleWorksAndIsAudited() {
        String username = "txn-crud-actor-" + System.nanoTime();
        String token = signupAndGetToken(username);
        Long actorAccountId = accountIdOf(username);

        long suffix = System.nanoTime();
        Structure origin = newStructure("Origin-" + suffix);
        Structure destination = newStructure("Destination-" + suffix);
        Equipment equipment = newEquipment("Equipment-" + suffix);
        Equipment equipmentDestinataire = newEquipment("EquipmentDest-" + suffix);
        SanguineProduct product = newSanguineProduct("Product-" + suffix);
        Intrant intrant = newIntrant("Intrant-" + suffix);

        Map<String, Object> createBody = Map.of(
                "originId", origin.getId(),
                "destinationId", destination.getId(),
                "equipmentId", equipment.getId(),
                "equipmentDestinataireId", equipmentDestinataire.getId(),
                "approved", false,
                "isRejected", false,
                "sanguineProductTransactions", List.of(Map.of("sanguine_product_id", product.getId(), "quantity", 10)),
                "medicinesTransactions", List.of(Map.of("intrant_id", intrant.getId(), "quantity", 4))
        );
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<Map> createResponse = restTemplate.exchange(
                "/api/super-admin/transactions", HttpMethod.POST, createEntity, Map.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number createdId = (Number) createResponse.getBody().get("id");
        assertThat(createdId).isNotNull();
        Long transactionId = createdId.longValue();

        assertThat(sanguineProductTransactionRepository.findAll().stream()
                .anyMatch(l -> l.getTransaction().getId().equals(transactionId))).isTrue();
        assertThat(medicinesTransactionRepository.findAll().stream()
                .anyMatch(l -> l.getTransaction().getId().equals(transactionId))).isTrue();

        List<AuditLog> creations = auditLogRepository.findAll().stream()
                .filter(a -> "Transaction".equals(a.getEntityType()) && a.getAction() == AuditAction.CREATE)
                .filter(a -> actorAccountId.equals(a.getAccountId()))
                .toList();
        assertThat(creations).isNotEmpty();

        // GET (liste) : Structures origine/destination, Équipement, statut d'approbation visibles.
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/super-admin/transactions", HttpMethod.GET, readEntity, List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> listedRow = ((List<Map<String, Object>>) listResponse.getBody()).stream()
                .filter(row -> transactionId.equals(((Number) row.get("id")).longValue()))
                .findFirst().orElseThrow();
        assertThat(((Map) listedRow.get("origin")).get("id")).isEqualTo(((Number) origin.getId()).intValue());
        assertThat(((Map) listedRow.get("destination")).get("id")).isEqualTo(((Number) destination.getId()).intValue());
        assertThat(((Map) listedRow.get("equipment")).get("id")).isEqualTo((int) equipment.getId());
        assertThat(listedRow.get("approved")).isEqualTo(false);

        // PUT : modification du Transfert et de ses lignes.
        Map<String, Object> updateBody = Map.of(
                "originId", origin.getId(),
                "destinationId", destination.getId(),
                "equipmentId", equipment.getId(),
                "equipmentDestinataireId", equipmentDestinataire.getId(),
                "approved", true,
                "isRejected", false,
                "sanguineProductTransactions", List.of(Map.of("sanguine_product_id", product.getId(), "quantity", 99)),
                "medicinesTransactions", List.of()
        );
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
                "/api/super-admin/transactions/" + transactionId, HttpMethod.PUT, updateEntity, Map.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("approved")).isEqualTo(true);

        List<SanguineProductTransaction> remainingSanguineLines = sanguineProductTransactionRepository.findAll().stream()
                .filter(l -> l.getTransaction().getId().equals(transactionId))
                .toList();
        assertThat(remainingSanguineLines).hasSize(1);
        assertThat(remainingSanguineLines.get(0).getQuantity()).isEqualTo(99);
        assertThat(medicinesTransactionRepository.findAll().stream()
                .noneMatch(l -> l.getTransaction().getId().equals(transactionId))).isTrue();

        List<AuditLog> updates = auditLogRepository.findAll().stream()
                .filter(a -> "Transaction".equals(a.getEntityType()) && a.getAction() == AuditAction.UPDATE)
                .filter(a -> transactionId.equals(a.getEntityId()))
                .toList();
        assertThat(updates).isNotEmpty();
    }
}
