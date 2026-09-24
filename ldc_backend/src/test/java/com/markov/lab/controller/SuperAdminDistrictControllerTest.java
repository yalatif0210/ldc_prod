package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.District;
import com.markov.lab.entity.Region;
import com.markov.lab.entity.Structure;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.DistrictRepository;
import com.markov.lab.repository.RegionRepository;
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
 * Ticket #10 : CRUD Super Admin sur le référentiel géographique {@code District}, avec blocage de
 * la suppression tant que des {@code Structure} en dépendent.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminDistrictControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private DistrictRepository districtRepository;

    @Autowired
    private StructureRepository structureRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static Long superAdminRoleId;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository) {
        com.markov.lab.entity.Role role = new com.markov.lab.entity.Role();
        role.setRole("SUPER_ADMIN");
        superAdminRoleId = roleRepository.save(role).getId();
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

    private Region newRegion(String name) {
        Region region = new Region();
        region.setName(name);
        return regionRepository.save(region);
    }

    private District newDistrict(String name, Region region) {
        District district = new District();
        district.setName(name);
        district.setRegion(region);
        return districtRepository.save(district);
    }

    @Test
    void createListAndUpdateDistrictSucceed() {
        String token = signupAndGetToken("district-crud-" + System.nanoTime());
        Region region = newRegion("Region For District CRUD " + System.nanoTime());
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));

        Map<String, Object> createBody = Map.of("name", "District CRUD " + System.nanoTime(), "regionId", region.getId());
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<Object> createResponse =
                restTemplate.postForEntity("/api/super-admin/districts", createEntity, Object.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<District[]> listResponse =
                restTemplate.exchange("/api/super-admin/districts", HttpMethod.GET, readEntity, District[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        District created = List.of(listResponse.getBody()).stream()
                .filter(d -> d.getName().equals(createBody.get("name")))
                .findFirst().orElseThrow();

        Map<String, Object> updateBody = Map.of("name", "District Renamed " + System.nanoTime(), "regionId", region.getId());
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/districts/" + created.getId(), HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        District updated = districtRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo(updateBody.get("name"));
    }

    @Test
    void deletingADistrictWithDependentStructuresIsRefused() {
        String token = signupAndGetToken("district-blocked-" + System.nanoTime());
        Region region = newRegion("Region For Blocked District " + System.nanoTime());
        District district = newDistrict("District With Structure " + System.nanoTime(), region);

        Structure structure = new Structure();
        structure.setName("Structure Dependent " + System.nanoTime());
        structure.setActive(true);
        structure.setDistrict(district);
        structureRepository.save(structure);

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/super-admin/districts/" + district.getId(), HttpMethod.DELETE, deleteEntity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("description").toString())
                .contains("1")
                .containsIgnoringCase("structure");
        assertThat(districtRepository.existsById(district.getId())).isTrue();
    }

    @Test
    void deletingADistrictWithoutDependentsSucceedsAndProducesAnAuditLogEntry() {
        String token = signupAndGetToken("district-delete-ok-" + System.nanoTime());
        Region region = newRegion("Region For Deletable District " + System.nanoTime());
        District district = newDistrict("District No Dependents " + System.nanoTime(), region);

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/districts/" + district.getId(), HttpMethod.DELETE, deleteEntity, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(districtRepository.existsById(district.getId())).isFalse();

        List<AuditLog> deletions = auditLogRepository.findAll().stream()
                .filter(a -> "District".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> district.getId().equals(a.getEntityId()))
                .toList();
        assertThat(deletions).hasSize(1);
    }
}
