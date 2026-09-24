package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.Account;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.User;
import com.markov.lab.repository.AuditLogRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam d'intégration convenu pour le Ticket #7 : journal d'audit générique sur la console
 * Super Admin. Vérifie que le point d'interception unique ({@code SuperAdminAuditAspect}) produit
 * automatiquement une entrée {@link AuditLog} correcte pour au moins deux entités différentes
 * (création d'un {@code Period}, désactivation d'un {@code Account}) — sans qu'aucun appel
 * explicite au journal d'audit n'existe dans {@code SuperAdminController}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminAuditLogTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

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

    private Long accountIdOf(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return user.getAccount().getId();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @Test
    void creatingAPeriodProducesAnAuditLogEntryWithoutAnyControllerChange() {
        String username = "audit-actor-period-" + System.nanoTime();
        String token = signupAndGetToken(username);
        Long actorAccountId = accountIdOf(username);

        Map<String, Object> periodInput = Map.of(
                "monthName", "Janvier",
                "periodName", "Periode Audit Test " + System.nanoTime(),
                "startDate", "2026-01-01",
                "endDate", "2026-01-31"
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(periodInput, authHeaders(token));
        ResponseEntity<Object> response = restTemplate.postForEntity("/api/super-admin/periods", entity, Object.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<AuditLog> periodCreations = auditLogRepository.findAll().stream()
                .filter(a -> "Period".equals(a.getEntityType()) && a.getAction() == AuditAction.CREATE)
                .filter(a -> actorAccountId.equals(a.getAccountId()))
                .toList();

        assertThat(periodCreations).isNotEmpty();
        AuditLog entry = periodCreations.get(periodCreations.size() - 1);
        assertThat(entry.getTimestamp()).isNotNull();
        assertThat(entry.getSnapshot()).contains("Periode Audit Test").contains("\"before\":null");
    }

    @Test
    void deactivatingAnAccountProducesAnAuditLogEntryWithBeforeAndAfterState() {
        long suffix = System.nanoTime();
        String actorUsername = "audit-actor-account-" + suffix;
        String targetUsername = "audit-target-account-" + suffix;

        String actorToken = signupAndGetToken(actorUsername);
        Long actorAccountId = accountIdOf(actorUsername);

        signupAndGetToken(targetUsername);
        Long targetAccountId = accountIdOf(targetUsername);

        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(actorToken));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/accounts/" + targetAccountId + "/deactivate",
                HttpMethod.PATCH, entity, Object.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<AuditLog> accountUpdates = auditLogRepository.findAll().stream()
                .filter(a -> "Account".equals(a.getEntityType()) && a.getAction() == AuditAction.UPDATE)
                .filter(a -> targetAccountId.equals(a.getEntityId()))
                .toList();

        assertThat(accountUpdates).hasSize(1);
        AuditLog entry = accountUpdates.get(0);
        assertThat(entry.getAccountId()).isEqualTo(actorAccountId);
        assertThat(entry.getSnapshot()).contains("\"isActive\":true").contains("\"isActive\":false");
        // Le mot de passe (même hashé) ne doit jamais être stocké dans le journal d'audit.
        assertThat(entry.getSnapshot().toLowerCase()).doesNotContain("password");
    }

    @Test
    void auditLogEndpointIsFilterableAndReadOnly() {
        String token = signupAndGetToken("audit-reader-" + System.nanoTime());

        Map<String, Object> periodInput = Map.of(
                "monthName", "Fevrier",
                "periodName", "Periode Filtrable " + System.nanoTime(),
                "startDate", "2026-02-01",
                "endDate", "2026-02-28"
        );
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(periodInput, authHeaders(token));
        restTemplate.postForEntity("/api/super-admin/periods", createEntity, Object.class);

        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> listResponse = restTemplate.exchange(
                "/api/super-admin/audit-logs?entityType=Period&action=CREATE&page=0&size=50",
                HttpMethod.GET, readEntity, Map.class);

        assertThat(listResponse.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> content = (List<Map<String, Object>>) listResponse.getBody().get("content");
        assertThat(content).isNotEmpty();
        assertThat(content).allSatisfy(row -> assertThat(row.get("entityType")).isEqualTo("Period"));

        // AuditLog est en lecture seule : aucune route de modification/suppression n'existe.
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/audit-logs/1", HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isFalse();

        ResponseEntity<Object> postResponse = restTemplate.exchange(
                "/api/super-admin/audit-logs", HttpMethod.POST, createEntity, Object.class);
        assertThat(postResponse.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
