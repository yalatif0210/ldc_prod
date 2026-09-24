package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.Synthesis;
import com.markov.lab.entity.SynthesisType;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.SynthesisRepository;
import com.markov.lab.repository.SynthesisTypeRepository;
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
 * Ticket #15 : CRUD Super Admin sur l'entité feuille {@code Synthesis} — à ne pas confondre avec
 * l'écran frontend {@code synthesis-admin} déjà existant, qui gère en réalité {@code Report} sous
 * un nom trompeur. Couverture légère (création, modification, suppression + audit),
 * complémentaire de la couverture détaillée sur {@code IntrantCmmConfig}
 * ({@link SuperAdminIntrantCmmConfigControllerTest}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminSynthesisControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SynthesisTypeRepository synthesisTypeRepository;

    @Autowired
    private SynthesisRepository synthesisRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void createUpdateDeleteSynthesisProducesAuditLogEntries() {
        long suffix = System.nanoTime();
        String token = signupAndGetToken("synthesis-admin-" + suffix);

        SynthesisType type = synthesisTypeRepository.save(newSynthesisType("Type-" + suffix));

        // ---- CREATE ----
        Map<String, Object> createBody = Map.of(
                "item", "Item-" + suffix,
                "synthesisTypeId", type.getId()
        );
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<Object> createResponse = restTemplate.postForEntity(
                "/api/super-admin/syntheses", createEntity, Object.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Synthesis> created = synthesisRepository.findAll().stream()
                .filter(s -> ("Item-" + suffix).equals(s.getItem()))
                .toList();
        assertThat(created).hasSize(1);
        Long synthesisId = created.get(0).getId();

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "Synthesis".equals(a.getEntityType())
                        && a.getAction() == AuditAction.CREATE
                        && synthesisId.equals(a.getEntityId()))).isTrue();

        // ---- UPDATE ----
        Map<String, Object> updateBody = Map.of(
                "item", "Item-updated-" + suffix,
                "synthesisTypeId", type.getId()
        );
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/syntheses/" + synthesisId, HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(synthesisRepository.findById(synthesisId).orElseThrow().getItem())
                .isEqualTo("Item-updated-" + suffix);

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "Synthesis".equals(a.getEntityType())
                        && a.getAction() == AuditAction.UPDATE
                        && synthesisId.equals(a.getEntityId()))).isTrue();

        // ---- DELETE (directe, sans blocage : entité feuille) ----
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/syntheses/" + synthesisId, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(synthesisRepository.existsById(synthesisId)).isFalse();

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "Synthesis".equals(a.getEntityType())
                        && a.getAction() == AuditAction.DELETE
                        && synthesisId.equals(a.getEntityId()))).isTrue();
    }

    private SynthesisType newSynthesisType(String type) {
        SynthesisType synthesisType = new SynthesisType();
        synthesisType.setType(type);
        return synthesisType;
    }
}
