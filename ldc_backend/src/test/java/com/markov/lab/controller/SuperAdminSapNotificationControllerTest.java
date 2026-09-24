package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.Structure;
import com.markov.lab.entity.User;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.SapNotificationRepository;
import com.markov.lab.repository.StructureRepository;
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
 * Ticket #9 : CRUD Notifications (SapNotification) depuis la console Super Admin.
 *
 * <p>Vérifie le comportement observable attendu par le ticket : la liste expose la Structure
 * émettrice / l'Équipement / l'Intrant / le statut résolu-rejeté, la création/modification/
 * suppression fonctionnent, et chaque mutation produit une entrée {@link AuditLog} correcte via
 * le point d'interception générique {@code SuperAdminAuditAspect} (Ticket #7) — sans qu'aucun
 * appel explicite au journal d'audit n'existe dans {@code SuperAdminSapNotificationController}.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminSapNotificationControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StructureRepository structureRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private IntrantRepository intrantRepository;

    @Autowired
    private SapNotificationRepository sapNotificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static Long superAdminRoleId;
    private static Long regularRoleId;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository) {
        superAdminRoleId = roleRepository.save(newRole("SUPER_ADMIN")).getId();
        regularRoleId = roleRepository.save(newRole("ADMIN")).getId();
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
        return structure;
    }

    private Equipment newEquipment(String name) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        return equipment;
    }

    private Intrant newIntrant(String name) {
        Intrant intrant = new Intrant();
        intrant.setName(name);
        return intrant;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findById(List<Map<String, Object>> notifications, long id) {
        return notifications.stream()
                .filter(n -> ((Number) n.get("id")).longValue() == id)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminCanCreateListUpdateAndDeleteANotificationWithAuditTrail() {
        long suffix = System.nanoTime();
        String username = "notif-admin-" + suffix;
        String token = signupAndGetToken(username);
        Long actorAccountId = accountIdOf(username);

        Structure structure = structureRepository.save(newStructure("Structure Notif " + suffix));
        Equipment equipment = equipmentRepository.save(newEquipment("Equipement Notif " + suffix));
        Intrant intrant = intrantRepository.save(newIntrant("Intrant Notif " + suffix));

        HttpHeaders headers = authHeaders(token);

        // ---- CREATE ----
        Map<String, Object> createInput = Map.of(
                "emitterId", structure.getId(),
                "equipmentId", equipment.getId(),
                "intrantId", intrant.getId(),
                "quantity", 42,
                "isResolved", false,
                "isRejected", false
        );
        ResponseEntity<Object> createResponse = restTemplate.postForEntity(
                "/api/super-admin/notifications", new HttpEntity<>(createInput, headers), Object.class);
        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();

        // ---- LIST : Structure émettrice / Équipement / Intrant / statut visibles ----
        ResponseEntity<List> listResponse = restTemplate.exchange(
                "/api/super-admin/notifications", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(listResponse.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> notifications = listResponse.getBody();

        Map<String, Object> created = notifications.stream()
                .filter(n -> ((Number) n.get("emitterId")).longValue() == structure.getId())
                .filter(n -> ((Number) n.get("equipmentId")).longValue() == equipment.getId())
                .filter(n -> ((Number) n.get("intrantId")).longValue() == intrant.getId())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Notification créée introuvable dans la liste"));

        assertThat(created.get("emitterName")).isEqualTo(structure.getName());
        assertThat(created.get("equipmentName")).isEqualTo(equipment.getName());
        assertThat(created.get("intrantName")).isEqualTo(intrant.getName());
        assertThat(created.get("quantity")).isEqualTo(42);
        assertThat(created.get("isResolved")).isEqualTo(false);
        assertThat(created.get("isRejected")).isEqualTo(false);

        long notificationId = ((Number) created.get("id")).longValue();

        // ---- Audit : CREATE ----
        List<AuditLog> createLogs = auditLogRepository.findAll().stream()
                .filter(a -> "SapNotification".equals(a.getEntityType()) && a.getAction() == AuditAction.CREATE)
                .filter(a -> a.getEntityId() != null && a.getEntityId() == notificationId)
                .toList();
        assertThat(createLogs).isNotEmpty();
        assertThat(createLogs.get(createLogs.size() - 1).getAccountId()).isEqualTo(actorAccountId);
        assertThat(createLogs.get(createLogs.size() - 1).getSnapshot()).contains("\"before\":null");

        // ---- UPDATE ----
        Map<String, Object> updateInput = Map.of(
                "emitterId", structure.getId(),
                "equipmentId", equipment.getId(),
                "intrantId", intrant.getId(),
                "quantity", 99,
                "isResolved", true,
                "isRejected", false
        );
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/notifications/" + notificationId, HttpMethod.PUT,
                new HttpEntity<>(updateInput, headers), Object.class);
        assertThat(updateResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<List> afterUpdateResponse = restTemplate.exchange(
                "/api/super-admin/notifications", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        Map<String, Object> updated = findById(afterUpdateResponse.getBody(), notificationId);
        assertThat(updated.get("quantity")).isEqualTo(99);
        assertThat(updated.get("isResolved")).isEqualTo(true);

        // ---- Audit : UPDATE avec état avant/après ----
        List<AuditLog> updateLogs = auditLogRepository.findAll().stream()
                .filter(a -> "SapNotification".equals(a.getEntityType()) && a.getAction() == AuditAction.UPDATE)
                .filter(a -> a.getEntityId() != null && a.getEntityId() == notificationId)
                .toList();
        assertThat(updateLogs).hasSize(1);
        AuditLog updateLog = updateLogs.get(0);
        assertThat(updateLog.getAccountId()).isEqualTo(actorAccountId);
        assertThat(updateLog.getSnapshot()).contains("\"quantity\":42").contains("\"quantity\":99");

        // ---- DELETE ----
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/notifications/" + notificationId, HttpMethod.DELETE,
                new HttpEntity<>(headers), Object.class);
        assertThat(deleteResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(sapNotificationRepository.existsById(notificationId)).isFalse();

        ResponseEntity<List> afterDeleteResponse = restTemplate.exchange(
                "/api/super-admin/notifications", HttpMethod.GET, new HttpEntity<>(headers), List.class);
        assertThat(afterDeleteResponse.getBody().stream()
                .anyMatch(n -> ((Number) ((Map<String, Object>) n).get("id")).longValue() == notificationId))
                .isFalse();

        // ---- Audit : DELETE avec état avant suppression ----
        List<AuditLog> deleteLogs = auditLogRepository.findAll().stream()
                .filter(a -> "SapNotification".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> a.getEntityId() != null && a.getEntityId() == notificationId)
                .toList();
        assertThat(deleteLogs).hasSize(1);
        AuditLog deleteLog = deleteLogs.get(0);
        assertThat(deleteLog.getAccountId()).isEqualTo(actorAccountId);
        assertThat(deleteLog.getSnapshot()).contains("\"quantity\":99");
    }

    @Test
    void nonSuperAdminAccountReceivesForbiddenOnNotificationsEndpoint() {
        long suffix = System.nanoTime();
        String username = "notif-non-admin-" + suffix;
        SignupRequest signup = new SignupRequest(
                username, "Test User", "0000000000", "Password1!",
                regularRoleId.intValue(), List.of(), List.of()
        );
        ResponseEntity<Object> signupResponse = restTemplate.postForEntity("/api/auth/signup", signup, Object.class);
        assertThat(signupResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, "Password1!"), LoginResponse.class);
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaders(loginResponse.getBody().access_token())), Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
