package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ticket #6 : /api/super-admin/** doit être réservé aux comptes SUPER_ADMIN.
 * Avant le correctif, ni SuperAdminController (@PreAuthorize commenté) ni
 * SecurityConfig (règle "/api/super-admin/**".hasRole(...) commentée) ne
 * protègent ces endpoints : ils retombent sur ".requestMatchers("/api/**").authenticated()",
 * donc n'importe quel compte authentifié y accède (403 attendu, 200 reçu).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminControllerAuthorizationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    private static Long superAdminRoleId;
    private static Long adminRoleId;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository) {
        // L'ordre d'insertion fixe les ids générés (1,2) sur une base fraîche,
        // exactement comme data.sql le fait en production. Le rôle id=1 doit
        // être SUPER_ADMIN : UserService.createUser traite spécialement role==1.
        superAdminRoleId = roleRepository.save(newRole("SUPER_ADMIN")).getId();
        adminRoleId = roleRepository.save(newRole("ADMIN")).getId();
    }

    private static com.markov.lab.entity.Role newRole(String name) {
        com.markov.lab.entity.Role role = new com.markov.lab.entity.Role();
        role.setRole(name);
        return role;
    }

    @Test
    void nonSuperAdminAccountReceivesForbiddenOnSuperAdminEndpoint() {
        signup("test-authz-admin", adminRoleId);
        String accessToken = login("test-authz-admin");

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/stats", HttpMethod.GET, authenticatedRequest(accessToken), Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void superAdminAccountCanAccessSuperAdminEndpoint() {
        signup("test-authz-super-admin", superAdminRoleId);
        String accessToken = login("test-authz-super-admin");

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/stats", HttpMethod.GET, authenticatedRequest(accessToken), Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void signup(String username, Long roleId) {
        SignupRequest request = new SignupRequest(
                username, "Test User", "0000000000", "Password1!",
                roleId.intValue(), List.of(), List.of()
        );
        ResponseEntity<Object> response = restTemplate.postForEntity("/api/auth/signup", request, Object.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private String login(String username) {
        LoginRequest request = new LoginRequest(username, "Password1!");
        ResponseEntity<LoginResponse> response =
                restTemplate.postForEntity("/api/auth/login", request, LoginResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().access_token();
    }

    private HttpEntity<Void> authenticatedRequest(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
