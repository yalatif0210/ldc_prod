package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Information;
import com.markov.lab.entity.InformationUnit;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.User;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.InformationUnitRepository;
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
 * Ticket #11 : CRUD Super Admin sur {@code InformationUnit}. Vérifie en détail :
 * <ul>
 *     <li>la création et la modification réussissent et produisent une entrée d'audit ;</li>
 *     <li>la suppression est REFUSEE (409) quand une {@code Information} référence encore l'unité,
 *     avec un message précisant le nombre et le type de dépendants, et sans aucune écriture
 *     (l'unité et son dépendant existent toujours) ;</li>
 *     <li>la suppression réussit (et produit une entrée d'audit DELETE) dès que ce dépendant est
 *     retiré.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminInformationUnitControllerTest {

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
    private InformationUnitRepository informationUnitRepository;

    @Autowired
    private InformationRepository informationRepository;

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

    @SuppressWarnings("unchecked")
    private Long createInformationUnitViaApi(String token, String name) {
        Map<String, Object> body = Map.of("name", name);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, authHeaders(token));
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/super-admin/information-units", entity, Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    void creatingAndUpdatingAnInformationUnitProducesAuditLogEntries() {
        String username = "audit-actor-iu-" + System.nanoTime();
        String token = signupAndGetToken(username);
        Long actorAccountId = accountIdOf(username);

        Long unitId = createInformationUnitViaApi(token, "Unite Audit Test " + System.nanoTime());

        List<AuditLog> creations = auditLogRepository.findAll().stream()
                .filter(a -> "InformationUnit".equals(a.getEntityType()) && a.getAction() == AuditAction.CREATE)
                .filter(a -> actorAccountId.equals(a.getAccountId()))
                .toList();
        assertThat(creations).isNotEmpty();

        Map<String, Object> updateBody = Map.of("name", "Unite Renommee " + System.nanoTime());
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/information-units/" + unitId, HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();

        List<AuditLog> updates = auditLogRepository.findAll().stream()
                .filter(a -> "InformationUnit".equals(a.getEntityType()) && a.getAction() == AuditAction.UPDATE)
                .filter(a -> unitId.equals(a.getEntityId()))
                .toList();
        assertThat(updates).hasSize(1);
    }

    @Test
    void deletingAnInformationUnitReferencedByAnInformationIsRefusedWithAClearMessageAndNothingIsDeleted() {
        String token = signupAndGetToken("audit-actor-iu-block-" + System.nanoTime());
        Long unitId = createInformationUnitViaApi(token, "Unite Bloquee " + System.nanoTime());

        InformationUnit unit = informationUnitRepository.findById(unitId).orElseThrow();
        Information dependent = new Information();
        dependent.setIsActive(true);
        dependent.setInformationUnit(unit);
        Long dependentId = informationRepository.save(dependent).getId();

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
                "/api/super-admin/information-units/" + unitId, HttpMethod.DELETE, deleteEntity, Map.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String message = String.valueOf(deleteResponse.getBody().get("description"));
        assertThat(message).contains("1").contains("Information");

        // Rien n'a été supprimé : ni l'unité, ni son dépendant.
        assertThat(informationUnitRepository.existsById(unitId)).isTrue();
        assertThat(informationRepository.existsById(dependentId)).isTrue();

        // Aucune entrée d'audit DELETE n'a été créée puisque la suppression a échoué.
        List<AuditLog> deletions = auditLogRepository.findAll().stream()
                .filter(a -> "InformationUnit".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> unitId.equals(a.getEntityId()))
                .toList();
        assertThat(deletions).isEmpty();
    }

    @Test
    void deletingAnInformationUnitWithoutDependentsSucceedsAndProducesAnAuditLogEntry() {
        String username = "audit-actor-iu-ok-" + System.nanoTime();
        String token = signupAndGetToken(username);
        Long actorAccountId = accountIdOf(username);
        Long unitId = createInformationUnitViaApi(token, "Unite Sans Dependant " + System.nanoTime());

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/information-units/" + unitId, HttpMethod.DELETE, deleteEntity, Object.class);

        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(informationUnitRepository.existsById(unitId)).isFalse();

        List<AuditLog> deletions = auditLogRepository.findAll().stream()
                .filter(a -> "InformationUnit".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> unitId.equals(a.getEntityId()))
                .filter(a -> actorAccountId.equals(a.getAccountId()))
                .toList();
        assertThat(deletions).hasSize(1);
    }
}
