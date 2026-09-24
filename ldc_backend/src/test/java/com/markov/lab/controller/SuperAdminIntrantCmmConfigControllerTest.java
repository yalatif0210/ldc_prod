package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.IntrantCmmConfig;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.Structure;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantCmmConfigRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.StructureRepository;
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
 * Ticket #15 : CRUD Super Admin sur l'entité feuille {@code IntrantCmmConfig}. Couverture détaillée
 * (création/modification/suppression + journal d'audit) pour cette entité représentative ; les deux
 * autres entités feuilles du ticket ({@code Information}, {@code Synthesis}) ont une couverture plus
 * légère dans leurs propres classes de test, suivant la décision de test du Ticket #5 (une entité
 * détaillée par lot plutôt que les 29 en profondeur).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminIntrantCmmConfigControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private IntrantRepository intrantRepository;

    @Autowired
    private StructureRepository structureRepository;

    @Autowired
    private IntrantCmmConfigRepository intrantCmmConfigRepository;

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

    private Equipment newEquipment(String name) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        return equipmentRepository.save(equipment);
    }

    private Intrant newIntrant(String name) {
        Intrant intrant = new Intrant();
        intrant.setName(name);
        intrant.setCode((int) System.nanoTime());
        intrant.setSku("SKU-" + System.nanoTime());
        return intrantRepository.save(intrant);
    }

    private Structure newStructure(String name) {
        Structure structure = new Structure();
        structure.setName(name);
        structure.setActive(true);
        return structureRepository.save(structure);
    }

    @Test
    void fullCrudLifecycleProducesCorrectAuditLogEntries() {
        long suffix = System.nanoTime();
        String token = signupAndGetToken("cmm-admin-" + suffix);

        Equipment equipment = newEquipment("Equip-" + suffix);
        Intrant intrant = newIntrant("Intrant-" + suffix);
        Structure structure = newStructure("Structure-" + suffix);

        // ---- CREATE ----
        Map<String, Object> createBody = Map.of(
                "structureId", structure.getId(),
                "intrantId", intrant.getId(),
                "equipmentId", equipment.getId(),
                "cmm", 42
        );
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<Object> createResponse = restTemplate.postForEntity(
                "/api/super-admin/intrant-cmm-configs", createEntity, Object.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<IntrantCmmConfig> created = intrantCmmConfigRepository.findAll().stream()
                .filter(c -> c.getStructure() != null && c.getStructure().getId() == structure.getId())
                .toList();
        assertThat(created).hasSize(1);
        Long configId = created.get(0).getId();
        assertThat(created.get(0).getCmm()).isEqualTo(42);

        List<AuditLog> creationLogs = auditLogRepository.findAll().stream()
                .filter(a -> "IntrantCmmConfig".equals(a.getEntityType()) && a.getAction() == AuditAction.CREATE)
                .filter(a -> configId.equals(a.getEntityId()))
                .toList();
        assertThat(creationLogs).hasSize(1);
        assertThat(creationLogs.get(0).getSnapshot()).contains("\"cmm\":42").contains("\"before\":null");

        // ---- LIST ----
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map[]> listResponse = restTemplate.exchange(
                "/api/super-admin/intrant-cmm-configs", HttpMethod.GET, readEntity, Map[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(List.of(listResponse.getBody())).anySatisfy(row ->
                assertThat(((Number) row.get("id")).longValue()).isEqualTo(configId));

        // ---- UPDATE ----
        Map<String, Object> updateBody = Map.of(
                "structureId", structure.getId(),
                "intrantId", intrant.getId(),
                "equipmentId", equipment.getId(),
                "cmm", 99
        );
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/intrant-cmm-configs/" + configId, HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        IntrantCmmConfig updated = intrantCmmConfigRepository.findById(configId).orElseThrow();
        assertThat(updated.getCmm()).isEqualTo(99);

        List<AuditLog> updateLogs = auditLogRepository.findAll().stream()
                .filter(a -> "IntrantCmmConfig".equals(a.getEntityType()) && a.getAction() == AuditAction.UPDATE)
                .filter(a -> configId.equals(a.getEntityId()))
                .toList();
        assertThat(updateLogs).hasSize(1);
        assertThat(updateLogs.get(0).getSnapshot()).contains("\"cmm\":42").contains("\"cmm\":99");

        // ---- DELETE (directe, sans blocage : entité feuille) ----
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/intrant-cmm-configs/" + configId, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(intrantCmmConfigRepository.existsById(configId)).isFalse();

        List<AuditLog> deleteLogs = auditLogRepository.findAll().stream()
                .filter(a -> "IntrantCmmConfig".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> configId.equals(a.getEntityId()))
                .toList();
        assertThat(deleteLogs).hasSize(1);
        assertThat(deleteLogs.get(0).getSnapshot()).contains("\"cmm\":99").contains("\"after\":null");
    }

    @Test
    void updateAndDeleteOfUnknownIdReturnNotFound() {
        String token = signupAndGetToken("cmm-admin-404-" + System.nanoTime());
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));

        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/intrant-cmm-configs/999999999", HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> updateBody = Map.of(
                "structureId", 1, "intrantId", 1, "equipmentId", 1, "cmm", 1);
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/intrant-cmm-configs/999999999", HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
