package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Information;
import com.markov.lab.entity.InformationSubSubUnit;
import com.markov.lab.entity.InformationSubUnit;
import com.markov.lab.entity.InformationUnit;
import com.markov.lab.entity.Role;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.InformationRepository;
import com.markov.lab.repository.InformationSubSubUnitRepository;
import com.markov.lab.repository.InformationSubUnitRepository;
import com.markov.lab.repository.InformationUnitRepository;
import com.markov.lab.repository.RoleRepository;
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
 * Ticket #15 : CRUD Super Admin sur l'entité feuille {@code Information} — couverture légère
 * (création, modification, suppression avec vérification de l'entrée d'audit correspondante),
 * complémentaire de la couverture détaillée sur {@code IntrantCmmConfig}
 * ({@link SuperAdminIntrantCmmConfigControllerTest}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminInformationControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InformationUnitRepository informationUnitRepository;

    @Autowired
    private InformationSubUnitRepository informationSubUnitRepository;

    @Autowired
    private InformationSubSubUnitRepository informationSubSubUnitRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private InformationRepository informationRepository;

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
    void createUpdateDeleteInformationProducesAuditLogEntries() {
        long suffix = System.nanoTime();
        String token = signupAndGetToken("info-admin-" + suffix);

        InformationUnit unit = informationUnitRepository.save(newInformationUnit("Unit-" + suffix));
        InformationSubUnit subUnit = informationSubUnitRepository.save(newInformationSubUnit("SubUnit-" + suffix));
        InformationSubSubUnit subSubUnit = informationSubSubUnitRepository.save(newInformationSubSubUnit("SubSubUnit-" + suffix));
        Equipment equipment = equipmentRepository.save(newEquipment("Equip-" + suffix));

        // ---- CREATE ----
        Map<String, Object> createBody = Map.of(
                "informationUnitId", unit.getId(),
                "informationSubUnitId", subUnit.getId(),
                "informationSubSubUnitId", subSubUnit.getId(),
                "equipmentId", equipment.getId(),
                "isActive", true
        );
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<Object> createResponse = restTemplate.postForEntity(
                "/api/super-admin/informations", createEntity, Object.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Information> created = informationRepository.findAll().stream()
                .filter(i -> i.getEquipment() != null && i.getEquipment().getId() == equipment.getId())
                .toList();
        assertThat(created).hasSize(1);
        Long informationId = created.get(0).getId();

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "Information".equals(a.getEntityType())
                        && a.getAction() == AuditAction.CREATE
                        && informationId.equals(a.getEntityId()))).isTrue();

        // ---- UPDATE (désactivation) ----
        Map<String, Object> updateBody = Map.of(
                "informationUnitId", unit.getId(),
                "informationSubUnitId", subUnit.getId(),
                "informationSubSubUnitId", subSubUnit.getId(),
                "equipmentId", equipment.getId(),
                "isActive", false
        );
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/informations/" + informationId, HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(informationRepository.findById(informationId).orElseThrow().getIsActive()).isFalse();

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "Information".equals(a.getEntityType())
                        && a.getAction() == AuditAction.UPDATE
                        && informationId.equals(a.getEntityId()))).isTrue();

        // ---- DELETE (directe, sans blocage : entité feuille) ----
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> deleteResponse = restTemplate.exchange(
                "/api/super-admin/informations/" + informationId, HttpMethod.DELETE, readEntity, Object.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(informationRepository.existsById(informationId)).isFalse();

        assertThat(auditLogRepository.findAll().stream()
                .anyMatch(a -> "Information".equals(a.getEntityType())
                        && a.getAction() == AuditAction.DELETE
                        && informationId.equals(a.getEntityId()))).isTrue();
    }

    private InformationUnit newInformationUnit(String name) {
        InformationUnit unit = new InformationUnit();
        unit.setName(name);
        return unit;
    }

    private InformationSubUnit newInformationSubUnit(String name) {
        InformationSubUnit subUnit = new InformationSubUnit();
        subUnit.setName(name);
        return subUnit;
    }

    private InformationSubSubUnit newInformationSubSubUnit(String name) {
        InformationSubSubUnit subSubUnit = new InformationSubSubUnit();
        subSubUnit.setName(name);
        return subSubUnit;
    }

    private Equipment newEquipment(String name) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        return equipment;
    }
}
