package com.markov.lab.controller;

import com.markov.lab.controller.dto.LoginRequest;
import com.markov.lab.controller.dto.LoginResponse;
import com.markov.lab.controller.dto.SignupRequest;
import com.markov.lab.entity.Equipment;
import com.markov.lab.entity.Intrant;
import com.markov.lab.entity.IntrantType;
import com.markov.lab.entity.Role;
import com.markov.lab.entity.SanguineProduct;
import com.markov.lab.entity.SanguineProductTransaction;
import com.markov.lab.entity.SapNotification;
import com.markov.lab.entity.Structure;
import com.markov.lab.repository.EquipmentRepository;
import com.markov.lab.repository.IntrantRepository;
import com.markov.lab.repository.IntrantTypeRepository;
import com.markov.lab.repository.RoleRepository;
import com.markov.lab.repository.SanguineProductRepository;
import com.markov.lab.repository.SanguineProductTransactionRepository;
import com.markov.lab.repository.SapNotificationRepository;
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
 * Ticket #13 : CRUD Super Admin sur les entités structurelles {@code Equipment}, {@code Intrant},
 * {@code SanguineProduct}, avec blocage de suppression tant que des enregistrements en dépendent.
 *
 * <p>Couverture détaillée sur {@code Equipment} (create/update/delete + les deux branches du
 * blocage de suppression), couverture plus légère sur {@code Intrant} et {@code SanguineProduct}
 * (une branche bloquée + une branche réussie chacune) puisque le motif de blocage est strictement
 * identique — voir Testing Decisions du Ticket #5.</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperAdminStructuralEntitiesControllerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private StructureRepository structureRepository;

    @Autowired
    private IntrantRepository intrantRepository;

    @Autowired
    private IntrantTypeRepository intrantTypeRepository;

    @Autowired
    private SapNotificationRepository sapNotificationRepository;

    @Autowired
    private SanguineProductRepository sanguineProductRepository;

    @Autowired
    private SanguineProductTransactionRepository sanguineProductTransactionRepository;

    private static Long superAdminRoleId;
    private static String sharedToken;

    @BeforeAll
    static void seedReferenceData(@Autowired RoleRepository roleRepository, @Autowired TestRestTemplate restTemplate) {
        Role role = new Role();
        role.setRole("SUPER_ADMIN");
        superAdminRoleId = roleRepository.save(role).getId();

        String username = "structural-entities-actor-" + System.nanoTime();
        SignupRequest signup = new SignupRequest(
                username, "Test User", "0000000000", "Password1!",
                superAdminRoleId.intValue(), List.of(), List.of());
        ResponseEntity<Object> signupResponse = restTemplate.postForEntity("/api/auth/signup", signup, Object.class);
        assertThat(signupResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, "Password1!"), LoginResponse.class);
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        sharedToken = loginResponse.getBody().access_token();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(sharedToken);
        return headers;
    }

    private <T> HttpEntity<T> authenticated(T body) {
        return new HttpEntity<>(body, authHeaders());
    }

    private HttpEntity<Void> authenticated() {
        return new HttpEntity<>(authHeaders());
    }

    // ---------------------------------------------------------------------------------------
    // Equipment — couverture détaillée
    // ---------------------------------------------------------------------------------------

    @Test
    void creatingAnEquipmentMakesItAppearInTheList() {
        String name = "Automate Chimie " + System.nanoTime();

        ResponseEntity<Object> createResponse = restTemplate.exchange(
                "/api/super-admin/equipments", HttpMethod.POST,
                authenticated(Map.of("name", name)), Object.class);
        assertThat(createResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<Equipment[]> listResponse = restTemplate.exchange(
                "/api/super-admin/equipments", HttpMethod.GET, authenticated(), Equipment[].class);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(List.of(listResponse.getBody())).anyMatch(e -> name.equals(e.getName()));
    }

    @Test
    void updatingAnEquipmentChangesItsName() {
        Equipment equipment = equipmentRepository.save(newEquipment("Avant " + System.nanoTime()));
        String newName = "Apres " + System.nanoTime();

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/equipments/" + equipment.getId(), HttpMethod.PUT,
                authenticated(Map.of("name", newName)), Object.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        Equipment reloaded = equipmentRepository.findById(equipment.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(newName);
    }

    @Test
    void deletingAnEquipmentWithoutDependentsSucceeds() {
        Equipment equipment = equipmentRepository.save(newEquipment("Sans dependants " + System.nanoTime()));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/equipments/" + equipment.getId(), HttpMethod.DELETE, authenticated(), Object.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(equipmentRepository.existsById(equipment.getId())).isFalse();
    }

    @Test
    void deletingAnEquipmentReferencedByAStructureIsRefusedWithAClearMessage() {
        Equipment equipment = equipmentRepository.save(newEquipment("Avec structure " + System.nanoTime()));
        Structure structure = new Structure();
        structure.setName("Structure de test " + System.nanoTime());
        structure.addEquipment(equipment);
        structureRepository.save(structure);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/super-admin/equipments/" + equipment.getId(), HttpMethod.DELETE, authenticated(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("description"))
                .contains("1")
                .containsIgnoringCase("structure");
        assertThat(equipmentRepository.existsById(equipment.getId())).isTrue();
    }

    private Equipment newEquipment(String name) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        return equipment;
    }

    // ---------------------------------------------------------------------------------------
    // Intrant — couverture plus légère (même motif de blocage que Equipment)
    // ---------------------------------------------------------------------------------------

    @Test
    void deletingAnIntrantWithoutDependentsSucceeds() {
        Intrant intrant = intrantRepository.save(newIntrant("Intrant libre " + System.nanoTime()));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/intrants/" + intrant.getId(), HttpMethod.DELETE, authenticated(), Object.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(intrantRepository.existsById(intrant.getId())).isFalse();
    }

    @Test
    void deletingAnIntrantReferencedByASapNotificationIsRefusedWithAClearMessage() {
        Intrant intrant = intrantRepository.save(newIntrant("Intrant lie " + System.nanoTime()));
        SapNotification notification = new SapNotification();
        notification.setIntrant(intrant);
        notification.setQuantity(5);
        sapNotificationRepository.save(notification);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/super-admin/intrants/" + intrant.getId(), HttpMethod.DELETE, authenticated(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("description"))
                .contains("1")
                .containsIgnoringCase("notification");
        assertThat(intrantRepository.existsById(intrant.getId())).isTrue();
    }

    private Intrant newIntrant(String name) {
        // Intrant.intrantType et Intrant.equipment sont en cascade = CascadeType.ALL (voir
        // l'entité) : on passe volontairement des entités transitoires (non sauvegardées au
        // préalable) pour que la cascade les persiste avec l'Intrant, plutôt que de les
        // pré-sauvegarder ici (ce qui les rendrait détachées et ferait échouer la cascade avec
        // "detached entity passed to persist").
        IntrantType intrantType = newIntrantType("Type " + System.nanoTime());
        Equipment equipment = newEquipment("Equip pour intrant " + System.nanoTime());
        Intrant intrant = new Intrant();
        intrant.setName(name);
        intrant.setCode((int) (System.nanoTime() % 100000));
        intrant.setSku("SKU-" + System.nanoTime());
        intrant.setIntrantType(intrantType);
        intrant.setEquipment(equipment);
        return intrant;
    }

    private IntrantType newIntrantType(String name) {
        IntrantType type = new IntrantType();
        type.setName(name);
        return type;
    }

    // ---------------------------------------------------------------------------------------
    // SanguineProduct — couverture plus légère (même motif de blocage que Equipment)
    // ---------------------------------------------------------------------------------------

    @Test
    void deletingASanguineProductWithoutDependentsSucceeds() {
        SanguineProduct sanguineProduct = sanguineProductRepository.save(newSanguineProduct("CS libre " + System.nanoTime()));

        ResponseEntity<Object> response = restTemplate.exchange(
                "/api/super-admin/sanguine-products/" + sanguineProduct.getId(), HttpMethod.DELETE, authenticated(), Object.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(sanguineProductRepository.existsById(sanguineProduct.getId())).isFalse();
    }

    @Test
    void deletingASanguineProductReferencedByATransactionIsRefusedWithAClearMessage() {
        SanguineProduct sanguineProduct = sanguineProductRepository.save(newSanguineProduct("CS lie " + System.nanoTime()));
        SanguineProductTransaction transaction = new SanguineProductTransaction();
        transaction.setSanguineProduct(sanguineProduct);
        transaction.setQuantity(3);
        sanguineProductTransactionRepository.save(transaction);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/super-admin/sanguine-products/" + sanguineProduct.getId(), HttpMethod.DELETE, authenticated(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("description"))
                .contains("1")
                .containsIgnoringCase("transaction");
        assertThat(sanguineProductRepository.existsById(sanguineProduct.getId())).isTrue();
    }

    private SanguineProduct newSanguineProduct(String name) {
        SanguineProduct sanguineProduct = new SanguineProduct();
        sanguineProduct.setName(name);
        return sanguineProduct;
    }
}
