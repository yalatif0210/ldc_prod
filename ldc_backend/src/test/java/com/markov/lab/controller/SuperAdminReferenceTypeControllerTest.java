package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.Role;
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
 * Seam d'intégration pour le Ticket #12 : CRUD Super Admin des cinq référentiels de types
 * ({@code Status}, {@code Month}, {@code AdjustmentType}, {@code IntrantType},
 * {@code SynthesisType}).
 *
 * <p>Couverture détaillée sur {@code Month} : blocage de la suppression tant qu'une
 * {@code Period} en dépend, message indiquant le nombre de dépendants, puis succès de la
 * suppression une fois le dépendant retiré. Les quatre autres référentiels suivent
 * exactement le même motif de contrôleur (voir les classes {@code SuperAdmin*Controller}
 * correspondantes) ; ils sont donc couverts plus légèrement ici (CRUD heureux, sans
 * dupliquer le scénario de blocage sur chacun).</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminReferenceTypeControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

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
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(String token, String path) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<List> response = restTemplate.exchange(path, HttpMethod.GET, entity, List.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    // ---- Month : scenario detaille (blocage sur dependant + succes) ----

    @Test
    void deletingAMonthReferencedByAPeriodIsBlockedThenSucceedsOnceTheDependentIsRemoved() {
        String token = signupAndGetToken("month-admin-" + System.nanoTime());
        String monthLabel = "MoisTest" + System.nanoTime();

        HttpEntity<Map<String, Object>> createMonth = new HttpEntity<>(Map.of("month", monthLabel), authHeaders(token));
        ResponseEntity<Object> createResponse = restTemplate.postForEntity("/api/super-admin/months", createMonth, Object.class);
        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();

        Long monthId = list(token, "/api/super-admin/months").stream()
                .filter(m -> monthLabel.equals(m.get("month")))
                .map(m -> ((Number) m.get("id")).longValue())
                .findFirst().orElseThrow();

        Map<String, Object> periodInput = Map.of(
                "monthName", monthLabel,
                "periodName", "Periode Ref Test " + System.nanoTime(),
                "startDate", "2026-03-01",
                "endDate", "2026-03-31"
        );
        HttpEntity<Map<String, Object>> createPeriod = new HttpEntity<>(periodInput, authHeaders(token));
        ResponseEntity<Object> periodResponse = restTemplate.postForEntity("/api/super-admin/periods", createPeriod, Object.class);
        assertThat(periodResponse.getStatusCode().is2xxSuccessful()).isTrue();

        Long periodId = list(token, "/api/super-admin/periods").stream()
                .filter(p -> periodInput.get("periodName").equals(p.get("periodName")))
                .map(p -> ((Number) p.get("id")).longValue())
                .findFirst().orElseThrow();

        // Blocage : le mois est reference par une periode active.
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> blockedDelete = restTemplate.exchange(
                "/api/super-admin/months/" + monthId, HttpMethod.DELETE, readEntity, Map.class);
        assertThat(blockedDelete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String message = (String) blockedDelete.getBody().get("description");
        assertThat(message).contains("1").containsIgnoringCase("période");

        // Le mois existe toujours.
        assertThat(list(token, "/api/super-admin/months").stream().anyMatch(m -> monthId.equals(((Number) m.get("id")).longValue())))
                .isTrue();

        // On retire le dependant, la suppression reussit alors.
        ResponseEntity<Object> deletePeriod = restTemplate.exchange(
                "/api/super-admin/periods/" + periodId, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deletePeriod.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Object> deleteMonth = restTemplate.exchange(
                "/api/super-admin/months/" + monthId, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteMonth.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(list(token, "/api/super-admin/months").stream().anyMatch(m -> monthId.equals(((Number) m.get("id")).longValue())))
                .isFalse();
    }

    @Test
    void updatingAMonthWithoutDependentsSucceeds() {
        String token = signupAndGetToken("month-update-admin-" + System.nanoTime());
        String monthLabel = "MoisMaj" + System.nanoTime();
        String updatedLabel = monthLabel + "-Maj";

        HttpEntity<Map<String, Object>> createMonth = new HttpEntity<>(Map.of("month", monthLabel), authHeaders(token));
        restTemplate.postForEntity("/api/super-admin/months", createMonth, Object.class);

        Long monthId = list(token, "/api/super-admin/months").stream()
                .filter(m -> monthLabel.equals(m.get("month")))
                .map(m -> ((Number) m.get("id")).longValue())
                .findFirst().orElseThrow();

        HttpEntity<Map<String, Object>> updateMonth = new HttpEntity<>(Map.of("month", updatedLabel), authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/months/" + monthId, HttpMethod.PUT, updateMonth, Object.class);
        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(list(token, "/api/super-admin/months").stream()
                .anyMatch(m -> updatedLabel.equals(m.get("month"))))
                .isTrue();

        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/months/" + monthId, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // ---- Autres referentiels : CRUD heureux, motif de controleur identique a Month ----

    @Test
    void statusCrudSucceedsWithoutDependents() {
        String token = signupAndGetToken("status-admin-" + System.nanoTime());
        String label = "StatutTest" + System.nanoTime();

        restTemplate.postForEntity("/api/super-admin/statuses", new HttpEntity<>(Map.of("status", label), authHeaders(token)), Object.class);
        Long id = list(token, "/api/super-admin/statuses").stream()
                .filter(s -> label.equals(s.get("status")))
                .map(s -> ((Number) s.get("id")).longValue())
                .findFirst().orElseThrow();

        String updatedLabel = label + "-Maj";
        restTemplate.exchange("/api/super-admin/statuses/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("status", updatedLabel), authHeaders(token)), Object.class);
        assertThat(list(token, "/api/super-admin/statuses").stream().anyMatch(s -> updatedLabel.equals(s.get("status")))).isTrue();

        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/statuses/" + id, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void adjustmentTypeCrudSucceedsWithoutDependents() {
        String token = signupAndGetToken("adjtype-admin-" + System.nanoTime());
        String label = "AjustementTest" + System.nanoTime();

        restTemplate.postForEntity("/api/super-admin/adjustment-types",
                new HttpEntity<>(Map.of("name", label, "type", "IN"), authHeaders(token)), Object.class);
        Long id = list(token, "/api/super-admin/adjustment-types").stream()
                .filter(a -> label.equals(a.get("name")))
                .map(a -> ((Number) a.get("id")).longValue())
                .findFirst().orElseThrow();

        String updatedLabel = label + "-Maj";
        restTemplate.exchange("/api/super-admin/adjustment-types/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", updatedLabel, "type", "OUT"), authHeaders(token)), Object.class);
        assertThat(list(token, "/api/super-admin/adjustment-types").stream().anyMatch(a -> updatedLabel.equals(a.get("name")))).isTrue();

        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/adjustment-types/" + id, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void intrantTypeCrudSucceedsWithoutDependents() {
        String token = signupAndGetToken("intrtype-admin-" + System.nanoTime());
        String label = "TypeIntrantTest" + System.nanoTime();

        restTemplate.postForEntity("/api/super-admin/intrant-types",
                new HttpEntity<>(Map.of("name", label), authHeaders(token)), Object.class);
        Long id = list(token, "/api/super-admin/intrant-types").stream()
                .filter(i -> label.equals(i.get("name")))
                .map(i -> ((Number) i.get("id")).longValue())
                .findFirst().orElseThrow();

        String updatedLabel = label + "-Maj";
        restTemplate.exchange("/api/super-admin/intrant-types/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", updatedLabel), authHeaders(token)), Object.class);
        assertThat(list(token, "/api/super-admin/intrant-types").stream().anyMatch(i -> updatedLabel.equals(i.get("name")))).isTrue();

        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/intrant-types/" + id, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void synthesisTypeCrudSucceedsWithoutDependents() {
        String token = signupAndGetToken("synthtype-admin-" + System.nanoTime());
        String label = "TypeSyntheseTest" + System.nanoTime();

        restTemplate.postForEntity("/api/super-admin/synthesis-types",
                new HttpEntity<>(Map.of("type", label), authHeaders(token)), Object.class);
        Long id = list(token, "/api/super-admin/synthesis-types").stream()
                .filter(s -> label.equals(s.get("type")))
                .map(s -> ((Number) s.get("id")).longValue())
                .findFirst().orElseThrow();

        String updatedLabel = label + "-Maj";
        restTemplate.exchange("/api/super-admin/synthesis-types/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("type", updatedLabel), authHeaders(token)), Object.class);
        assertThat(list(token, "/api/super-admin/synthesis-types").stream().anyMatch(s -> updatedLabel.equals(s.get("type")))).isTrue();

        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/synthesis-types/" + id, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
