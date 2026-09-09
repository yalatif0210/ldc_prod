package com.markov.lab.controller;

import com.markov.lab.controller.dto.ChangePasswordRequest;
import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.District;
import com.markov.lab.entity.Region;
import com.markov.lab.entity.Role;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam d'intégration convenu pour le Ticket #4 : POST /api/auth/change-password.
 * Voir docs/adr — issue #4 (changement de mot de passe self-service).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationControllerChangePasswordTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    private static Long labUserRoleId;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository,
                                   @Autowired RegionRepository regionRepository,
                                   @Autowired DistrictRepository districtRepository) {
        roleRepository.save(newRole("SUPER_ADMIN"));
        roleRepository.save(newRole("ADMIN"));
        roleRepository.save(newRole("SUPERVISOR"));
        labUserRoleId = roleRepository.save(newRole("LABORATORY_USER")).getId();
        roleRepository.save(newRole("PHARMACY_USER"));

        Region region = new Region();
        region.setName("TEST_REGION");
        regionRepository.save(region);

        District district = new District();
        district.setName("TEST_DISTRICT");
        district.setRegion(region);
        districtRepository.save(district);
    }

    private static Role newRole(String name) {
        Role role = new Role();
        role.setRole(name);
        return role;
    }

    private String signupAndGetToken(String username, String password) {
        SignupRequest signup = new SignupRequest(
                username, "Test User", "0000000000", password,
                labUserRoleId.intValue(), List.of(), List.of()
        );
        ResponseEntity<Object> signupResponse =
                restTemplate.postForEntity("/api/auth/signup", signup, Object.class);
        assertThat(signupResponse.getStatusCode().is2xxSuccessful()).isTrue();
        return login(username, password);
    }

    private String login(String username, String password) {
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, password), LoginResponse.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody().access_token();
    }

    private ResponseEntity<Object> changePassword(String token, String currentPassword, String newPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<ChangePasswordRequest> entity =
                new HttpEntity<>(new ChangePasswordRequest(currentPassword, newPassword), headers);
        return restTemplate.postForEntity("/api/auth/change-password", entity, Object.class);
    }

    @Test
    void correctCurrentPasswordAndValidNewPasswordSucceedsAndNewPasswordThenWorks() {
        String token = signupAndGetToken("test-cp-success", "OldPassword1!");

        ResponseEntity<Object> response = changePassword(token, "OldPassword1!", "NewPassword2!");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        // Le nouveau mot de passe permet bien de se reconnecter.
        assertThat(login("test-cp-success", "NewPassword2!")).isNotBlank();
    }

    @Test
    void incorrectCurrentPasswordIsRejectedAndPasswordStaysUnchanged() {
        String token = signupAndGetToken("test-cp-wrong-current", "OldPassword1!");

        ResponseEntity<Object> response = changePassword(token, "WrongPassword1!", "NewPassword2!");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // L'ancien mot de passe fonctionne toujours.
        assertThat(login("test-cp-wrong-current", "OldPassword1!")).isNotBlank();
    }

    @Test
    void newPasswordFailingComplexityPolicyIsRejected() {
        String token = signupAndGetToken("test-cp-weak-new", "OldPassword1!");

        ResponseEntity<Object> response = changePassword(token, "OldPassword1!", "weak");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // L'ancien mot de passe fonctionne toujours.
        assertThat(login("test-cp-weak-new", "OldPassword1!")).isNotBlank();
    }
}
