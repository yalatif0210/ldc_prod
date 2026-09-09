package com.markov.lab.controller;

import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.District;
import com.markov.lab.entity.Region;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.Structure;
import com.markov.lab.entity.User;
import com.markov.lab.repository.DistrictRepository;
import com.markov.lab.repository.RegionRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.StructureRepository;
import com.markov.lab.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seam d'intégration convenu pour le Ticket #2 : POST /api/auth/signup.
 * Vérifie que les Structures assignées au compte créé correspondent à la règle par rôle
 * (voir ldc_backend/CONTEXT.md et docs/adr/0001-admin-role-scoped-structure-access.md) :
 * SUPER_ADMIN reçoit toutes les Structures, les autres rôles reçoivent exactement
 * celles envoyées (plus de troncature au premier élément).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationControllerSignupTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    private static Long superAdminRoleId;
    private static Long adminRoleId;
    private static Long supervisorRoleId;
    private static Long labUserRoleId;
    private static Long pharmUserRoleId;

    private static Long structure1Id;
    private static Long structure2Id;
    private static Long structure3Id;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository,
                                   @Autowired RegionRepository regionRepository,
                                   @Autowired DistrictRepository districtRepository,
                                   @Autowired StructureRepository structureRepository) {
        // L'ordre d'insertion fixe les ids générés (1,2,3,4,5) sur une base fraîche,
        // exactement comme data.sql le fait en production.
        superAdminRoleId = roleRepository.save(newRole("SUPER_ADMIN")).getId();
        adminRoleId = roleRepository.save(newRole("ADMIN")).getId();
        supervisorRoleId = roleRepository.save(newRole("SUPERVISOR")).getId();
        labUserRoleId = roleRepository.save(newRole("LABORATORY_USER")).getId();
        pharmUserRoleId = roleRepository.save(newRole("PHARMACY_USER")).getId();

        Region region = new Region();
        region.setName("TEST_REGION");
        region = regionRepository.save(region);

        District district = new District();
        district.setName("TEST_DISTRICT");
        district.setRegion(region);
        district = districtRepository.save(district);

        structure1Id = structureRepository.save(newStructure("TEST_STRUCTURE_1", district)).getId();
        structure2Id = structureRepository.save(newStructure("TEST_STRUCTURE_2", district)).getId();
        structure3Id = structureRepository.save(newStructure("TEST_STRUCTURE_3", district)).getId();
    }

    private static Role newRole(String name) {
        Role role = new Role();
        role.setRole(name);
        return role;
    }

    private static Structure newStructure(String name, District district) {
        Structure structure = new Structure();
        structure.setName(name);
        structure.setActive(true);
        structure.setCode(1);
        structure.setDistrict(district);
        return structure;
    }

    @Test
    @Transactional
    void superAdminReceivesAllStructuresRegardlessOfSelection() {
        signup("test-super-admin", superAdminRoleId, List.of(structure1Id));

        List<Long> actual = persistedStructureIds("test-super-admin");
        assertThat(actual).containsExactlyInAnyOrder(structure1Id, structure2Id, structure3Id);
    }

    @Test
    @Transactional
    void adminReceivesExactlyTheSelectedStructures() {
        signup("test-admin", adminRoleId, List.of(structure1Id, structure2Id));

        List<Long> actual = persistedStructureIds("test-admin");
        assertThat(actual).containsExactlyInAnyOrder(structure1Id, structure2Id);
    }

    @Test
    @Transactional
    void supervisorReceivesExactlyTheSelectedStructures() {
        signup("test-supervisor", supervisorRoleId, List.of(structure1Id, structure2Id, structure3Id));

        List<Long> actual = persistedStructureIds("test-supervisor");
        assertThat(actual).containsExactlyInAnyOrder(structure1Id, structure2Id, structure3Id);
    }

    @Test
    @Transactional
    void laboratoryUserReceivesEverySelectedStructureNotJustTheFirst() {
        signup("test-lab-user", labUserRoleId, List.of(structure1Id, structure2Id));

        List<Long> actual = persistedStructureIds("test-lab-user");
        assertThat(actual).containsExactlyInAnyOrder(structure1Id, structure2Id);
    }

    @Test
    @Transactional
    void pharmacyUserReceivesEverySelectedStructureNotJustTheFirst() {
        signup("test-pharm-user", pharmUserRoleId, List.of(structure2Id, structure3Id));

        List<Long> actual = persistedStructureIds("test-pharm-user");
        assertThat(actual).containsExactlyInAnyOrder(structure2Id, structure3Id);
    }

    private void signup(String username, Long roleId, List<Long> platforms) {
        SignupRequest request = new SignupRequest(
                username, "Test User", "0000000000", "Password1!",
                roleId.intValue(), List.of(), platforms
        );
        ResponseEntity<Object> response = restTemplate.postForEntity("/api/auth/signup", request, Object.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private List<Long> persistedStructureIds(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return user.getAccount().getStructures().stream().map(Structure::getId).toList();
    }
}
