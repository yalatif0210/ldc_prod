package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.Adjustment;
import com.markov.lab.entity.AdjustmentType;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.IntrantMvtData;
import com.markov.lab.entity.Report;
import com.markov.lab.entity.Role;
import com.markov.lab.repository.AdjustmentRepository;
import com.markov.lab.repository.AdjustmentTypeRepository;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.IntrantMvtDataRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.ReportRepository;
import com.markov.lab.repository.RoleRepository;
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
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD seam pour le Ticket #14 : suppression en cascade des {@link Adjustment} quand un
 * {@link IntrantMvtData} parent est supprimé via la console Super Admin, et traçage de l'action
 * (y compris la cascade) dans le journal d'audit générique (Ticket #7).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminIntrantMvtDataCascadeDeleteTest {

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
    private ReportRepository reportRepository;

    @Autowired
    private IntrantRepository intrantRepository;

    @Autowired
    private IntrantMvtDataRepository intrantMvtDataRepository;

    @Autowired
    private AdjustmentTypeRepository adjustmentTypeRepository;

    @Autowired
    private AdjustmentRepository adjustmentRepository;

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
        return userRepository.findByUsername(username).orElseThrow().getAccount().getId();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @Test
    void deletingAnIntrantMvtDataWithExistingAdjustmentsCascadesAndIsAudited() {
        String username = "audit-actor-imd-" + System.nanoTime();
        String token = signupAndGetToken(username);
        Long actorAccountId = accountIdOf(username);

        Report report = reportRepository.save(new Report());
        Intrant intrant = intrantRepository.save(new Intrant());
        AdjustmentType adjustmentType = adjustmentTypeRepository.save(newAdjustmentType());

        IntrantMvtData intrantMvtData = new IntrantMvtData();
        intrantMvtData.setReport(report);
        intrantMvtData.setIntrant(intrant);
        intrantMvtData.setEntryStock(10);
        intrantMvtData.setDistributionStock(2);
        intrantMvtData.setAvailableStock(8);
        intrantMvtData = intrantMvtDataRepository.save(intrantMvtData);

        Adjustment adjustment1 = newAdjustment(intrantMvtData, adjustmentType, 3, "Ajustement 1");
        Adjustment adjustment2 = newAdjustment(intrantMvtData, adjustmentType, -1, "Ajustement 2");
        adjustment1 = adjustmentRepository.save(adjustment1);
        adjustment2 = adjustmentRepository.save(adjustment2);

        Long intrantMvtDataId = intrantMvtData.getId();
        Long adjustment1Id = adjustment1.getId();
        Long adjustment2Id = adjustment2.getId();

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/intrant-mvt-data/" + intrantMvtDataId,
                HttpMethod.DELETE, entity, Object.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        // Le IntrantMvtData est supprime.
        assertThat(intrantMvtDataRepository.existsById(intrantMvtDataId)).isFalse();

        // Ses Adjustment enfants sont supprimes en cascade, sans erreur de contrainte FK.
        assertThat(adjustmentRepository.existsById(adjustment1Id)).isFalse();
        assertThat(adjustmentRepository.existsById(adjustment2Id)).isFalse();

        // L'action (y compris la cascade) est tracee dans le journal d'audit generique.
        List<AuditLog> deletions = auditLogRepository.findAll().stream()
                .filter(a -> "IntrantMvtData".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> intrantMvtDataId.equals(a.getEntityId()))
                .filter(a -> actorAccountId.equals(a.getAccountId()))
                .toList();
        assertThat(deletions).hasSize(1);
    }

    private static AdjustmentType newAdjustmentType() {
        AdjustmentType type = new AdjustmentType();
        type.setName("Type de test");
        type.setType("CORRECTION");
        return type;
    }

    private static Adjustment newAdjustment(IntrantMvtData parent, AdjustmentType type, int quantity, String comment) {
        Adjustment adjustment = new Adjustment();
        adjustment.setIntrantMvtData(parent);
        adjustment.setAdjustmentType(type);
        adjustment.setQuantity(quantity);
        adjustment.setComment(comment);
        return adjustment;
    }
}
