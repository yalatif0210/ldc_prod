package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.AuditAction;
import com.markov.lab.entity.AuditLog;
import com.markov.lab.entity.District;
import com.markov.lab.entity.Region;
import com.markov.lab.repository.AuditLogRepository;
import com.markov.lab.repository.DistrictRepository;
import com.markov.lab.repository.RegionRepository;
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
 * Ticket #10 : CRUD Super Admin sur le référentiel géographique {@code Region}, avec blocage de
 * la suppression tant que des {@code District} en dépendent.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminRegionControllerTest {

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

    @Test
    void createListAndUpdateRegionSucceed() {
        String token = signupAndGetToken("region-crud-" + System.nanoTime());
        HttpEntity<Void> readEntity = new HttpEntity<>(authHeaders(token));

        Map<String, Object> createBody = Map.of("name", "Region CRUD " + System.nanoTime());
        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createBody, authHeaders(token));
        ResponseEntity<Object> createResponse =
                restTemplate.postForEntity("/api/super-admin/regions", createEntity, Object.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Region[]> listResponse =
                restTemplate.exchange("/api/super-admin/regions", HttpMethod.GET, readEntity, Region[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Region created = List.of(listResponse.getBody()).stream()
                .filter(r -> r.getName().equals(createBody.get("name")))
                .findFirst().orElseThrow();

        Map<String, Object> updateBody = Map.of("name", "Region Renamed " + System.nanoTime());
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(updateBody, authHeaders(token));
        ResponseEntity<Object> updateResponse = restTemplate.exchange(
                "/api/super-admin/regions/" + created.getId(), HttpMethod.PUT, updateEntity, Object.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Region updated = regionRepository.findById(created.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo(updateBody.get("name"));
    }

    @Test
    void deletingARegionWithDependentDistrictsIsRefused() {
        String token = signupAndGetToken("region-blocked-" + System.nanoTime());
        Region region = newRegion("Region With District " + System.nanoTime());
        District district = new District();
        district.setName("District Dependent " + System.nanoTime());
        district.setRegion(region);
        districtRepository.save(district);

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/super-admin/regions/" + region.getId(), HttpMethod.DELETE, deleteEntity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("description").toString())
                .contains("1")
                .containsIgnoringCase("district");
        assertThat(regionRepository.existsById(region.getId())).isTrue();
    }

    @Test
    void deletingARegionWithoutDependentsSucceedsAndProducesAnAuditLogEntry() {
        String username = "region-delete-ok-" + System.nanoTime();
        String token = signupAndGetToken(username);
        Region region = newRegion("Region No Dependents " + System.nanoTime());

        HttpEntity<Void> deleteEntity = new HttpEntity<>(authHeaders(token));
        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/regions/" + region.getId(), HttpMethod.DELETE, deleteEntity, Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(regionRepository.existsById(region.getId())).isFalse();

        List<AuditLog> deletions = auditLogRepository.findAll().stream()
                .filter(a -> "Region".equals(a.getEntityType()) && a.getAction() == AuditAction.DELETE)
                .filter(a -> region.getId().equals(a.getEntityId()))
                .toList();
        assertThat(deletions).hasSize(1);
    }
}
